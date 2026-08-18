package com.splitopt.backend.settlement.service;

import com.splitopt.backend.expense.service.ExpenseService;
import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.group.domain.GroupParticipant;
import com.splitopt.backend.group.repository.GroupParticipantRepository;
import com.splitopt.backend.group.repository.GroupRepository;
import com.splitopt.backend.settlement.domain.ParticipantBalance;
import com.splitopt.backend.settlement.domain.Settlement;
import com.splitopt.backend.settlement.domain.SettlementStatus;
import com.splitopt.backend.settlement.dto.ParticipantBalanceResponse;
import com.splitopt.backend.settlement.optimizer.BalanceCalculator;
import com.splitopt.backend.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 개인별 잔액 산출 (API 23) 및 정산 최적화(API 24) 입력용 순잔액 계산.
 *
 * <p>지출 원장 집계는 지출 파트({@link ExpenseService#getPaidAmountsByParticipant}·
 * {@link ExpenseService#getOwedAmountsByParticipant})에서 가져오고, 이 서비스는 거기에
 * <b>이미 오간 돈</b>을 상계해 정산에 쓸 수 있는 형태로 만든다.
 *
 * <p><b>왜 상계가 필요한가.</b> 지출 원장 잔액은 "누가 얼마를 더 냈는가"만 말하고, 그 뒤에
 * 실제로 송금이 오갔는지는 모른다. 최적화 재실행 시 {@code SENT}·{@code COMPLETED} 정산은
 * 보존되므로(3주차 회의 확정), 그 금액을 빼지 않은 총잔액으로 재최적화하면 이미 보낸 돈에 대한
 * 송금 건이 다시 만들어져 <b>이중 청구</b>가 된다. 상계 규칙은 다음과 같다.
 * <pre>
 *   보낸 사람(from): 이미 냈으므로 빚이 줄어든다  → netBalance += amount
 *   받은 사람(to)  : 이미 받았으므로 받을 돈이 준다 → netBalance −= amount
 * </pre>
 * 상계액의 총합은 0이므로 순잔액 총합도 0이 유지된다(최적화기의 전제).
 */
@Service
@RequiredArgsConstructor
public class BalanceService {

    /** 이미 돈이 오간 것으로 보는 상태. PENDING은 아직 아무것도 오가지 않았으므로 제외. */
    private static final Set<SettlementStatus> SETTLED_STATUSES =
            Set.of(SettlementStatus.SENT, SettlementStatus.COMPLETED);

    private final ExpenseService expenseService;
    private final SettlementRepository settlementRepository;
    private final GroupParticipantRepository groupParticipantRepository;
    private final GroupRepository groupRepository;

    private final BalanceCalculator balanceCalculator = new BalanceCalculator();

    /**
     * 개인별 잔액 조회 (API 23).
     *
     * <p>대상은 <b>활성 참여자 전원</b>(활동이 없어 0원인 사람도 포함) 및 탈퇴했지만
     * 지출·정산 이력이 남은 참여자다. 참여자 id 오름차순.
     */
    @Transactional(readOnly = true)
    public List<ParticipantBalanceResponse> getBalances(Long groupId) {
        validateGroupExists(groupId);

        Map<Long, BigDecimal> paid = expenseService.getPaidAmountsByParticipant(groupId);
        Map<Long, BigDecimal> owed = expenseService.getOwedAmountsByParticipant(groupId);
        Map<Long, BigDecimal> settled = settledAdjustments(groupId);

        Map<Long, GroupParticipant> participants = participantsById(groupId);

        // 활성 참여자 + 원장/정산에 등장한 참여자(탈퇴자 포함)의 합집합
        TreeSet<Long> targetIds = new TreeSet<>();
        participants.values().stream().filter(GroupParticipant::isActive)
                .forEach(p -> targetIds.add(p.getId()));
        targetIds.addAll(paid.keySet());
        targetIds.addAll(owed.keySet());
        targetIds.addAll(settled.keySet());

        List<ParticipantBalanceResponse> result = new ArrayList<>(targetIds.size());
        for (Long id : targetIds) {
            GroupParticipant participant = participants.get(id);
            BigDecimal paidAmount = paid.getOrDefault(id, BigDecimal.ZERO);
            BigDecimal owedAmount = owed.getOrDefault(id, BigDecimal.ZERO);
            BigDecimal balance = paidAmount.subtract(owedAmount);
            result.add(new ParticipantBalanceResponse(
                    id,
                    participant != null ? participant.getEffectiveDisplayName() : null,
                    participant != null && participant.isActive(),
                    paidAmount,
                    owedAmount,
                    balance,
                    balance.add(settled.getOrDefault(id, BigDecimal.ZERO))));
        }
        return result;
    }

    /**
     * 정산 최적화(API 24) 입력용 순잔액. 지출 원장 잔액에서 이미 오간 돈(SENT·COMPLETED)을 상계한다.
     *
     * <p>잔액 0인 참여자도 포함해 반환한다(최적화기가 무시한다).
     */
    @Transactional(readOnly = true)
    public List<ParticipantBalance> getNetBalances(Long groupId) {
        validateGroupExists(groupId);

        List<ParticipantBalance> gross = balanceCalculator.calculate(
                expenseService.getPaidAmountsByParticipant(groupId),
                expenseService.getOwedAmountsByParticipant(groupId));
        Map<Long, BigDecimal> settled = settledAdjustments(groupId);

        // 원장에 없지만 정산 이력만 있는 참여자도 빠지지 않도록 합집합으로 순회 (총합 0 유지)
        Map<Long, BigDecimal> net = new LinkedHashMap<>();
        for (ParticipantBalance b : gross) {
            net.put(b.participantId(), b.amount());
        }
        settled.forEach((id, adjustment) -> net.merge(id, adjustment, BigDecimal::add));

        return new TreeSet<>(net.keySet()).stream()
                .map(id -> new ParticipantBalance(id, net.get(id)))
                .toList();
    }

    /**
     * 한 참여자에게 <b>확정되지 않은 몫</b>이 남아 있는지 (참여자 삭제·탈퇴 판정용).
     *
     * <p>여기서는 {@code COMPLETED}만 상계한다. 잔액 조회(23)·최적화(24)가 {@code SENT}까지
     * 상계하는 것과 다르다. 그쪽은 "이미 보낸 돈에 대해 송금 건을 또 만들지 않는다"가 목적이라
     * 보낸 시점부터 빼는 게 맞지만, 여기서는 <b>모임을 떠나도 되는가</b>를 판정한다.
     *
     * <p>{@code SENT}는 보낸 사람이 스스로 표시한 상태이고 받는 사람의 확인 전이며,
     * 취소해서 {@code PENDING}으로 되돌릴 수 있다. 그것까지 상계하면 돈을 보내지 않고 보냈다고
     * 표시만 해도 잔액이 0이 되어 나갈 수 있다. 나간 뒤에는 되돌릴 방법이 없다.
     *
     * @return 확정 기준 순잔액이 0이 아니면 true
     */
    @Transactional(readOnly = true)
    public boolean hasUnconfirmedBalance(Long groupId, Long participantId) {
        Map<Long, BigDecimal> paid = expenseService.getPaidAmountsByParticipant(groupId);
        Map<Long, BigDecimal> owed = expenseService.getOwedAmountsByParticipant(groupId);
        Map<Long, BigDecimal> confirmed = adjustmentsFor(groupId, Set.of(SettlementStatus.COMPLETED));

        BigDecimal net = paid.getOrDefault(participantId, BigDecimal.ZERO)
                .subtract(owed.getOrDefault(participantId, BigDecimal.ZERO))
                .add(confirmed.getOrDefault(participantId, BigDecimal.ZERO));
        return net.compareTo(BigDecimal.ZERO) != 0;
    }

    /**
     * 이미 오간 돈의 참여자별 상계액. 보낸 쪽은 +금액, 받은 쪽은 −금액이며 총합은 0이다.
     */
    private Map<Long, BigDecimal> settledAdjustments(Long groupId) {
        return adjustmentsFor(groupId, SETTLED_STATUSES);
    }

    /** 주어진 상태의 정산만 상계한다. */
    private Map<Long, BigDecimal> adjustmentsFor(Long groupId, Set<SettlementStatus> statuses) {
        Map<Long, BigDecimal> adjustments = new HashMap<>();
        for (Settlement s : settlementRepository.findByGroup_IdAndStatusIn(groupId, statuses)) {
            adjustments.merge(s.getFromParticipant().getId(), s.getAmount(), BigDecimal::add);
            adjustments.merge(s.getToParticipant().getId(), s.getAmount().negate(), BigDecimal::add);
        }
        return adjustments;
    }

    /** 탈퇴자 포함 참여자 맵 — 탈퇴 전 지출 이력이 남은 참여자의 이름도 표시해야 한다. */
    private Map<Long, GroupParticipant> participantsById(Long groupId) {
        Map<Long, GroupParticipant> map = new HashMap<>();
        for (GroupParticipant p : groupParticipantRepository.findAllByGroupId(groupId)) {
            map.put(p.getId(), p);
        }
        return map;
    }

    private void validateGroupExists(Long groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "모임을 찾을 수 없습니다.");
        }
    }
}
