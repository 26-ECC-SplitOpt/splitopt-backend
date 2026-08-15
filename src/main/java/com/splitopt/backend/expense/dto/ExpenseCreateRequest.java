package com.splitopt.backend.expense.dto;

import com.splitopt.backend.expense.domain.ExpenseCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 지출 등록 요청 (API 17). payer는 없음 — 결제자는 서버가 로그인 사용자로 고정.
 *
 * <p>날짜는 시각 없이 {@code expenseDate}(yyyy-MM-dd)로 받는다. 화면의 입력이 날짜뿐이라
 * 시각을 요구하면 프론트가 임의의 시각을 붙여 보내야 하고, 날짜만 보내면 파싱에 실패한다.
 * 저장은 기존 {@code spent_at} 컬럼에 그날 00:00으로 한다.
 */
public record ExpenseCreateRequest(

        @NotBlank(message = "지출 항목명을 입력해주세요.")
        String title,

        @NotNull(message = "금액을 입력해주세요.")
        @Positive(message = "금액은 0보다 커야 합니다.")
        BigDecimal amount,

        @NotNull(message = "카테고리를 선택해주세요.")
        ExpenseCategory category,

        String memo,

        @NotNull(message = "지출 날짜를 입력해주세요.")
        LocalDate expenseDate,

        Long scheduleId, // 선택 항목, 없으면 null

        @NotNull(message = "분담 방식을 선택해주세요.")
        SplitMethod splitMethod, // EQUAL(균등) / DIRECT(직접입력) — 저장은 안 되고 계산 방식 결정용

        @NotNull(message = "부담자 목록이 필요합니다.")
        List<@Valid ShareInput> shares
) {
    public enum SplitMethod {
        EQUAL, DIRECT
    }

    /** 부담자 한 명의 입력값. EQUAL이면 amount는 무시하고 participantId 목록만 사용, DIRECT면 amount 필수. */
    public record ShareInput(
            @NotNull(message = "참여자 ID가 필요합니다.")
            Long participantId,

            BigDecimal amount // DIRECT일 때만 필수 (검증은 Service에서)
    ) {}
}