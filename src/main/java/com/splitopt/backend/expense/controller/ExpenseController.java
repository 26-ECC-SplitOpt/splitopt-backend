package com.splitopt.backend.expense.controller;

import com.splitopt.backend.expense.dto.ExpenseCreateRequest;
import com.splitopt.backend.expense.dto.ExpenseResponse;
import com.splitopt.backend.expense.service.ExpenseService;
import com.splitopt.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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
}