package com.splitopt.backend.settlement.service;

import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.group.domain.Group;
import com.splitopt.backend.group.domain.GroupParticipant;
import com.splitopt.backend.settlement.domain.Settlement;
import com.splitopt.backend.settlement.domain.SettlementStatus;
import com.splitopt.backend.settlement.domain.ParticipantBalance;
import com.splitopt.backend.settlement.domain.SettlementTransfer;
import com.splitopt.backend.settlement.dto.SettlementResponse;
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
     * 재실행 정책: 기존 {@code PENDING}은 삭제 후 재생성하고, {@code COMPLETED}는 보존한다.
     *
     * @param groupId  모임 id
     * @param balances 참여자별 잔액 (총합 0)
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

    /** 내 정산 내역 조회 (API 26). userId는 인증에서 주입 예정. */
    @Transactional(readOnly = true)
    public List<SettlementResponse> getMySettlements(Long groupId, Long userId) {
        return settlementRepository.findMine(groupId, userId).stream()
                .map(SettlementResponse::from)
                .toList();
    }

    /** 정산 완료 처리 (API 27). 경로의 모임에 속한 정산만 완료할 수 있다. */
    @Transactional
    public SettlementResponse complete(Long groupId, Long settlementId) {
        Settlement settlement = settlementRepository.findByIdAndGroup_Id(settlementId, groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "정산 내역을 찾을 수 없습니다."));
        settlement.complete();
        return SettlementResponse.from(settlement);
    }

    /** 전체 정산 완료 여부 조회 (API 29). */
    @Transactional(readOnly = true)
    public SettlementSummaryResponse getSummary(Long groupId) {
        long total = settlementRepository.countByGroup_Id(groupId);
        long completed = settlementRepository.countByGroup_IdAndStatus(groupId, SettlementStatus.COMPLETED);
        return SettlementSummaryResponse.of(total, completed);
    }
}
