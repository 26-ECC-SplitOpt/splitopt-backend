package com.splitopt.backend.settlement.optimizer;

import com.splitopt.backend.settlement.domain.ParticipantBalance;
import com.splitopt.backend.settlement.domain.SettlementTransfer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * 정산 최적화 알고리즘 (API 24) — 서비스의 핵심 기능.
 *
 * <p>참여자별 잔액을 채권자(+)와 채무자(−)로 나눈 뒤, <b>greedy netting</b> 방식으로
 * 송금 건수를 줄인다: 매 단계에서 잔액 절댓값이 가장 큰 채권자와 채무자를 골라
 * 둘 중 작은 금액만큼 상쇄하고, 남은 금액은 다시 큐에 넣는다. 결과 송금 건수는 최대 (n−1)건.
 *
 * <p>참고: 최소 송금 횟수 문제는 일반적으로 NP-hard이므로 이 greedy는 최적 근사다.
 * (정확한 최소해가 필요하면 부분집합 상쇄 등 추가 최적화를 얹을 수 있음 — 추후 논의)
 *
 * <p>전제: 입력 잔액의 총합은 0이어야 한다(NFR-04: 결제 총액 = 부담 총액).
 */
public class SettlementOptimizer {

    /**
     * @param balances 참여자별 잔액 (총합 0). 잔액 0인 참여자는 무시된다.
     * @return 송금 목록 (채무자 → 채권자). 정산할 것이 없으면 빈 목록.
     * @throws IllegalArgumentException 잔액 총합이 0이 아니면
     */
    public List<SettlementTransfer> optimize(List<ParticipantBalance> balances) {
        if (balances == null) {
            throw new IllegalArgumentException("balances must not be null");
        }

        BigDecimal total = balances.stream()
                .map(ParticipantBalance::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() != 0) {
            throw new IllegalArgumentException(
                    "balances must sum to zero but was " + total.toPlainString());
        }

        // 채권자: 잔액 큰 순 / 채무자: 부담액(양수화) 큰 순
        PriorityQueue<Node> creditors = new PriorityQueue<>(Comparator.comparing((Node n) -> n.amount).reversed());
        PriorityQueue<Node> debtors = new PriorityQueue<>(Comparator.comparing((Node n) -> n.amount).reversed());

        for (ParticipantBalance b : balances) {
            int sign = b.amount().signum();
            if (sign > 0) {
                creditors.add(new Node(b.participantId(), b.amount()));
            } else if (sign < 0) {
                debtors.add(new Node(b.participantId(), b.amount().negate()));
            }
        }

        List<SettlementTransfer> transfers = new ArrayList<>();
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            Node creditor = creditors.poll();
            Node debtor = debtors.poll();

            BigDecimal amount = creditor.amount.min(debtor.amount);
            transfers.add(new SettlementTransfer(debtor.participantId, creditor.participantId, amount));

            BigDecimal creditorRemainder = creditor.amount.subtract(amount);
            BigDecimal debtorRemainder = debtor.amount.subtract(amount);
            if (creditorRemainder.signum() > 0) {
                creditors.add(new Node(creditor.participantId, creditorRemainder));
            }
            if (debtorRemainder.signum() > 0) {
                debtors.add(new Node(debtor.participantId, debtorRemainder));
            }
        }
        return transfers;
    }

    /** 큐 내부용 가변 노드 (participantId, 남은 금액은 항상 양수). */
    private record Node(Long participantId, BigDecimal amount) {
    }
}
