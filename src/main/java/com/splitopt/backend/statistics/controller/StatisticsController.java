package com.splitopt.backend.statistics.controller;

import com.splitopt.backend.global.response.ApiResponse;
import com.splitopt.backend.statistics.dto.CategoryStatisticsResponse;
import com.splitopt.backend.statistics.dto.GroupStatisticsResponse;
import com.splitopt.backend.statistics.dto.ParticipantStatisticsResponse;
import com.splitopt.backend.statistics.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 지출 통계 조회 API (30~32). 모두 저장 없이 조회만 하는 파생 데이터. */
@RestController
@RequestMapping("/api/groups/{groupId}/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    /** 모임 전체 지출 통계(30). */
    @GetMapping
    public ApiResponse<GroupStatisticsResponse> getGroupStatistics(@PathVariable Long groupId) {
        return ApiResponse.success(statisticsService.getGroupStatistics(groupId));
    }

    /** 카테고리별 지출 통계(31). */
    @GetMapping("/categories")
    public ApiResponse<CategoryStatisticsResponse> getCategoryStatistics(@PathVariable Long groupId) {
        return ApiResponse.success(statisticsService.getCategoryStatistics(groupId));
    }

    /** 참여자별 지출 통계(32). */
    @GetMapping("/participants")
    public ApiResponse<ParticipantStatisticsResponse> getParticipantStatistics(@PathVariable Long groupId) {
        return ApiResponse.success(statisticsService.getParticipantStatistics(groupId));
    }
}