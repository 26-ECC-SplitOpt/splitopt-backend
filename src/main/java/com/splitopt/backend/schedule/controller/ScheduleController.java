package com.splitopt.backend.schedule.controller;

import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.global.response.ApiResponse;
import com.splitopt.backend.global.security.UserPrincipal;
import com.splitopt.backend.group.service.GroupAccessGuard;
import com.splitopt.backend.schedule.dto.ScheduleCreateRequest;
import com.splitopt.backend.schedule.dto.ScheduleExpensesResponse;
import com.splitopt.backend.schedule.dto.ScheduleResponse;
import com.splitopt.backend.schedule.service.ScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 일정 API (33~37).
 *
 * <p><b>인가</b>: 모임 참여자만 다룰 수 있다. 경로에 {@code groupId}만 있어 인증만으로는
 * 호출자가 그 모임 사람인지 알 수 없으므로, 지출(17~21)·정산(24~29)과 같은 기준으로
 * {@link GroupAccessGuard}를 먼저 통과시킨다.
 *
 * <p>조회(34·37)뿐 아니라 등록·수정·삭제(33·35·36)도 참여자 전체에게 연다. 일정은 모임의
 * 공동 일정이라 누가 만들었는지로 나눌 근거가 없고, 지출과 달리 금액을 담지 않아 잘못
 * 고쳐도 정산이 틀어지지 않는다 — 지출의 일정 연결을 참여자 전체에 연 것과 같은 이유다.
 */
@RestController
@RequestMapping("/api/groups/{groupId}/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final GroupAccessGuard groupAccessGuard;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ScheduleResponse> createSchedule(
            @PathVariable Long groupId, @Valid @RequestBody ScheduleCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireMember(groupId, principal);
        return ApiResponse.success(scheduleService.createSchedule(groupId, request), "일정이 등록되었습니다.");
    }

    @GetMapping
    public ApiResponse<List<ScheduleResponse>> getSchedules(
            @PathVariable Long groupId,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireMember(groupId, principal);
        return ApiResponse.success(scheduleService.getSchedules(groupId));
    }

    @PutMapping("/{scheduleId}")
    public ApiResponse<ScheduleResponse> updateSchedule(
            @PathVariable Long groupId, @PathVariable Long scheduleId,
            @Valid @RequestBody ScheduleCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireMember(groupId, principal);
        return ApiResponse.success(scheduleService.updateSchedule(groupId, scheduleId, request), "일정이 수정되었습니다.");
    }

    @DeleteMapping("/{scheduleId}")
    public ApiResponse<Void> deleteSchedule(
            @PathVariable Long groupId, @PathVariable Long scheduleId,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireMember(groupId, principal);
        scheduleService.deleteSchedule(groupId, scheduleId);
        return ApiResponse.success();
    }

    @GetMapping("/{scheduleId}/expenses")
    public ApiResponse<ScheduleExpensesResponse> getScheduleExpenses(
            @PathVariable Long groupId, @PathVariable Long scheduleId,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireMember(groupId, principal);
        return ApiResponse.success(scheduleService.getScheduleExpenses(groupId, scheduleId));
    }

    /** 로그인 사용자가 이 모임의 활성 참여자인지 확인한다 — 아니면 403, 없는 모임이면 404. */
    private void requireMember(Long groupId, UserPrincipal principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        groupAccessGuard.requireMember(groupId, principal.getUserId());
    }
}
