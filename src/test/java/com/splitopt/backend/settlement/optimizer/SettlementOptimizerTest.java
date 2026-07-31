package com.splitopt.backend.settlement.optimizer;

import com.splitopt.backend.settlement.domain.ParticipantBalance;
import com.splitopt.backend.settlement.domain.SettlementTransfer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementOptimizerTest {

    private final SettlementOptimizer optimizer = new SettlementOptimizer();

    private static ParticipantBalance balance(long id, String amount) {
        return new ParticipantBalance(id, new BigDecimal(amount));
    }

    /** 각 참여자의 순증감 합계 (받은 것 − 보낸 것)을 검증용으로 집계. */
    private static Map<Long, BigDecimal> netByParticipant(List<SettlementTransfer> transfers) {
        Map<Long, BigDecimal> net = new java.util.HashMap<>();
        for (SettlementTransfer t : transfers) {
            net.merge(t.toParticipantId(), t.amount(), BigDecimal::add);       // 채권자: +
            net.merge(t.fromParticipantId(), t.amount().negate(), BigDecimal::add); // 채무자: −
        }
        return net;
    }

    @Test
    @DisplayName("회의록 예시: A +120,000 / C −40,000 / D −80,000 → 2건으로 정산")
    void optimizesMeetingExample() {
        List<ParticipantBalance> balances = List.of(
                balance(1, "120000"),   // A 받을 돈
                balance(2, "0"),        // B 정산 없음
                balance(3, "-40000"),   // C 보낼 돈
                balance(4, "-80000")    // D 보낼 돈
        );

        List<SettlementTransfer> transfers = optimizer.optimize(balances);

        // 채권자가 A 한 명뿐이라 C→A, D→A 2건이면 충분
        assertEquals(2, transfers.size());
        // 모든 송금은 A(1)에게로
        assertTrue(transfers.stream().allMatch(t -> t.toParticipantId() == 1L));
        // A가 받는 총액 = 120,000
        BigDecimal received = transfers.stream()
                .map(SettlementTransfer::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, received.compareTo(new BigDecimal("120000")));
    }

    @Test
    @DisplayName("정산 후 모든 참여자의 순증감이 원래 잔액과 정확히 일치한다")
    void transfersReconcileEveryBalance() {
        List<ParticipantBalance> balances = List.of(
                balance(1, "50000"),
                balance(2, "30000"),
                balance(3, "-20000"),
                balance(4, "-60000")
        );

        List<SettlementTransfer> transfers = optimizer.optimize(balances);
        Map<Long, BigDecimal> net = netByParticipant(transfers);

        for (ParticipantBalance b : balances) {
            BigDecimal settled = net.getOrDefault(b.participantId(), BigDecimal.ZERO);
            assertEquals(0, settled.compareTo(b.amount()),
                    "participant " + b.participantId() + " expected " + b.amount() + " but got " + settled);
        }
    }

    @Test
    @DisplayName("송금 건수는 (참여자 수 − 1) 이하다")
    void transferCountIsAtMostNMinusOne() {
        List<ParticipantBalance> balances = List.of(
                balance(1, "100"), balance(2, "100"), balance(3, "100"),
                balance(4, "-150"), balance(5, "-150")
        );
        List<SettlementTransfer> transfers = optimizer.optimize(balances);
        assertTrue(transfers.size() <= balances.size() - 1);
    }

    @Test
    @DisplayName("모두 0이면 송금이 없다")
    void noTransfersWhenAllZero() {
        List<ParticipantBalance> balances = List.of(balance(1, "0"), balance(2, "0"));
        assertTrue(optimizer.optimize(balances).isEmpty());
    }

    @Test
    @DisplayName("소수점(원 미만) 잔액도 합이 0이면 정확히 정산된다")
    void handlesDecimalCents() {
        List<ParticipantBalance> balances = List.of(
                balance(1, "33.34"),
                balance(2, "-16.67"),
                balance(3, "-16.67")
        );
        List<SettlementTransfer> transfers = optimizer.optimize(balances);
        Map<Long, BigDecimal> net = netByParticipant(transfers);
        for (ParticipantBalance b : balances) {
            assertEquals(0, net.getOrDefault(b.participantId(), BigDecimal.ZERO).compareTo(b.amount()));
        }
    }

    @Test
    @DisplayName("잔액 총합이 0이 아니면 예외를 던진다")
    void rejectsNonZeroSum() {
        List<ParticipantBalance> balances = List.of(balance(1, "100"), balance(2, "-50"));
        assertThrows(IllegalArgumentException.class, () -> optimizer.optimize(balances));
    }
}
