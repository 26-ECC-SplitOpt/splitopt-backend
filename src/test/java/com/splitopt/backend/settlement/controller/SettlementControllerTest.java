package com.splitopt.backend.settlement.controller;

import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.global.exception.GlobalExceptionHandler;
import com.splitopt.backend.settlement.domain.SettlementStatus;
import com.splitopt.backend.settlement.dto.MySettlementsResponse;
import com.splitopt.backend.settlement.dto.SettlementResponse;
import com.splitopt.backend.settlement.dto.SettlementStatusChangeRequest.Action;
import com.splitopt.backend.settlement.dto.SettlementSummaryResponse;
import com.splitopt.backend.settlement.service.SettlementService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 정산 컨트롤러 웹 계층 테스트 (API 25·26·27·28·29).
 * HTTP 상태 매핑(200/400/403/404/409)·헤더 바인딩·@Valid·JSON 직렬화를 검증한다.
 */
@WebMvcTest(controllers = SettlementController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class SettlementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SettlementService settlementService;

    private SettlementResponse sampleSettlement(String status) {
        return new SettlementResponse(5L, 10L, "주영", 8L, "수빈",
                new BigDecimal("18000"), status, null, null);
    }

    @Test
    @DisplayName("최적화 실행(24) → 200, 생성된 송금 목록과 안내 메시지")
    void optimize_ok() throws Exception {
        given(settlementService.optimize(1L)).willReturn(List.of(sampleSettlement("PENDING")));

        mockMvc.perform(post("/api/groups/1/settlements/optimize"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("정산 최적화를 실행했습니다."))
                .andExpect(jsonPath("$.data[0].fromName").value("주영"))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("최적화 실행(24) 정산할 것이 없으면 → 200, 빈 목록")
    void optimize_empty() throws Exception {
        given(settlementService.optimize(1L)).willReturn(List.of());

        mockMvc.perform(post("/api/groups/1/settlements/optimize"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("최적화 실행(24) 없는 모임 → 404(ENTITY_NOT_FOUND)")
    void optimize_notFound() throws Exception {
        given(settlementService.optimize(404L))
                .willThrow(new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "모임을 찾을 수 없습니다."));

        mockMvc.perform(post("/api/groups/404/settlements/optimize"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("모임을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("정산 결과 전체 조회(25) → 200")
    void getSettlements_ok() throws Exception {
        given(settlementService.getSettlements(1L)).willReturn(List.of(sampleSettlement("PENDING")));

        mockMvc.perform(get("/api/groups/1/settlements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("지원하지 않는 status 파라미터 → 400")
    void getSettlements_invalidStatus() throws Exception {
        mockMvc.perform(get("/api/groups/1/settlements").param("status", "pendng"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("지원하지 않는 정산 상태입니다: pendng"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("미정산 조회(28, ?status=pending) → 200, PENDING으로 변환되어 전달")
    void getSettlements_pending() throws Exception {
        given(settlementService.getByStatus(eq(1L), eq(SettlementStatus.PENDING)))
                .willReturn(List.of(sampleSettlement("PENDING")));

        mockMvc.perform(get("/api/groups/1/settlements").param("status", "pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("정산 요약(29) 진행 중 → 200, IN_PROGRESS")
    void getSummary_inProgress() throws Exception {
        given(settlementService.getSummary(1L)).willReturn(SettlementSummaryResponse.of(3, 1));

        mockMvc.perform(get("/api/groups/1/settlements/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.allCompleted").value(false))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("정산 요약(29) 정산 전(total=0) → 200, NOT_STARTED (완료와 구분)")
    void getSummary_notStarted() throws Exception {
        given(settlementService.getSummary(1L)).willReturn(SettlementSummaryResponse.of(0, 0));

        mockMvc.perform(get("/api/groups/1/settlements/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.allCompleted").value(false))
                .andExpect(jsonPath("$.data.status").value("NOT_STARTED"));
    }

    @Test
    @DisplayName("정산 요약(29) 전부 완료 → 200, DONE")
    void getSummary_done() throws Exception {
        given(settlementService.getSummary(1L)).willReturn(SettlementSummaryResponse.of(3, 3));

        mockMvc.perform(get("/api/groups/1/settlements/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.completed").value(3))
                .andExpect(jsonPath("$.data.allCompleted").value(true))
                .andExpect(jsonPath("$.data.status").value("DONE"));
    }

    @Test
    @DisplayName("내 정산 조회(26) → 200, 보낼/받을/완료 구조")
    void getMySettlements_ok() throws Exception {
        MySettlementsResponse mine = new MySettlementsResponse(
                List.of(new MySettlementsResponse.Item(6L, "지은", new BigDecimal("10500"),
                        "PENDING", MySettlementsResponse.Direction.SEND)),
                List.of(),
                List.of());
        given(settlementService.getMySettlements(1L, 7L)).willReturn(mine);

        mockMvc.perform(get("/api/groups/1/settlements/me").header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.toSend[0].counterpartName").value("지은"))
                .andExpect(jsonPath("$.data.toSend[0].direction").value("SEND"))
                .andExpect(jsonPath("$.data.toReceive").isEmpty());
    }

    @Test
    @DisplayName("상태 변경(27) SEND → 200")
    void changeStatus_ok() throws Exception {
        given(settlementService.changeStatus(1L, 5L, Action.SEND, 10L))
                .willReturn(sampleSettlement("SENT"));

        mockMvc.perform(patch("/api/groups/1/settlements/5/status")
                        .header("X-Participant-Id", "10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"SEND\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SENT"));
    }

    @Test
    @DisplayName("상태 변경(27) 권한 없음 → 403")
    void changeStatus_forbidden() throws Exception {
        given(settlementService.changeStatus(1L, 5L, Action.SEND, 99L))
                .willThrow(new BusinessException(ErrorCode.ACCESS_DENIED));

        mockMvc.perform(patch("/api/groups/1/settlements/5/status")
                        .header("X-Participant-Id", "99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"SEND\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(ErrorCode.ACCESS_DENIED.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("상태 변경(27) 상태 위반 → 409(INVALID_STATE)")
    void changeStatus_conflict() throws Exception {
        given(settlementService.changeStatus(1L, 5L, Action.CONFIRM, 10L))
                .willThrow(new BusinessException(ErrorCode.INVALID_STATE));

        mockMvc.perform(patch("/api/groups/1/settlements/5/status")
                        .header("X-Participant-Id", "10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"CONFIRM\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_STATE.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("상태 변경(27) 없는 정산 id → 404(ENTITY_NOT_FOUND)")
    void changeStatus_notFound() throws Exception {
        given(settlementService.changeStatus(1L, 404L, Action.SEND, 10L))
                .willThrow(new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "정산 내역을 찾을 수 없습니다."));

        mockMvc.perform(patch("/api/groups/1/settlements/404/status")
                        .header("X-Participant-Id", "10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"SEND\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("정산 내역을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("상태 변경(27) action 누락 → 400")
    void changeStatus_missingAction() throws Exception {
        mockMvc.perform(patch("/api/groups/1/settlements/5/status")
                        .header("X-Participant-Id", "10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("action: action은 필수입니다."));
    }
}
