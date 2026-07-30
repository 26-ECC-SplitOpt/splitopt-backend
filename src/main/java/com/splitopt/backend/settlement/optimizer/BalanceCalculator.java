package com.splitopt.backend.settlement.optimizer;

import com.splitopt.backend.settlement.domain.ParticipantBalance;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 개인별 잔액 계산 (API 23).
 *
 * <p>참여자별 <b>실제 결제 금액</b>과 <b>부담해야 할 금액</b>을 받아
 * {@code 잔액 = 결제 − 부담}을 산출한다. 두 입력은 각각 지출({@code expenses.payer_id})과
 * 부담 분배({@code expense_shares})에서 집계되지만, 이 클래스는 해당 엔티티에 의존하지 않고
 * 참여자 ID → 금액 맵만 입력으로 받는다. (엔티티 미완성 상태에서 단독 개발·테스트 가능)
 */
public class BalanceCalculator {

    /**
     * @param paidByParticipant 참여자 ID → 실제 결제 금액 합계
     * @param owedByParticipant 참여자 ID → 부담해야 할 금액 합계
     * @return 참여자 ID 오름차순 정렬된 잔액 목록 (잔액 0인 참여자도 포함)
     */
    public List<ParticipantBalance> calculate(Map<Long, BigDecimal> paidByParticipant,
                                              Map<Long, BigDecimal> owedByParticipant) {
        if (paidByParticipant == null || owedByParticipant == null) {
            throw new IllegalArgumentException("input maps must not be null");
        }

        TreeSet<Long> participantIds = new TreeSet<>();
        participantIds.addAll(paidByParticipant.keySet());
        participantIds.addAll(owedByParticipant.keySet());

        List<ParticipantBalance> balances = new ArrayList<>(participantIds.size());
        for (Long id : participantIds) {
            BigDecimal paid = paidByParticipant.getOrDefault(id, BigDecimal.ZERO);
            BigDecimal owed = owedByParticipant.getOrDefault(id, BigDecimal.ZERO);
            balances.add(new ParticipantBalance(id, paid.subtract(owed)));
        }
        return balances;
    }
}
