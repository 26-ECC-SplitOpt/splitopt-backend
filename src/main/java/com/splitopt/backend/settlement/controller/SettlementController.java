package com.splitopt.backend.settlement.controller;

import com.splitopt.backend.global.response.ApiResponse;
import com.splitopt.backend.settlement.dto.SettlementResponse;
import com.splitopt.backend.settlement.dto.SettlementSummaryResponse;
import com.splitopt.backend.settlement.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 정산 조회·상태 관리 API.
 * <p>구현: 25(전체 조회) · 28(미정산 조회) · 29(요약) · 27(완료 처리).
 * <p>미구현(의존성 대기): 24 최적화 실행(잔액=지출 파트 필요) · 26 내 정산(인증 필요).
 */
@RestController
@RequestMapping("/api/groups/{groupId}/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    /** 정산 결과 전체 조회(25) / 미정산 조회(28, ?status=pending). */
    @GetMapping
    public ApiResponse<List<SettlementResponse>> getSettlements(
            @PathVariable Long groupId,
            @RequestParam(required = false) String status) {
        List<SettlementResponse> result = "pending".equalsIgnoreCase(status)
                ? settlementService.getPending(groupId)
                : settlementService.getSettlements(groupId);
        return ApiResponse.success(result);
    }

    /** 전체 정산 완료 여부 조회(29). */
    @GetMapping("/summary")
    public ApiResponse<SettlementSummaryResponse> getSummary(@PathVariable Long groupId) {
        return ApiResponse.success(settlementService.getSummary(groupId));
    }

    /** 정산 완료 처리(27). */
    @PatchMapping("/{settlementId}/status")
    public ApiResponse<SettlementResponse> complete(
            @PathVariable Long groupId,
            @PathVariable Long settlementId) {
        return ApiResponse.success(settlementService.complete(settlementId), "정산 완료 처리되었습니다.");
    }
}
