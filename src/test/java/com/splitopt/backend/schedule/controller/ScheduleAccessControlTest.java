package com.splitopt.backend.schedule.controller;

import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.global.exception.GlobalExceptionHandler;
import com.splitopt.backend.global.security.JwtTokenProvider;
import com.splitopt.backend.global.security.UserPrincipal;
import com.splitopt.backend.group.service.GroupAccessGuard;
import com.splitopt.backend.schedule.service.ScheduleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 일정 API(33~37)의 인가.
 *
 * <p>로그인만 하면 {@code groupId}를 아는 누구나 남의 모임 일정을 조회·등록·수정·<b>삭제</b>할
 * 수 있었다. 경로에 모임 id만 있고 참여 여부를 아무도 확인하지 않았기 때문이다
 * (컨트롤러는 로그인 사용자를 받지도 않았고, 서비스는 모임의 존재만 확인했다).
 *
 * <p>인가는 컨트롤러의 {@link GroupAccessGuard} 호출 <b>한 곳</b>에 있다. 그 호출이 사라지면
 * 해당 엔드포인트가 통째로 열리므로, 다섯 엔드포인트 각각에 대해 비참여자가 막히고
 * 서비스까지 닿지 않는다는 것을 남긴다.
 */
@WebMvcTest(controllers = ScheduleController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class ScheduleAccessControlTest {

    private static final String BODY = """
            {"title":"1일차","startAt":"2026-08-20T09:00:00"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScheduleService scheduleService;

    @MockitoBean
    private GroupAccessGuard groupAccessGuard;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    /** 로그인은 했지만 이 모임의 참여자는 아닌 사용자. */
    @BeforeEach
    void loginAsNonMember() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(99L), null, List.of()));
        willThrow(new BusinessException(ErrorCode.ACCESS_DENIED, "이 모임의 참여자가 아닙니다."))
                .given(groupAccessGuard).requireMember(eq(1L), anyLong());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("일정 목록 조회(34) 비참여자 → 403, 서비스는 호출되지 않음")
    void getSchedules_forbiddenForNonMember() throws Exception {
        mockMvc.perform(get("/api/groups/1/schedules"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("이 모임의 참여자가 아닙니다."));

        verifyNoInteractions(scheduleService);
    }

    @Test
    @DisplayName("일정별 지출 조회(37) 비참여자 → 403, 서비스는 호출되지 않음")
    void getScheduleExpenses_forbiddenForNonMember() throws Exception {
        mockMvc.perform(get("/api/groups/1/schedules/1/expenses"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(scheduleService);
    }

    @Test
    @DisplayName("일정 등록(33) 비참여자 → 403, 남의 모임에 일정이 생기지 않는다")
    void createSchedule_forbiddenForNonMember() throws Exception {
        mockMvc.perform(post("/api/groups/1/schedules")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());

        verify(scheduleService, never()).createSchedule(anyLong(), any());
    }

    @Test
    @DisplayName("일정 수정(35) 비참여자 → 403")
    void updateSchedule_forbiddenForNonMember() throws Exception {
        mockMvc.perform(put("/api/groups/1/schedules/1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());

        verify(scheduleService, never()).updateSchedule(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("일정 삭제(36) 비참여자 → 403, 남의 모임 일정이 지워지지 않는다")
    void deleteSchedule_forbiddenForNonMember() throws Exception {
        mockMvc.perform(delete("/api/groups/1/schedules/1"))
                .andExpect(status().isForbidden());

        verify(scheduleService, never()).deleteSchedule(anyLong(), anyLong());
    }

    @Test
    @DisplayName("없는 모임의 일정 목록(34) → 404. 예전에는 빈 목록으로 200이었다")
    void getSchedules_notFound() throws Exception {
        willThrow(new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "모임을 찾을 수 없습니다."))
                .given(groupAccessGuard).requireMember(eq(404L), anyLong());

        mockMvc.perform(get("/api/groups/404/schedules"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("모임을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        verifyNoInteractions(scheduleService);
    }

    @Test
    @DisplayName("인증 principal 없음 → 401")
    void unauthorizedWithoutPrincipal() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/groups/1/schedules"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(scheduleService);
    }

    @Test
    @DisplayName("가드가 통과시키면(참여자) 서비스가 호출된다 — 가드가 전부를 막는 것은 아니다")
    void memberPassesThrough() throws Exception {
        // 모임 2는 @BeforeEach가 막아 둔 모임(1)이 아니라 가드가 그대로 통과시킨다.
        given(scheduleService.getSchedules(2L)).willReturn(List.of());

        mockMvc.perform(get("/api/groups/2/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(scheduleService).getSchedules(2L);
    }
}
