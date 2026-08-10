package com.splitopt.backend.budget.controller;

import com.splitopt.backend.budget.dto.BudgetForecastResponse;
import com.splitopt.backend.budget.dto.BudgetRequest;
import com.splitopt.backend.budget.dto.BudgetResponse;
import com.splitopt.backend.budget.service.BudgetService;
import com.splitopt.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 예산 API.
 * <p>구현: 38(설정/수정) · 39(현황 조회 — 금액·사용액·잔여·초과 여부) · 40(초과 예측).
 */
@RestController
@RequestMapping("/api/groups/{groupId}/budget")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    /** 예산 설정/수정 (38). */
    @PutMapping
    public ApiResponse<BudgetResponse> upsert(
            @PathVariable Long groupId,
            @Valid @RequestBody BudgetRequest request) {
        return ApiResponse.success(budgetService.upsert(groupId, request.amount()), "예산이 설정되었습니다.");
    }

    /** 예산 현황 조회 (39) — 사용액·잔여·초과 여부는 지출 합계에서 파생한다. */
    @GetMapping
    public ApiResponse<BudgetResponse> getBudget(@PathVariable Long groupId) {
        return ApiResponse.success(budgetService.getBudget(groupId));
    }

    /**
     * 예산 초과 예측 (40) — 모임 일정 기간의 경과 비율로 예상 총 지출을 낸다.
     *
     * <p>예측할 근거가 없으면(일정 없음·기간 시작 전) {@code basis=NONE}으로 내려가고 예측
     * 필드는 비어 있다. 화면은 그때 현황(39)으로 물러서면 된다.
     */
    @GetMapping("/forecast")
    public ApiResponse<BudgetForecastResponse> getForecast(@PathVariable Long groupId) {
        return ApiResponse.success(budgetService.getForecast(groupId));
    }
}
