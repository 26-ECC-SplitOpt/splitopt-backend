package com.splitopt.backend.settlement.controller;

import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.global.response.ApiResponse;
import com.splitopt.backend.global.security.UserPrincipal;
import com.splitopt.backend.group.service.GroupAccessGuard;
import com.splitopt.backend.settlement.domain.SettlementStatus;
import com.splitopt.backend.settlement.dto.MySettlementsResponse;
import com.splitopt.backend.settlement.dto.SettlementListResponse;
import com.splitopt.backend.settlement.dto.SettlementOptimizeResponse;
import com.splitopt.backend.settlement.dto.SettlementResponse;
import com.splitopt.backend.settlement.dto.SettlementStatusChangeRequest;
import com.splitopt.backend.settlement.dto.SettlementSummaryResponse;
import com.splitopt.backend.settlement.service.SettlementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 정산 실행·조회·상태 관리 API.
 * <p>구현: 24(최적화 실행) · 25(전체 조회) · 26(내 정산) · 28(미정산 조회) · 29(요약) ·
 * 27(상태 전이 SEND/CONFIRM/CANCEL). 개인별 잔액(23)은 {@link BalanceController}.
 *
 * <p><b>인가</b>: 요청자는 오직 인증 principal에서만 얻는다. 모든 핸들러는 먼저
 * {@link GroupAccessGuard}로 로그인 사용자가 경로 모임의 활성 참여자인지 확인한다 —
 * groupId만 알면 남의 모임 정산을 조회·변경할 수 있던 문제(IDOR)를 여기서 막는다.
 * 상태 전이(27)의 요청자 참여자 id도 가드가 해석한 값을 쓰므로 클라이언트가 위장할 수 없다.
 */
@RestController
@RequestMapping("/api/groups/{groupId}/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;
    private final GroupAccessGuard groupAccessGuard;

    /**
     * 정산 최적화 실행(24).
     *
     * <p>지출 원장에서 순잔액(이미 오간 SENT·COMPLETED 정산을 상계한 잔액)을 산출해 송금 목록을
     * 다시 만든다. 재실행 시 기존 PENDING은 대체되고 SENT·COMPLETED는 보존된다.
     */
    @PostMapping("/optimize")
    public ApiResponse<SettlementOptimizeResponse> optimize(
            @PathVariable Long groupId,
            @AuthenticationPrincipal UserPrincipal principal) {
        groupAccessGuard.requireMember(groupId, userId(principal));
        return ApiResponse.success(
                SettlementOptimizeResponse.of(settlementService.optimize(groupId)),
                "정산 최적화를 실행했습니다.");
    }

    /** 정산 결과 전체 조회(25) / 상태별 조회(28, ?status=pending|completed). */
    @GetMapping
    public ApiResponse<SettlementListResponse> getSettlements(
            @PathVariable Long groupId,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String status) {
        groupAccessGuard.requireMember(groupId, userId(principal));
        List<SettlementResponse> result = (status == null || status.isBlank())
                ? settlementService.getSettlements(groupId)
                : settlementService.getByStatus(groupId, parseStatus(status));
        return ApiResponse.success(SettlementListResponse.of(result));
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
    public ApiResponse<SettlementSummaryResponse> getSummary(
            @PathVariable Long groupId,
            @AuthenticationPrincipal UserPrincipal principal) {
        groupAccessGuard.requireMember(groupId, userId(principal));
        return ApiResponse.success(settlementService.getSummary(groupId));
    }

    /**
     * 내 정산 내역 조회(26) — 보낼/받을/완료로 분류.
     *
     * <p>대상은 로그인 사용자 본인 고정이다. 비참여자는 빈 응답이 아니라 403으로 끊는다 —
     * 빈 응답은 "그 모임에 내 정산이 없다"는 정보를 남의 모임에 대해 알려주는 셈이다.
     */
    @GetMapping("/me")
    public ApiResponse<MySettlementsResponse> getMySettlements(
            @PathVariable Long groupId,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = userId(principal);
        groupAccessGuard.requireMember(groupId, userId);
        return ApiResponse.success(settlementService.getMySettlements(groupId, userId));
    }

    /**
     * 정산 상태 변경(27) — 전이형(SEND/CONFIRM/CANCEL).
     *
     * <p>요청자의 참여자 id는 로그인 사용자로부터 서버가 해석한다. 송금 완료·취소는 보내는
     * 사람(from)만, 송금 확인은 받는 사람(to)만 — 그 외 403.
     */
    @PatchMapping("/{settlementId}/status")
    public ApiResponse<SettlementResponse> changeStatus(
            @PathVariable Long groupId,
            @PathVariable Long settlementId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SettlementStatusChangeRequest request) {
        Long requesterParticipantId =
                groupAccessGuard.requireActiveParticipant(groupId, userId(principal)).getId();
        return ApiResponse.success(
                settlementService.changeStatus(groupId, settlementId, request.action(), requesterParticipantId),
                "정산 상태가 변경되었습니다.");
    }

    /** principal이 비어 있으면(필터가 인증을 못 채운 경우) 401. */
    private Long userId(UserPrincipal principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return principal.getUserId();
    }
}
