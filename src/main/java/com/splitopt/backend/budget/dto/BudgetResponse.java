package com.splitopt.backend.budget.dto;

import com.splitopt.backend.budget.domain.Budget;

import java.math.BigDecimal;

/**
 * 예산 응답 (API 38·39).
 * <p>참고: 예산 현황(39)의 사용액·잔여·초과 여부와 예측(40)은 지출 집계가 필요해
 * 지출 파트 완성 후 필드를 확장한다. 현재는 설정된 예산 금액만 반환.
 */
public record BudgetResponse(
        Long groupId,
        BigDecimal amount
) {
    public static BudgetResponse from(Budget budget) {
        return new BudgetResponse(budget.getGroup().getId(), budget.getAmount());
    }
}
