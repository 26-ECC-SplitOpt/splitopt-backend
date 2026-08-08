package com.splitopt.backend.schedule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record ScheduleCreateRequest(
        @NotBlank(message = "일정 제목을 입력해주세요.")
        String title,

        String location, // 선택

        @NotNull(message = "시작 일시를 입력해주세요.")
        LocalDateTime startAt,

        LocalDateTime endAt, // 선택

        String memo // 선택
) {}