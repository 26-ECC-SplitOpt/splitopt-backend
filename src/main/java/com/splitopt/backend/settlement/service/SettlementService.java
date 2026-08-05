package com.splitopt.backend.settlement.service;

import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.group.domain.Group;
import com.splitopt.backend.group.domain.GroupParticipant;
import com.splitopt.backend.settlement.domain.Settlement;
import com.splitopt.backend.settlement.domain.SettlementStatus;
import com.splitopt.backend.settlement.domain.ParticipantBalance;
import com.splitopt.backend.settlement.domain.SettlementTransfer;
import com.splitopt.backend.settlement.dto.MySettlementsResponse;
import com.splitopt.backend.settlement.dto.SettlementResponse;
import com.splitopt.backend.settlement.dto.SettlementStatusChangeRequest;
import com.splitopt.backend.settlement.dto.SettlementSummaryResponse;
import com.splitopt.backend.settlement.optimizer.SettlementOptimizer;
import com.splitopt.backend.settlement.repository.SettlementRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 정산 계산/최적화·상태 관리 서비스 (API 24·25·26·27·28·29).
 *
 * <p>주의: 개인별 잔액(API 23)은 지출·부담({@code expenses}/{@code expense_shares}, 이채빈 파트)
 * 집계가 필요하다. 이 서비스는 잔액을 <b>입력값으로 받아</b> 최적화·저장하며, 잔액 산출 자체는
 * 지출 파트 완성 후 연결한다. (그래서 {@link #optimizeAndSave}가 balances를 파라미터로 받음)
 */
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final EntityManager em;
    private final SettlementOptimizer optimizer = new SettlementOptimizer();

    /**
     * 정산 최적화 실행 및 저장 (API 24).
     * 재실행 정책: 기존 {@code PENDING}은 삭제 후 재생성하고, {@code SENT}·{@code COMPLETED}는 보존한다
     * (송금 중·완료 건은 실제로 오간 돈이라 초기화하지 않는다).
     *
     * @param groupId  모임 id
     * @param balances 참여자별 잔액 (총합 0). 재실행 시에는 반드시 이미 정산된(SENT·COMPLETED) 금액을
     *                 차감한 <b>순잔액</b>이어야 한다 — 그러지 않으면 이미 보낸 돈을 다시 만들어 이중청구가 된다.
     * @return 새로 생성된 정산 목록
     */
    @Transactional
    public List<SettlementResponse> optimizeAndSave(Long groupId, List<ParticipantBalance> balances) {
        settlementRepository.deleteByGroup_IdAndStatus(groupId, SettlementStatus.PENDING);

        List<SettlementTransfer> transfers = optimizer.optimize(balances);
        Group groupRef = em.getReference(Group.class, groupId);

        List<Settlement> settlements = transfers.stream()
                .map(t -> Settlement.builder()
                        .group(groupRef)
                        .fromParticipant(em.getReference(GroupParticipant.class, t.fromParticipantId()))
                        .toParticipant(em.getReference(GroupParticipant.class, t.toParticipantId()))
                        .amount(t.amount())
                        .build())
                .toList();

        return settlementRepository.saveAll(settlements).stream()
                .map(SettlementResponse::from)
                .toList();
    }

    /** 정산 결과 전체 조회 (API 25). */
    @Transactional(readOnly = true)
    public List<SettlementResponse> getSettlements(Long groupId) {
        return settlementRepository.findByGroup_Id(groupId).stream()
                .map(SettlementResponse::from)
                .toList();
    }

    /** 미정산 내역 조회 (API 28). */
    @Transactional(readOnly = true)
    public List<SettlementResponse> getPending(Long groupId) {
        return getByStatus(groupId, SettlementStatus.PENDING);
    }

    /** 상태별 정산 조회 (API 25/28 공통). */
    @Transactional(readOnly = true)
    public List<SettlementResponse> getByStatus(Long groupId, SettlementStatus status) {
        return settlementRepository.findByGroup_IdAndStatus(groupId, status).stream()
                .map(SettlementResponse::from)
                .toList();
    }

    /**
     * 내 정산 내역 조회 (API 26, 개정안 C-3). 보낼/받을/완료로 분류해 반환.
     * userId는 인증에서 주입 예정(현재는 컨트롤러에서 헤더로 전달).
     */
    @Transactional(readOnly = true)
    public MySettlementsResponse getMySettlements(Long groupId, Long userId) {
        return MySettlementsResponse.from(settlementRepository.findMine(groupId, userId), userId);
    }

    /**
     * 정산 상태 변경 (API 27, 개정안 C-4). 경로의 모임에 속한 정산만 대상.
     *
     * <p>권한: SEND/CANCEL은 보내는 사람(from)만, CONFIRM은 받는 사람(to)만 — 그 외 403.
     * 상태 전이 위반(예: 이미 SENT인데 SEND)은 409.
     *
     * @param requesterParticipantId 요청자의 참여자 id (인증 연동 시 로그인 참여자로 주입)
     */
    @Transactional
    public SettlementResponse changeStatus(Long groupId, Long settlementId,
                                           SettlementStatusChangeRequest.Action action,
                                           Long requesterParticipantId) {
        // 잠금 조회: 동시 전이 요청의 lost update 방지 (두 번째 요청은 갱신된 상태를 읽어 상태 가드에서 걸림)
        Settlement settlement = settlementRepository.findByIdAndGroup_IdForUpdate(settlementId, groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "정산 내역을 찾을 수 없습니다."));

        Long from = settlement.getFromParticipant().getId();
        Long to = settlement.getToParticipant().getId();

        switch (action) {
            case SEND -> {
                requireRequester(requesterParticipantId, from, "송금 완료는 보내는 사람만 처리할 수 있습니다.");
                transit(settlement::markSent);
            }
            case CONFIRM -> {
                requireRequester(requesterParticipantId, to, "송금 확인은 받는 사람만 처리할 수 있습니다.");
                transit(settlement::confirm);
            }
            case CANCEL -> {
                requireRequester(requesterParticipantId, from, "송금 취소는 보내는 사람만 처리할 수 있습니다.");
                transit(settlement::cancelSend);
            }
        }
        return SettlementResponse.from(settlement);
    }

    /** 요청자가 허용된 참여자인지 검증 — 아니면 403. */
    private void requireRequester(Long requesterParticipantId, Long allowedParticipantId, String message) {
        if (requesterParticipantId == null || !requesterParticipantId.equals(allowedParticipantId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, message);
        }
    }

    /** 도메인 상태 전이 실행 — 상태 위반(IllegalState)은 409로 변환. */
    private void transit(Runnable transition) {
        try {
            transition.run();
        } catch (IllegalStateException e) {
            throw new BusinessException(ErrorCode.INVALID_STATE, e.getMessage());
        }
    }

    /** 전체 정산 완료 여부 조회 (API 29). */
    @Transactional(readOnly = true)
    public SettlementSummaryResponse getSummary(Long groupId) {
        long total = settlementRepository.countByGroup_Id(groupId);
        long completed = settlementRepository.countByGroup_IdAndStatus(groupId, SettlementStatus.COMPLETED);
        return SettlementSummaryResponse.of(total, completed);
    }
}
