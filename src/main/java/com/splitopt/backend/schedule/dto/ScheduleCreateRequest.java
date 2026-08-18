package com.splitopt.backend.schedule.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 일정 등록·수정 요청 (API 33·35).
 *
 * <p>{@code endAt}은 선택이지만, 넣는다면 시작보다 뒤여야 한다. 거꾸로 들어가면 기간의 길이가
 * 0 이하가 되어 <b>오류 없이 조용히</b> 예산 초과 예측(40)이 불가로 빠진다. 화면에는 아무 설명도
 * 없이 예측만 사라져 원인을 찾기 어렵다. 저장 전에 막는다.
 */
public record ScheduleCreateRequest(
        @NotBlank(message = "일정 제목을 입력해주세요.")
        String title,
        String location, // 선택
        @NotNull(message = "시작 일시를 입력해주세요.")
        LocalDateTime startAt,
        LocalDateTime endAt, // 선택
        String memo // 선택
) {
    /** 종료가 시작보다 앞서지 않아야 한다. 같은 시각은 허용(순간 일정). */
    @AssertTrue(message = "종료 일시는 시작 일시보다 뒤여야 합니다.")
    public boolean isEndAtAfterStartAt() {
        return endAt == null || startAt == null || !endAt.isBefore(startAt);
    }
}
