package com.splitopt.backend.budget.controller;

import com.splitopt.backend.budget.dto.BudgetForecastResponse;
import com.splitopt.backend.budget.dto.BudgetRequest;
import com.splitopt.backend.budget.dto.BudgetResponse;
import com.splitopt.backend.budget.service.BudgetService;
import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.global.response.ApiResponse;
import com.splitopt.backend.global.security.UserPrincipal;
import com.splitopt.backend.group.service.GroupAccessGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 예산 API.
 * <p>구현: 38(설정/수정) · 39(현황 조회 — 금액·사용액·잔여·초과 여부) · 40(초과 예측).
 *
 * <p><b>인가</b>: 모임 참여자만 조회·설정할 수 있다. 인증만 통과하면 groupId를 아는 누구나
 * 남의 모임 예산을 바꿀 수 있던 문제를 막는다. 명세에 예산 변경을 owner로 제한하는 규칙은
 * 없어 참여자 누구나로 둔다.
 */
@RestController
@RequestMapping("/api/groups/{groupId}/budget")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;
    private final GroupAccessGuard groupAccessGuard;

    /** 예산 설정/수정 (38). */
    @PutMapping
    public ApiResponse<BudgetResponse> upsert(
            @PathVariable Long groupId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody BudgetRequest request) {
        requireMember(groupId, principal);
        return ApiResponse.success(budgetService.upsert(groupId, request.amount()), "예산이 설정되었습니다.");
    }

    /** 예산 현황 조회 (39) — 사용액·잔여·초과 여부는 지출 합계에서 파생한다. */
    @GetMapping
    public ApiResponse<BudgetResponse> getBudget(
            @PathVariable Long groupId,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireMember(groupId, principal);
        return ApiResponse.success(budgetService.getBudget(groupId));
    }

    /**
     * 예산 초과 예측 (40) — 모임 일정 기간의 경과 비율로 예상 총 지출을 낸다.
     *
     * <p>예측할 근거가 없으면(일정 없음·기간 시작 전) {@code basis=NONE}으로 내려가고 예측
     * 필드는 비어 있다. 화면은 그때 현황(39)으로 물러서면 된다.
     */
    @GetMapping("/forecast")
    public ApiResponse<BudgetForecastResponse> getForecast(
            @PathVariable Long groupId,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireMember(groupId, principal);
        return ApiResponse.success(budgetService.getForecast(groupId));
    }

    /** 로그인 사용자가 이 모임 참여자인지 확인 — principal이 비어 있으면 401, 비참여자면 403. */
    private void requireMember(Long groupId, UserPrincipal principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        groupAccessGuard.requireMember(groupId, principal.getUserId());
    }
}
