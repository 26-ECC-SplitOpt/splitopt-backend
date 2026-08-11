package com.splitopt.backend.settlement.controller;

import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.global.response.ApiResponse;
import com.splitopt.backend.global.security.UserPrincipal;
import com.splitopt.backend.group.service.GroupAccessGuard;
import com.splitopt.backend.settlement.dto.ParticipantBalanceResponse;
import com.splitopt.backend.settlement.service.BalanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 개인별 잔액 API (23: 결제·부담·잔액).
 *
 * <p>정산 최적화(24) 실행 전 "누가 얼마를 더 냈고 얼마를 더 내야 하는지"를 보여주는 화면용.
 *
 * <p><b>인가</b>: 모임 참여자만 조회할 수 있다. 잔액은 참여자별 결제·부담 금액이 그대로
 * 드러나는 데이터라 비참여자에게 열려 있으면 안 된다.
 */
@RestController
@RequestMapping("/api/groups/{groupId}/balances")
@RequiredArgsConstructor
public class BalanceController {

    private final BalanceService balanceService;
    private final GroupAccessGuard groupAccessGuard;

    /** 개인별 잔액 조회(23). */
    @GetMapping
    public ApiResponse<List<ParticipantBalanceResponse>> getBalances(
            @PathVariable Long groupId,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        groupAccessGuard.requireMember(groupId, principal.getUserId());
        return ApiResponse.success(balanceService.getBalances(groupId));
    }
}
