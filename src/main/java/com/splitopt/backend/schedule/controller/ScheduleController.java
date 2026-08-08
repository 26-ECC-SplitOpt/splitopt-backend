package com.splitopt.backend.schedule.controller;

import com.splitopt.backend.global.response.ApiResponse;
import com.splitopt.backend.schedule.dto.ScheduleCreateRequest;
import com.splitopt.backend.schedule.dto.ScheduleExpensesResponse;
import com.splitopt.backend.schedule.dto.ScheduleResponse;
import com.splitopt.backend.schedule.service.ScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups/{groupId}/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ScheduleResponse> createSchedule(
            @PathVariable Long groupId, @Valid @RequestBody ScheduleCreateRequest request) {
        return ApiResponse.success(scheduleService.createSchedule(groupId, request), "일정이 등록되었습니다.");
    }

    @GetMapping
    public ApiResponse<List<ScheduleResponse>> getSchedules(@PathVariable Long groupId) {
        return ApiResponse.success(scheduleService.getSchedules(groupId));
    }

    @PutMapping("/{scheduleId}")
    public ApiResponse<ScheduleResponse> updateSchedule(
            @PathVariable Long groupId, @PathVariable Long scheduleId,
            @Valid @RequestBody ScheduleCreateRequest request) {
        return ApiResponse.success(scheduleService.updateSchedule(groupId, scheduleId, request), "일정이 수정되었습니다.");
    }

    @DeleteMapping("/{scheduleId}")
    public ApiResponse<Void> deleteSchedule(@PathVariable Long groupId, @PathVariable Long scheduleId) {
        scheduleService.deleteSchedule(groupId, scheduleId);
        return ApiResponse.success();
    }

    @GetMapping("/{scheduleId}/expenses")
    public ApiResponse<ScheduleExpensesResponse> getScheduleExpenses(
            @PathVariable Long groupId, @PathVariable Long scheduleId) {
        return ApiResponse.success(scheduleService.getScheduleExpenses(groupId, scheduleId));
    }
}