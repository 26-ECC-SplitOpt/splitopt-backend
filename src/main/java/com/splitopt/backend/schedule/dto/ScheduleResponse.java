package com.splitopt.backend.schedule.dto;

import com.splitopt.backend.schedule.domain.Schedule;

import java.time.LocalDateTime;

public record ScheduleResponse(
        Long id,
        String title,
        String location,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String memo
) {
    public static ScheduleResponse from(Schedule schedule) {
        return new ScheduleResponse(
                schedule.getId(),
                schedule.getTitle(),
                schedule.getLocation(),
                schedule.getStartAt(),
                schedule.getEndAt(),
                schedule.getMemo()
        );
    }
}