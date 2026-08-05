package com.splitopt.backend.budget.controller;

import com.splitopt.backend.budget.dto.BudgetResponse;
import com.splitopt.backend.budget.service.BudgetService;
import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 예산 컨트롤러 웹 계층 테스트 (API 38·39).
 * HTTP 상태 매핑(200/400/404)·@Valid·JSON 직렬화를 검증한다.
 */
@WebMvcTest(controllers = BudgetController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class BudgetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BudgetService budgetService;

    @Test
    @DisplayName("예산 설정(38) → 200")
    void upsert_ok() throws Exception {
        given(budgetService.upsert(eq(1L), any())).willReturn(new BudgetResponse(1L, new BigDecimal("200000")));

        mockMvc.perform(put("/api/groups/1/budget")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":200000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.amount").value(200000));
    }

    @Test
    @DisplayName("예산 설정(38) 음수 금액 → 400")
    void upsert_negative() throws Exception {
        mockMvc.perform(put("/api/groups/1/budget")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("예산 설정(38) 금액 누락 → 400")
    void upsert_missingAmount() throws Exception {
        mockMvc.perform(put("/api/groups/1/budget")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("예산 현황 조회(39) → 200")
    void getBudget_ok() throws Exception {
        given(budgetService.getBudget(1L)).willReturn(new BudgetResponse(1L, new BigDecimal("200000")));

        mockMvc.perform(get("/api/groups/1/budget"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(200000));
    }

    @Test
    @DisplayName("예산 미설정 → 404")
    void getBudget_notFound() throws Exception {
        given(budgetService.getBudget(1L))
                .willThrow(new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "설정된 예산이 없습니다."));

        mockMvc.perform(get("/api/groups/1/budget"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
