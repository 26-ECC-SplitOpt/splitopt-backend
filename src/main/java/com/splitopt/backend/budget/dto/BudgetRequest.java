package com.splitopt.backend.budget.dto;

import com.splitopt.backend.budget.domain.BudgetType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * 예산 설정/수정 요청 (API 38).
 *
 * <p>{@code budgetType}은 금액이 무엇을 뜻하는지 정한다. 생략하면 모임 전체 예산({@code TOTAL})으로
 * 본다 — 이 필드가 생기기 전에 저장된 예산과 같은 해석이다.
 */
public record BudgetRequest(
        BudgetType budgetType,

        @NotNull(message = "예산 금액은 필수입니다.")
        @PositiveOrZero(message = "예산 금액은 0 이상이어야 합니다.")
        @Digits(integer = 10, fraction = 2, message = "예산 금액은 정수 10자리, 소수 2자리까지 가능합니다.")
        BigDecimal amount
) {
}
