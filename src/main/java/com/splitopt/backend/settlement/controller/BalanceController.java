package com.splitopt.backend.settlement.controller;

import com.splitopt.backend.global.response.ApiResponse;
import com.splitopt.backend.settlement.dto.ParticipantBalanceResponse;
import com.splitopt.backend.settlement.service.BalanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 개인별 잔액 API (23: 결제·부담·잔액).
 *
 * <p>정산 최적화(24) 실행 전 "누가 얼마를 더 냈고 얼마를 더 내야 하는지"를 보여주는 화면용.
 */
@RestController
@RequestMapping("/api/groups/{groupId}/balances")
@RequiredArgsConstructor
public class BalanceController {

    private final BalanceService balanceService;

    /** 개인별 잔액 조회(23). */
    @GetMapping
    public ApiResponse<List<ParticipantBalanceResponse>> getBalances(@PathVariable Long groupId) {
        return ApiResponse.success(balanceService.getBalances(groupId));
    }
}
