package com.splitopt.backend.settlement.optimizer;

import com.splitopt.backend.settlement.domain.ParticipantBalance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BalanceCalculatorTest {

    private final BalanceCalculator calculator = new BalanceCalculator();

    private static BigDecimal amountOf(List<ParticipantBalance> balances, long id) {
        return balances.stream()
                .filter(b -> b.participantId() == id)
                .map(ParticipantBalance::amount)
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("회의록 예시: 결제−부담으로 A +120,000 / B 0 / C −40,000 / D −80,000 산출")
    void calculatesMeetingExample() {
        Map<Long, BigDecimal> paid = Map.of(
                1L, new BigDecimal("200000"),
                2L, new BigDecimal("80000"),
                3L, new BigDecimal("40000")
                // D(4)는 결제 없음
        );
        Map<Long, BigDecimal> owed = Map.of(
                1L, new BigDecimal("80000"),
                2L, new BigDecimal("80000"),
                3L, new BigDecimal("80000"),
                4L, new BigDecimal("80000")
        );

        List<ParticipantBalance> balances = calculator.calculate(paid, owed);

        assertEquals(4, balances.size());
        assertEquals(0, amountOf(balances, 1).compareTo(new BigDecimal("120000")));
        assertEquals(0, amountOf(balances, 2).compareTo(BigDecimal.ZERO));
        assertEquals(0, amountOf(balances, 3).compareTo(new BigDecimal("-40000")));
        assertEquals(0, amountOf(balances, 4).compareTo(new BigDecimal("-80000")));
    }

    @Test
    @DisplayName("결제만 있고 부담 없는 참여자, 부담만 있고 결제 없는 참여자 모두 포함")
    void includesParticipantsFromBothSides() {
        Map<Long, BigDecimal> paid = Map.of(1L, new BigDecimal("100"));
        Map<Long, BigDecimal> owed = Map.of(2L, new BigDecimal("100"));

        List<ParticipantBalance> balances = calculator.calculate(paid, owed);

        assertEquals(2, balances.size());
        assertEquals(0, amountOf(balances, 1).compareTo(new BigDecimal("100")));
        assertEquals(0, amountOf(balances, 2).compareTo(new BigDecimal("-100")));
    }

    @Test
    @DisplayName("잔액 총합은 항상 0 (결제 총액 = 부담 총액)")
    void balancesSumToZero() {
        Map<Long, BigDecimal> paid = Map.of(1L, new BigDecimal("300"), 2L, new BigDecimal("100"));
        Map<Long, BigDecimal> owed = Map.of(1L, new BigDecimal("100"), 2L, new BigDecimal("100"),
                3L, new BigDecimal("200"));

        List<ParticipantBalance> balances = calculator.calculate(paid, owed);
        BigDecimal sum = balances.stream().map(ParticipantBalance::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, sum.compareTo(BigDecimal.ZERO));
    }
}
