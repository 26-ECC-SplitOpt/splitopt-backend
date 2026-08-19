package com.splitopt.backend.expense.controller;

import com.splitopt.backend.expense.dto.ExpenseCreateRequest;
import com.splitopt.backend.expense.dto.ExpenseResponse;
import com.splitopt.backend.expense.service.ExpenseService;
import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.global.exception.GlobalExceptionHandler;
import com.splitopt.backend.global.security.JwtTokenProvider;
import com.splitopt.backend.global.security.UserPrincipal;
import com.splitopt.backend.group.domain.GroupParticipant;
import com.splitopt.backend.group.service.GroupAccessGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 지출 컨트롤러 웹 계층 테스트 (API 17~19).
 *
 * <p>프론트가 실제로 보내는 요청 형태를 그대로 태워 계약을 고정한다. 이 API는 두 가지 이유로
 * 연동이 막혀 있었다 — 결제자를 {@code ?payerId=} 쿼리 파라미터로 요구한 것, 날짜를 시각까지
 * 있는 값으로 요구한 것. 둘 다 요청 본문 검증 <b>전에</b> 실패해 500으로 나갔다.
 */
@WebMvcTest(controllers = ExpenseController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class ExpenseControllerTest {

    private static final Long USER_ID = 7L;
    private static final Long PARTICIPANT_ID = 10L;

    /** 프론트(ExpenseForm.jsx)가 보내는 본문 그대로. payerId도 시각도 없다. */
    private static final String FRONTEND_PAYLOAD = """
            {"title":"d","amount":1000,"category":"SHOPPING","expenseDate":"2026-08-15",
             "memo":"d","splitMethod":"EQUAL",
             "shares":[{"participantId":10,"amount":500},{"participantId":11,"amount":500}]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExpenseService expenseService;

    @MockitoBean
    private GroupAccessGuard groupAccessGuard;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(Long userId) {
        var auth = new UsernamePasswordAuthenticationToken(new UserPrincipal(userId), null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void memberOf(Long groupId) {
        loginAs(USER_ID);
        GroupParticipant me = mock(GroupParticipant.class);
        given(me.getId()).willReturn(PARTICIPANT_ID);
        given(groupAccessGuard.requireActiveParticipant(groupId, USER_ID)).willReturn(me);
    }

    private ExpenseResponse sampleExpense() {
        return new ExpenseResponse(1L, "d", new BigDecimal("1000"), "SHOPPING", "d",
                LocalDate.of(2026, 8, 15),
                new ExpenseResponse.PayerInfo(PARTICIPANT_ID, "주영"),
                null,
                List.of(new ExpenseResponse.ShareInfo(PARTICIPANT_ID, "주영", new BigDecimal("1000"))));
    }

    @Test
    @DisplayName("지출 등록(17): 프론트가 보내는 본문 그대로 → 201")
    void createExpense_acceptsFrontendPayload() throws Exception {
        memberOf(2L);
        given(expenseService.createExpense(eq(2L), eq(PARTICIPANT_ID), any())).willReturn(sampleExpense());

        mockMvc.perform(post("/api/groups/2/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FRONTEND_PAYLOAD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.expenseDate").value("2026-08-15"));
    }

    @Test
    @DisplayName("지출 등록(17): 결제자는 로그인 사용자로 고정 — 쿼리 파라미터로 지정할 수 없다")
    void createExpense_payerComesFromPrincipal() throws Exception {
        memberOf(2L);
        given(expenseService.createExpense(anyLong(), anyLong(), any())).willReturn(sampleExpense());

        // 예전 seam(?payerId=)으로 다른 참여자를 지정해도 무시된다.
        mockMvc.perform(post("/api/groups/2/expenses")
                        .param("payerId", "999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FRONTEND_PAYLOAD))
                .andExpect(status().isCreated());

        verify(expenseService).createExpense(eq(2L), eq(PARTICIPANT_ID), any());
    }

    @Test
    @DisplayName("지출 등록(17): 날짜는 yyyy-MM-dd로 받는다")
    void createExpense_bindsPlainDate() throws Exception {
        memberOf(2L);
        given(expenseService.createExpense(anyLong(), anyLong(), any())).willReturn(sampleExpense());

        mockMvc.perform(post("/api/groups/2/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FRONTEND_PAYLOAD))
                .andExpect(status().isCreated());

        var captor = org.mockito.ArgumentCaptor.forClass(ExpenseCreateRequest.class);
        verify(expenseService).createExpense(anyLong(), anyLong(), captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
                LocalDate.of(2026, 8, 15), captor.getValue().expenseDate());
    }

    @Test
    @DisplayName("지출 등록(17): 날짜 누락 → 500이 아니라 400")
    void createExpense_missingDateIsBadRequest() throws Exception {
        memberOf(2L);

        mockMvc.perform(post("/api/groups/2/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"d","amount":1000,"category":"SHOPPING","splitMethod":"EQUAL",
                                 "shares":[{"participantId":10,"amount":1000}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("expenseDate"));
    }

    @Test
    @DisplayName("지출 등록(17): 읽을 수 없는 본문(잘못된 날짜 형식) → 500이 아니라 400")
    void createExpense_unparsableBodyIsBadRequest() throws Exception {
        memberOf(2L);

        // 프론트가 원인을 알 수 있도록 400으로 내려야 한다. 예전에는 여기서 500이 났다.
        mockMvc.perform(post("/api/groups/2/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"d","amount":1000,"category":"SHOPPING",
                                 "expenseDate":"2026년 8월 15일","splitMethod":"EQUAL",
                                 "shares":[{"participantId":10,"amount":1000}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("지출 등록(17): 비참여자 → 403, 서비스는 호출되지 않음")
    void createExpense_forbiddenForNonMember() throws Exception {
        loginAs(USER_ID);
        willThrow(new BusinessException(ErrorCode.ACCESS_DENIED, "이 모임의 참여자가 아닙니다."))
                .given(groupAccessGuard).requireActiveParticipant(eq(2L), anyLong());

        mockMvc.perform(post("/api/groups/2/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FRONTEND_PAYLOAD))
                .andExpect(status().isForbidden());

        verify(expenseService, never()).createExpense(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("지출 목록(18): 비참여자 → 403")
    void getExpenses_forbiddenForNonMember() throws Exception {
        loginAs(USER_ID);
        willThrow(new BusinessException(ErrorCode.ACCESS_DENIED, "이 모임의 참여자가 아닙니다."))
                .given(groupAccessGuard).requireMember(eq(2L), anyLong());

        mockMvc.perform(get("/api/groups/2/expenses"))
                .andExpect(status().isForbidden());

        verify(expenseService, never()).getExpenses(anyLong());
    }

    @Test
    @DisplayName("지출 상세(19): 응답의 날짜도 yyyy-MM-dd")
    void getExpense_returnsPlainDate() throws Exception {
        memberOf(2L);
        given(expenseService.getExpense(2L, 1L)).willReturn(sampleExpense());

        mockMvc.perform(get("/api/groups/2/expenses/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expenseDate").value("2026-08-15"));
    }

    @Test
    @DisplayName("인증 principal 없음 → 401")
    void unauthorizedWithoutPrincipal() throws Exception {
        mockMvc.perform(get("/api/groups/2/expenses"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("일정 연결: PATCH .../schedule 에 scheduleId → 200, 응답에 일정 정보")
    void linkSchedule() throws Exception {
        memberOf(2L);
        ExpenseResponse withSchedule = new ExpenseResponse(1L, "d", new BigDecimal("1000"), "SHOPPING", "d",
                LocalDate.of(2026, 8, 15),
                new ExpenseResponse.PayerInfo(PARTICIPANT_ID, "주영"),
                new ExpenseResponse.ScheduleInfo(5L, "맛집 탐방"),
                List.of(new ExpenseResponse.ShareInfo(PARTICIPANT_ID, "주영", new BigDecimal("1000"))));
        given(expenseService.linkSchedule(2L, 1L, 5L)).willReturn(withSchedule);

        mockMvc.perform(patch("/api/groups/2/expenses/1/schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduleId":5}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.schedule.scheduleId").value(5))
                .andExpect(jsonPath("$.data.schedule.title").value("맛집 탐방"));
    }

    @Test
    @DisplayName("일정 연결 해제: scheduleId를 null로 보내면 서비스에 null이 전달된다")
    void unlinkScheduleWithExplicitNull() throws Exception {
        memberOf(2L);
        given(expenseService.linkSchedule(anyLong(), anyLong(), any())).willReturn(sampleExpense());

        mockMvc.perform(patch("/api/groups/2/expenses/1/schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduleId":null}"""))
                .andExpect(status().isOk());

        verify(expenseService).linkSchedule(2L, 1L, null);
    }

    @Test
    @DisplayName("일정 연결 해제: 빈 본문 {}도 해제로 본다")
    void unlinkScheduleWithEmptyBody() throws Exception {
        memberOf(2L);
        given(expenseService.linkSchedule(anyLong(), anyLong(), any())).willReturn(sampleExpense());

        mockMvc.perform(patch("/api/groups/2/expenses/1/schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        verify(expenseService).linkSchedule(2L, 1L, null);
    }

    @Test
    @DisplayName("일정 연결: 모임 비참여자면 403, 서비스는 호출되지 않는다")
    void linkSchedule_nonMemberForbidden() throws Exception {
        loginAs(USER_ID);
        willThrow(new BusinessException(ErrorCode.ACCESS_DENIED, "이 모임의 참여자가 아닙니다."))
                .given(groupAccessGuard).requireActiveParticipant(2L, USER_ID);

        mockMvc.perform(patch("/api/groups/2/expenses/1/schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduleId":5}"""))
                .andExpect(status().isForbidden());

        verify(expenseService, never()).linkSchedule(anyLong(), anyLong(), any());
    }
}
