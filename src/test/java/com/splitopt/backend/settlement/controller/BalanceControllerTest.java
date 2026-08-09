package com.splitopt.backend.settlement.controller;

import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.global.exception.GlobalExceptionHandler;
import com.splitopt.backend.settlement.dto.ParticipantBalanceResponse;
import com.splitopt.backend.settlement.service.BalanceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 개인별 잔액 컨트롤러 웹 계층 테스트 (API 23).
 * 응답 필드(결제·부담·총잔액·순잔액)와 상태 매핑을 검증한다.
 */
@WebMvcTest(controllers = BalanceController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class BalanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BalanceService balanceService;

    @Test
    @DisplayName("개인별 잔액 조회(23) → 200, 결제·부담·총잔액·순잔액을 모두 내려준다")
    void getBalances_ok() throws Exception {
        given(balanceService.getBalances(1L)).willReturn(List.of(
                new ParticipantBalanceResponse(12L, "주영", true,
                        new BigDecimal("41000"), new BigDecimal("13668"),
                        new BigDecimal("27332"), new BigDecimal("13666")),
                new ParticipantBalanceResponse(8L, "수빈", true,
                        BigDecimal.ZERO, new BigDecimal("13666"),
                        new BigDecimal("-13666"), new BigDecimal("-13666"))));

        mockMvc.perform(get("/api/groups/1/balances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].participantId").value(12))
                .andExpect(jsonPath("$.data[0].name").value("주영"))
                .andExpect(jsonPath("$.data[0].active").value(true))
                .andExpect(jsonPath("$.data[0].paid").value(41000))
                .andExpect(jsonPath("$.data[0].owed").value(13668))
                .andExpect(jsonPath("$.data[0].balance").value(27332))
                .andExpect(jsonPath("$.data[0].netBalance").value(13666))
                .andExpect(jsonPath("$.data[1].balance").value(-13666));
    }

    @Test
    @DisplayName("개인별 잔액 조회(23) 탈퇴자는 active=false로 구분된다")
    void getBalances_withdrawnParticipant() throws Exception {
        given(balanceService.getBalances(1L)).willReturn(List.of(
                new ParticipantBalanceResponse(9L, "채빈", false,
                        BigDecimal.ZERO, new BigDecimal("10000"),
                        new BigDecimal("-10000"), new BigDecimal("-10000"))));

        mockMvc.perform(get("/api/groups/1/balances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].active").value(false));
    }

    @Test
    @DisplayName("개인별 잔액 조회(23) 지출이 없으면 → 200, 빈 잔액 목록")
    void getBalances_empty() throws Exception {
        given(balanceService.getBalances(1L)).willReturn(List.of());

        mockMvc.perform(get("/api/groups/1/balances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("개인별 잔액 조회(23) 없는 모임 → 404(ENTITY_NOT_FOUND)")
    void getBalances_notFound() throws Exception {
        given(balanceService.getBalances(404L))
                .willThrow(new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "모임을 찾을 수 없습니다."));

        mockMvc.perform(get("/api/groups/404/balances"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("모임을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
