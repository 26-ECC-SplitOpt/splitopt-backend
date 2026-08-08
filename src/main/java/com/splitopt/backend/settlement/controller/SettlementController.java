package com.splitopt.backend.settlement.controller;

import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.global.response.ApiResponse;
import com.splitopt.backend.settlement.domain.SettlementStatus;
import com.splitopt.backend.settlement.dto.MySettlementsResponse;
import com.splitopt.backend.settlement.dto.SettlementResponse;
import com.splitopt.backend.settlement.dto.SettlementStatusChangeRequest;
import com.splitopt.backend.settlement.dto.SettlementSummaryResponse;
import com.splitopt.backend.settlement.service.SettlementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 정산 조회·상태 관리 API.
 * <p>구현: 25(전체 조회) · 26(내 정산) · 28(미정산 조회) · 29(요약) · 27(상태 전이 SEND/CONFIRM/CANCEL).
 * <p>미구현(의존성 대기): 24 최적화 실행(잔액=지출 파트 필요).
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

    /**
     * 내 정산 내역 조회(26) — 보낼/받을/완료로 분류.
     *
     * <p>{@code X-User-Id} 헤더는 로그인 사용자 식별용 임시 seam이다.
     * 인증(1~4) 연동 시 로그인 사용자로 대체한다.
     */
    @GetMapping("/me")
    public ApiResponse<MySettlementsResponse> getMySettlements(
            @PathVariable Long groupId,
            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.success(settlementService.getMySettlements(groupId, userId));
    }

    /**
     * 정산 상태 변경(27) — 전이형(SEND/CONFIRM/CANCEL).
     *
     * <p>{@code X-Participant-Id} 헤더는 요청자(참여자) 식별용 임시 seam이다.
     * 인증(1~4) 연동 시 로그인 참여자로 대체한다.
     */
    @PatchMapping("/{settlementId}/status")
    public ApiResponse<SettlementResponse> changeStatus(
            @PathVariable Long groupId,
            @PathVariable Long settlementId,
            @RequestHeader("X-Participant-Id") Long requesterParticipantId,
            @Valid @RequestBody SettlementStatusChangeRequest request) {
        return ApiResponse.success(
                settlementService.changeStatus(groupId, settlementId, request.action(), requesterParticipantId),
                "정산 상태가 변경되었습니다.");
    }
}
