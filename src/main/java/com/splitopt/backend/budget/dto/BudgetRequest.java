package com.splitopt.backend.budget.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/** 예산 설정/수정 요청 (API 38). */
public record BudgetRequest(
        @NotNull(message = "예산 금액은 필수입니다.")
        @PositiveOrZero(message = "예산 금액은 0 이상이어야 합니다.")
        BigDecimal amount
) {
}
