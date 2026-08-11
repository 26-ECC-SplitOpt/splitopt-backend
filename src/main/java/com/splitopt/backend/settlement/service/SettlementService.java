package com.splitopt.backend.settlement.service;

import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.group.domain.Group;
import com.splitopt.backend.group.domain.GroupParticipant;
import com.splitopt.backend.group.repository.GroupParticipantRepository;
import com.splitopt.backend.group.repository.GroupRepository;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 정산 계산/최적화·상태 관리 서비스 (API 24·25·26·27·28·29).
 *
 * <p>잔액 산출은 {@link BalanceService}가 담당한다. 이 서비스는 순잔액을 받아 최적화·저장하며,
 * 진입점 {@link #optimize(Long)}가 잔액 조회부터 저장까지 하나의 트랜잭션으로 묶는다.
 */
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final GroupParticipantRepository groupParticipantRepository;
    private final GroupRepository groupRepository;
    private final BalanceService balanceService;
    private final SettlementOptimizer optimizer = new SettlementOptimizer();

    /**
     * 정산 최적화 실행 (API 24). 지출 원장에서 순잔액을 산출해 최적화·저장한다.
     *
     * <p>잔액 조회와 저장을 같은 트랜잭션에서 수행한다 — 그 사이 지출이 추가되면 총합이 0이
     * 아닌 잔액으로 최적화하게 되어 실패하거나 어긋난 정산이 만들어진다.
     *
     * <p>잔액을 <b>읽기 전에</b> 모임 행을 잠근다. 잔액 조회 → PENDING 삭제 → 저장은
     * read-modify-write라, 잠그지 않으면 동시 요청이 서로의 미커밋 PENDING을 못 보고 각자
     * 새로 저장해 같은 청구가 두 벌 남는다.
     */
    @Transactional
    public List<SettlementResponse> optimize(Long groupId) {
        lockGroupForUpdate(groupId);
        return optimizeAndSave(groupId, balanceService.getNetBalances(groupId));
    }

    /**
     * 정산 최적화 실행 및 저장 (API 24).
     * 재실행 정책: 기존 {@code PENDING}은 삭제 후 재생성하고, {@code SENT}·{@code COMPLETED}는 보존한다
     * (송금 중·완료 건은 실제로 오간 돈이라 초기화하지 않는다).
     *
     * @param groupId  모임 id
     * @param balances 참여자별 잔액 (총합 0). 재실행 시에는 반드시 이미 정산된(SENT·COMPLETED) 금액을
     *                 차감한 <b>순잔액</b>이어야 한다 — 그러지 않으면 이미 보낸 돈을 다시 만들어 이중청구가 된다.
     *                 ({@link BalanceService#getNetBalances}가 이 형태로 반환한다.)
     * @return 새로 생성된 정산 목록
     */
    @Transactional
    public List<SettlementResponse> optimizeAndSave(Long groupId, List<ParticipantBalance> balances) {
        // 이 메서드도 삭제 후 저장이라 단독 호출 경로에서 같은 경합이 생긴다.
        // optimize()가 이미 잠갔다면 같은 트랜잭션이므로 여기서는 아무 일도 일어나지 않는다.
        Group group = lockGroupForUpdate(groupId);
        Map<Long, GroupParticipant> participants = loadGroupParticipants(groupId, balances);

        settlementRepository.deleteByGroup_IdAndStatus(groupId, SettlementStatus.PENDING);

        List<SettlementTransfer> transfers = optimizer.optimize(balances);

        List<Settlement> settlements = transfers.stream()
                .map(t -> Settlement.builder()
                        .group(group)
                        .fromParticipant(participants.get(t.fromParticipantId()))
                        .toParticipant(participants.get(t.toParticipantId()))
                        .amount(t.amount())
                        .build())
                .toList();

        return settlementRepository.saveAll(settlements).stream()
                .map(SettlementResponse::from)
                .toList();
    }

    /**
     * 최적화 대상 모임 행을 배타 잠금으로 읽어 같은 모임의 최적화를 직렬화한다.
     *
     * <p>정산 테이블에는 (모임, 보내는 사람, 받는 사람) 유니크 제약이 없어 중복 저장을 DB가
     * 막아주지 못한다. 모임 행을 잠그는 쪽이 정산 스키마를 바꾸는 것보다 가볍고, 재실행 자체가
     * 모임 단위 작업이라 잠금 범위도 자연스럽다.
     */
    private Group lockGroupForUpdate(Long groupId) {
        return groupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "모임을 찾을 수 없습니다."));
    }

    /**
     * 잔액에 등장한 참여자가 모두 이 모임 소속인지 검증하고 엔티티 맵으로 돌려준다.
     *
     * <p>참조만 걸어 저장하면(예: {@code EntityManager#getReference}) 다른 모임의 참여자 id가 섞여도 그대로
     * 저장되어 모임과 참여자가 어긋난 정산이 생긴다. 저장 전에 소속을 확인한다.
     * 탈퇴자도 정산 대상이므로 활성 여부는 보지 않는다.
     */
    private Map<Long, GroupParticipant> loadGroupParticipants(Long groupId, List<ParticipantBalance> balances) {
        Map<Long, GroupParticipant> participants = groupParticipantRepository.findAllByGroupId(groupId).stream()
                .collect(Collectors.toMap(GroupParticipant::getId, Function.identity()));

        for (ParticipantBalance balance : balances) {
            if (!participants.containsKey(balance.participantId())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "모임에 속하지 않은 참여자입니다: " + balance.participantId());
            }
        }
        return participants;
    }

    /** 정산 결과 전체 조회 (API 25). */
    @Transactional(readOnly = true)
    public List<SettlementResponse> getSettlements(Long groupId) {
        return settlementRepository.findByGroup_Id(groupId).stream()
                .map(SettlementResponse::from)
                .toList();
    }

    /** 상태별 정산 조회 (API 25 필터 · 28 미정산 — 컨트롤러가 {@code ?status=}를 파싱해 넘긴다). */
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
