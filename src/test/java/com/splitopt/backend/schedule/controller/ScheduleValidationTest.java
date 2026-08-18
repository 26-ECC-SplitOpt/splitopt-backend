package com.splitopt.backend.schedule.controller;

import com.splitopt.backend.global.exception.GlobalExceptionHandler;
import com.splitopt.backend.global.security.JwtTokenProvider;
import com.splitopt.backend.schedule.dto.ScheduleResponse;
import com.splitopt.backend.schedule.service.ScheduleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 일정 등록·수정(33·35)의 기간 검증.
 *
 * <p>종료가 시작보다 앞서도 그대로 저장됐다. 그러면 기간의 길이가 0 이하가 되어 예산 초과
 * 예측(40)이 <b>오류 없이 조용히</b> 불가로 빠진다. 화면에는 설명 없이 예측만 사라져 원인을
 * 찾기 어렵다. 저장 전에 400으로 막는다.
 */
@WebMvcTest(controllers = ScheduleController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class ScheduleValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScheduleService scheduleService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private static final String REVERSED = """
            {"title":"제주도","startAt":"2026-08-23T18:00:00","endAt":"2026-08-20T09:00:00"}""";

    private ScheduleResponse sample() {
        return new ScheduleResponse(1L, "제주도", "제주",
                LocalDateTime.of(2026, 8, 20, 9, 0),
                LocalDateTime.of(2026, 8, 23, 18, 0), null);
    }

    @Test
    @DisplayName("등록(33): 종료가 시작보다 앞서면 400, 저장되지 않는다")
    void create_rejectsReversedPeriod() throws Exception {
        mockMvc.perform(post("/api/groups/1/schedules")
                        .contentType(MediaType.APPLICATION_JSON).content(REVERSED))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("endAtAfterStartAt"));

        verify(scheduleService, never()).createSchedule(anyLong(), any());
    }

    @Test
    @DisplayName("수정(35): 종료가 시작보다 앞서면 400")
    void update_rejectsReversedPeriod() throws Exception {
        mockMvc.perform(put("/api/groups/1/schedules/1")
                        .contentType(MediaType.APPLICATION_JSON).content(REVERSED))
                .andExpect(status().isBadRequest());

        verify(scheduleService, never()).updateSchedule(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("종료 시각이 없으면 통과한다 — 선택 항목이다")
    void create_allowsMissingEndAt() throws Exception {
        given(scheduleService.createSchedule(anyLong(), any())).willReturn(sample());

        mockMvc.perform(post("/api/groups/1/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"제주도","startAt":"2026-08-20T09:00:00"}"""))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("시작과 종료가 같은 시각이면 통과한다 — 순간 일정")
    void create_allowsSameInstant() throws Exception {
        given(scheduleService.createSchedule(anyLong(), any())).willReturn(sample());

        mockMvc.perform(post("/api/groups/1/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"체크인","startAt":"2026-08-20T15:00:00","endAt":"2026-08-20T15:00:00"}"""))
                .andExpect(status().isCreated());
    }
}
