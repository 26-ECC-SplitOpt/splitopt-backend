package com.splitopt.backend.statistics.controller;

import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.global.response.ApiResponse;
import com.splitopt.backend.global.security.UserPrincipal;
import com.splitopt.backend.group.service.GroupAccessGuard;
import com.splitopt.backend.statistics.dto.CategoryStatisticsResponse;
import com.splitopt.backend.statistics.dto.GroupStatisticsResponse;
import com.splitopt.backend.statistics.dto.ParticipantStatisticsResponse;
import com.splitopt.backend.statistics.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 지출 통계 조회 API (30~32). 모두 저장 없이 조회만 하는 파생 데이터.
 *
 * <p><b>인가</b>: 모임 참여자만 조회할 수 있다. 저장을 하지 않는다고 열어 둘 수 있는 데이터가
 * 아니다 — 참여자별 통계(32)는 참여자 이름과 각자의 결제·부담 금액을 그대로 내려주고,
 * 전체·카테고리 통계(30·31)도 그 모임이 어디에 얼마를 썼는지를 드러낸다.
 * 경로에 {@code groupId}만 있어 인증만으로는 호출자가 그 모임 사람인지 알 수 없으므로,
 * 잔액(23)·정산(24~29)과 같은 기준으로 {@link GroupAccessGuard}를 먼저 통과시킨다.
 */
@RestController
@RequestMapping("/api/groups/{groupId}/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;
    private final GroupAccessGuard groupAccessGuard;

    /** 모임 전체 지출 통계(30). */
    @GetMapping
    public ApiResponse<GroupStatisticsResponse> getGroupStatistics(
            @PathVariable Long groupId,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireMember(groupId, principal);
        return ApiResponse.success(statisticsService.getGroupStatistics(groupId));
    }

    /** 카테고리별 지출 통계(31). */
    @GetMapping("/categories")
    public ApiResponse<CategoryStatisticsResponse> getCategoryStatistics(
            @PathVariable Long groupId,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireMember(groupId, principal);
        return ApiResponse.success(statisticsService.getCategoryStatistics(groupId));
    }

    /** 참여자별 지출 통계(32). */
    @GetMapping("/participants")
    public ApiResponse<ParticipantStatisticsResponse> getParticipantStatistics(
            @PathVariable Long groupId,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireMember(groupId, principal);
        return ApiResponse.success(statisticsService.getParticipantStatistics(groupId));
    }

    /** 로그인 사용자가 이 모임의 활성 참여자인지 확인한다 — 아니면 403, 없는 모임이면 404. */
    private void requireMember(Long groupId, UserPrincipal principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        groupAccessGuard.requireMember(groupId, principal.getUserId());
    }
}
