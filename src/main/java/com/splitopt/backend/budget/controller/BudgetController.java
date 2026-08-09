package com.splitopt.backend.budget.controller;

import com.splitopt.backend.budget.dto.BudgetRequest;
import com.splitopt.backend.budget.dto.BudgetResponse;
import com.splitopt.backend.budget.service.BudgetService;
import com.splitopt.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 예산 API.
 * <p>구현: 38(설정/수정) · 39(현황 조회 — 금액·사용액·잔여·초과 여부).
 * <p>미구현: 40 초과 예측.
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
}
