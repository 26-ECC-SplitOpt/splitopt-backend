package com.splitopt.backend.budget.dto;

import com.splitopt.backend.budget.domain.Budget;
import com.splitopt.backend.budget.domain.BudgetType;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 예산 응답 (API 38·39).
 *
 * <p>사용액·잔여·초과 여부는 지출 합계에서 파생한다. 저장하지 않고 조회 시점에 계산하므로 지출이
 * 바뀌어도 예산 행을 손댈 필요가 없다.
 *
 * <p>판정 기준은 언제나 {@code totalBudget}(모임 전체)이다. 1인당으로 잡은 예산도 인원수를 곱해
 * 총예산을 구한 뒤 비교한다. 인원이 바뀌면 총예산도 함께 바뀐다.
 *
 * @param budgetType      예산 단위 (TOTAL / PER_PERSON)
 * @param budgetPerPerson 1인당 예산. {@code TOTAL}로 잡았으면 개념이 없어 null이다.
 * @param totalBudget     모임 전체 기준 총예산
 * @param remaining       남은 예산 {@code totalBudget − spent}. <b>초과 시 음수</b>다(0으로 깎지 않는다) —
 *                        얼마나 넘었는지가 화면에서 필요한 정보라서다.
 * @param exceeded        초과 여부. {@code spent > totalBudget}
 * @param usageRate       총예산 대비 사용 비율(%, 소수 첫째 자리까지). 100을 넘을 수 있다.
 *                        총예산이 0원이면 비율을 정의할 수 없어 0으로 둔다({@code exceeded}로 판단).
 */
public record BudgetResponse(
        Long groupId,
        BudgetType budgetType,
        BigDecimal budgetPerPerson,
        BigDecimal totalBudget,
        BigDecimal spent,
        BigDecimal remaining,
        boolean exceeded,
        BigDecimal usageRate
) {
    public static BudgetResponse from(Budget budget, BigDecimal spent, long participantCount) {
        BigDecimal total = budget.totalBudget(participantCount);
        BigDecimal spentAmount = spent != null ? spent : BigDecimal.ZERO;
        return new BudgetResponse(
                budget.getGroup().getId(),
                budget.getBudgetType(),
                budget.budgetPerPerson(),
                total,
                spentAmount,
                total.subtract(spentAmount),
                spentAmount.compareTo(total) > 0,
                usageRate(spentAmount, total));
    }

    /** 총예산 0원은 나눗셈이 성립하지 않으므로 0%로 둔다 — 초과 여부는 {@code exceeded}가 말해 준다. */
    private static BigDecimal usageRate(BigDecimal spent, BigDecimal total) {
        if (total.signum() == 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return spent.divide(total, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }
}
