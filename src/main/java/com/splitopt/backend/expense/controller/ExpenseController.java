package com.splitopt.backend.expense.controller;

import com.splitopt.backend.expense.dto.ExpenseCreateRequest;
import com.splitopt.backend.expense.dto.ExpenseResponse;
import com.splitopt.backend.expense.service.ExpenseService;
import com.splitopt.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 지출 관리 API (17~21).
 * TODO: 인증 완성되면 payerId 요청 파라미터를 없애고 로그인 사용자로 대체.
 */
@RestController
@RequestMapping("/api/groups/{groupId}/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    /** 지출 등록(17). 인증 전이라 임시로 payerId를 쿼리 파라미터로 받는다. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ExpenseResponse> createExpense(
            @PathVariable Long groupId,
            @RequestParam Long payerId, // TODO: 인증 완성되면 제거
            @Valid @RequestBody ExpenseCreateRequest request) {
        ExpenseResponse response = expenseService.createExpense(groupId, payerId, request);
        return ApiResponse.success(response, "지출이 등록되었습니다.");
    }

    /** 지출 목록 조회(18). */
    @GetMapping
    public ApiResponse<List<ExpenseResponse>> getExpenses(@PathVariable Long groupId) {
        return ApiResponse.success(expenseService.getExpenses(groupId));
    }

    /** 지출 상세 조회(19). */
    @GetMapping("/{expenseId}")
    public ApiResponse<ExpenseResponse> getExpense(@PathVariable Long groupId, @PathVariable Long expenseId) {
        return ApiResponse.success(expenseService.getExpense(groupId, expenseId));
    }

    /** 지출 수정(20). requesterId는 인증 완성 전 임시 파라미터. */
    @PutMapping("/{expenseId}")
    public ApiResponse<ExpenseResponse> updateExpense(
            @PathVariable Long groupId,
            @PathVariable Long expenseId,
            @RequestParam Long requesterId, // TODO: 인증 완성되면 제거
            @Valid @RequestBody ExpenseCreateRequest request) {
        return ApiResponse.success(expenseService.updateExpense(groupId, expenseId, requesterId, request), "지출이 수정되었습니다.");
    }

    /** 지출 삭제(21). requesterId는 인증 완성 전 임시 파라미터. */
    @DeleteMapping("/{expenseId}")
    public ApiResponse<Void> deleteExpense(
            @PathVariable Long groupId,
            @PathVariable Long expenseId,
            @RequestParam Long requesterId) { // TODO: 인증 완성되면 제거
        expenseService.deleteExpense(groupId, expenseId, requesterId);
        return ApiResponse.success();
    }
}