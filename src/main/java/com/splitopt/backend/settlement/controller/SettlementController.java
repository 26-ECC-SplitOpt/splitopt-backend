package com.splitopt.backend.settlement.controller;

import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.global.response.ApiResponse;
import com.splitopt.backend.settlement.domain.SettlementStatus;
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

    /** 정산 결과 전체 조회(25) / 상태별 조회(28, ?status=pending|completed). */
    @GetMapping
    public ApiResponse<List<SettlementResponse>> getSettlements(
            @PathVariable Long groupId,
            @RequestParam(required = false) String status) {
        List<SettlementResponse> result = (status == null || status.isBlank())
                ? settlementService.getSettlements(groupId)
                : settlementService.getByStatus(groupId, parseStatus(status));
        return ApiResponse.success(result);
    }

    /** status 파라미터 파싱 — 지원하지 않는 값은 400으로 거부(조용한 전체 반환 방지). */
    private SettlementStatus parseStatus(String status) {
        try {
            return SettlementStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 정산 상태입니다: " + status);
        }
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
        return ApiResponse.success(settlementService.complete(groupId, settlementId), "정산 완료 처리되었습니다.");
    }
}
