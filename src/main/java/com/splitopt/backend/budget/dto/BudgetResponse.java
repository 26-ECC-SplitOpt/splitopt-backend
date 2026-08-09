package com.splitopt.backend.budget.dto;

import com.splitopt.backend.budget.domain.Budget;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 예산 응답 (API 38·39).
 *
 * <p>설정 금액과 함께 지출 집계에서 파생한 현황을 담는다. 파생값이므로 저장하지 않고 조회 시점에
 * 계산한다 — 지출이 등록·수정·삭제될 때마다 예산 행을 갱신하면 두 값이 어긋날 수 있다.
 *
 * @param spent     지출 합계
 * @param remaining 남은 예산 {@code amount − spent}. <b>초과 시 음수</b>다(0으로 깎지 않는다) —
 *                  얼마나 넘었는지가 화면에서 필요한 정보라서다.
 * @param exceeded  초과 여부. {@code spent > amount}
 * @param usageRate 예산 대비 사용 비율(%, 소수 첫째 자리까지). 100을 넘을 수 있다.
 *                  예산이 0원이면 비율을 정의할 수 없어 0으로 둔다({@code exceeded}로 판단).
 */
public record BudgetResponse(
        Long groupId,
        BigDecimal amount,
        BigDecimal spent,
        BigDecimal remaining,
        boolean exceeded,
        BigDecimal usageRate
) {
    public static BudgetResponse from(Budget budget, BigDecimal spent) {
        BigDecimal amount = budget.getAmount();
        BigDecimal spentAmount = spent != null ? spent : BigDecimal.ZERO;
        return new BudgetResponse(
                budget.getGroup().getId(),
                amount,
                spentAmount,
                amount.subtract(spentAmount),
                spentAmount.compareTo(amount) > 0,
                usageRate(spentAmount, amount));
    }

    /** 예산 0원은 나눗셈이 성립하지 않으므로 0%로 둔다 — 초과 여부는 {@code exceeded}가 말해 준다. */
    private static BigDecimal usageRate(BigDecimal spent, BigDecimal amount) {
        if (amount.signum() == 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return spent.divide(amount, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }
}
