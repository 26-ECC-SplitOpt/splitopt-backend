package com.splitopt.backend.budget.controller;

import com.splitopt.backend.budget.dto.BudgetForecastResponse;
import com.splitopt.backend.budget.dto.BudgetForecastResponse.Basis;
import com.splitopt.backend.budget.dto.BudgetResponse;
import com.splitopt.backend.budget.service.BudgetService;
import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.global.exception.GlobalExceptionHandler;
import com.splitopt.backend.global.security.JwtTokenProvider;
import com.splitopt.backend.global.security.UserPrincipal;
import com.splitopt.backend.group.service.GroupAccessGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 예산 컨트롤러 웹 계층 테스트 (API 38·39·40).
 * HTTP 상태 매핑(200/400/401/403/404)·@Valid·JSON 직렬화를 검증한다.
 *
 * <p>예산은 모임 참여자만 조회·설정할 수 있다 — 비참여자 403은 {@link GroupAccessGuard}가 막는다.
 */
@WebMvcTest(controllers = BudgetController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class BudgetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BudgetService budgetService;

    @MockitoBean
    private GroupAccessGuard groupAccessGuard;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    /** 기본 상태: 로그인한 참여자(가드 통과). 비참여자 케이스는 개별 테스트에서 다시 세팅한다. */
    @BeforeEach
    void loginAsMember() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(7L), null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /** 예산 20만원에 사용액 5만원인 상태. */
    private BudgetResponse sampleBudget() {
        return new BudgetResponse(1L, new BigDecimal("200000"), new BigDecimal("50000"),
                new BigDecimal("150000"), false, new BigDecimal("25.0"));
    }

    @Test
    @DisplayName("예산 설정(38) → 200, 설정 직후에도 현황이 함께 온다")
    void upsert_ok() throws Exception {
        given(budgetService.upsert(eq(1L), any())).willReturn(sampleBudget());

        mockMvc.perform(put("/api/groups/1/budget")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":200000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.amount").value(200000))
                .andExpect(jsonPath("$.data.spent").value(50000))
                .andExpect(jsonPath("$.data.remaining").value(150000));
    }

    @Test
    @DisplayName("예산 설정(38) 음수 금액 → 400")
    void upsert_negative() throws Exception {
        mockMvc.perform(put("/api/groups/1/budget")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("입력값을 확인해주세요."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("예산 설정(38) 금액 누락 → 400")
    void upsert_missingAmount() throws Exception {
        mockMvc.perform(put("/api/groups/1/budget")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("입력값을 확인해주세요."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("예산 현황 조회(39) → 200, 금액·사용액·잔여·초과 여부·사용률")
    void getBudget_ok() throws Exception {
        given(budgetService.getBudget(1L)).willReturn(sampleBudget());

        mockMvc.perform(get("/api/groups/1/budget"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.groupId").value(1))
                .andExpect(jsonPath("$.data.amount").value(200000))
                .andExpect(jsonPath("$.data.spent").value(50000))
                .andExpect(jsonPath("$.data.remaining").value(150000))
                .andExpect(jsonPath("$.data.exceeded").value(false))
                .andExpect(jsonPath("$.data.usageRate").value(25.0));
    }

    @Test
    @DisplayName("예산 현황 조회(39) 초과 상태 → 잔여는 음수, exceeded true")
    void getBudget_exceeded() throws Exception {
        given(budgetService.getBudget(1L)).willReturn(new BudgetResponse(
                1L, new BigDecimal("200000"), new BigDecimal("250000"),
                new BigDecimal("-50000"), true, new BigDecimal("125.0")));

        mockMvc.perform(get("/api/groups/1/budget"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remaining").value(-50000))
                .andExpect(jsonPath("$.data.exceeded").value(true))
                .andExpect(jsonPath("$.data.usageRate").value(125.0));
    }

    @Test
    @DisplayName("초과 예측(40) → 200, 기간·경과율·예상 총액·초과 예상")
    void getForecast_ok() throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 8, 5, 0, 0);
        given(budgetService.getForecast(1L)).willReturn(new BudgetForecastResponse(
                1L, new BigDecimal("150000"), new BigDecimal("100000"), Basis.SCHEDULE,
                start, end, new BigDecimal("50.0"), new BigDecimal("200000"),
                new BigDecimal("50000"), true));

        mockMvc.perform(get("/api/groups/1/budget/forecast"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.basis").value("SCHEDULE"))
                .andExpect(jsonPath("$.data.elapsedRate").value(50.0))
                .andExpect(jsonPath("$.data.projectedTotal").value(200000))
                .andExpect(jsonPath("$.data.projectedOverspend").value(50000))
                .andExpect(jsonPath("$.data.willExceed").value(true))
                .andExpect(jsonPath("$.data.periodStart").exists());
    }

    @Test
    @DisplayName("초과 예측(40) 근거 없음 → 200, basis=NONE이고 예측 필드는 비어 있다")
    void getForecast_noBasis() throws Exception {
        given(budgetService.getForecast(1L)).willReturn(
                BudgetForecastResponse.notForecastable(1L, new BigDecimal("150000"), new BigDecimal("100000")));

        mockMvc.perform(get("/api/groups/1/budget/forecast"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.basis").value("NONE"))
                .andExpect(jsonPath("$.data.amount").value(150000))
                .andExpect(jsonPath("$.data.spent").value(100000))
                .andExpect(jsonPath("$.data.projectedTotal").doesNotExist())
                .andExpect(jsonPath("$.data.willExceed").doesNotExist());
    }

    @Test
    @DisplayName("초과 예측(40) 예산 미설정 → 404")
    void getForecast_notFound() throws Exception {
        given(budgetService.getForecast(1L))
                .willThrow(new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "설정된 예산이 없습니다."));

        mockMvc.perform(get("/api/groups/1/budget/forecast"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("설정된 예산이 없습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("예산 미설정 → 404")
    void getBudget_notFound() throws Exception {
        given(budgetService.getBudget(1L))
                .willThrow(new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "설정된 예산이 없습니다."));

        mockMvc.perform(get("/api/groups/1/budget"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("설정된 예산이 없습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("예산 설정(38) 비참여자 → 403, 남의 모임 예산을 바꿀 수 없다")
    void upsert_forbiddenForNonMember() throws Exception {
        denyMembership();

        mockMvc.perform(put("/api/groups/1/budget")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":200000}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("이 모임의 참여자가 아닙니다."));

        verify(budgetService, never()).upsert(anyLong(), any());
    }

    @Test
    @DisplayName("예산 현황 조회(39) 비참여자 → 403")
    void getBudget_forbiddenForNonMember() throws Exception {
        denyMembership();

        mockMvc.perform(get("/api/groups/1/budget"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("이 모임의 참여자가 아닙니다."));

        verify(budgetService, never()).getBudget(anyLong());
    }

    @Test
    @DisplayName("초과 예측(40) 비참여자 → 403")
    void getForecast_forbiddenForNonMember() throws Exception {
        denyMembership();

        mockMvc.perform(get("/api/groups/1/budget/forecast"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("이 모임의 참여자가 아닙니다."));

        verify(budgetService, never()).getForecast(anyLong());
    }

    @Test
    @DisplayName("인증 principal 없음 → 401")
    void getBudget_unauthorizedWithoutPrincipal() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/groups/1/budget"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    /** 로그인은 했지만 이 모임 참여자가 아닌 상태로 만든다. */
    private void denyMembership() {
        willThrow(new BusinessException(ErrorCode.ACCESS_DENIED, "이 모임의 참여자가 아닙니다."))
                .given(groupAccessGuard).requireMember(eq(1L), anyLong());
    }
}
