package com.splitopt.backend.settlement.controller;

import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.global.exception.GlobalExceptionHandler;
import com.splitopt.backend.global.security.JwtTokenProvider;
import com.splitopt.backend.global.security.UserPrincipal;
import com.splitopt.backend.group.domain.GroupParticipant;
import com.splitopt.backend.group.service.GroupAccessGuard;
import com.splitopt.backend.settlement.domain.SettlementStatus;
import com.splitopt.backend.settlement.dto.MySettlementsResponse;
import com.splitopt.backend.settlement.dto.SettlementResponse;
import com.splitopt.backend.settlement.dto.SettlementStatusChangeRequest.Action;
import com.splitopt.backend.settlement.dto.SettlementSummaryResponse;
import com.splitopt.backend.settlement.service.SettlementService;
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
 * 정산 컨트롤러 웹 계층 테스트 (API 24·25·26·27·28·29).
 * HTTP 상태 매핑(200/400/401/403/404/409)·principal 바인딩·@Valid·JSON 직렬화를 검증한다.
 *
 * <p>요청자는 인증 principal에서만 오고(과거의 X-User-Id·X-Participant-Id 헤더는 제거),
 * 모임 참여 여부는 {@link GroupAccessGuard}가 검증한다.
 */
@WebMvcTest(controllers = SettlementController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class SettlementControllerTest {

    private static final Long USER_ID = 7L;
    private static final Long PARTICIPANT_ID = 10L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SettlementService settlementService;

    @MockitoBean
    private GroupAccessGuard groupAccessGuard;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /** addFilters=false 에선 SecurityContextHolder에 직접 넣어야 @AuthenticationPrincipal 이 채워진다. */
    private void loginAs(Long userId) {
        var auth = new UsernamePasswordAuthenticationToken(new UserPrincipal(userId), null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /** 로그인 사용자가 이 모임의 참여자로 해석되도록 가드를 세팅한다. */
    private void memberOf(Long groupId) {
        loginAs(USER_ID);
        GroupParticipant me = mock(GroupParticipant.class);
        given(me.getId()).willReturn(PARTICIPANT_ID);
        given(groupAccessGuard.requireActiveParticipant(groupId, USER_ID)).willReturn(me);
    }

    /** 로그인은 했지만 이 모임의 참여자가 아닌 상태 — 가드가 403을 던진다. */
    private void notMemberOf(Long groupId) {
        loginAs(USER_ID);
        BusinessException denied = new BusinessException(ErrorCode.ACCESS_DENIED, "이 모임의 참여자가 아닙니다.");
        willThrow(denied).given(groupAccessGuard).requireMember(eq(groupId), anyLong());
        willThrow(denied).given(groupAccessGuard).requireActiveParticipant(eq(groupId), anyLong());
    }

    private SettlementResponse sampleSettlement(String status) {
        return new SettlementResponse(5L, 10L, "주영", 8L, "수빈",
                new BigDecimal("18000"), status, null, null);
    }

    @Test
    @DisplayName("최적화 실행(24) → 200, 생성된 송금 목록과 안내 메시지")
    void optimize_ok() throws Exception {
        memberOf(1L);
        given(settlementService.optimize(1L)).willReturn(List.of(sampleSettlement("PENDING")));

        mockMvc.perform(post("/api/groups/1/settlements/optimize"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("정산 최적화를 실행했습니다."))
                .andExpect(jsonPath("$.data.settlements[0].fromName").value("주영"))
                .andExpect(jsonPath("$.data.settlements[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.transactionCount").value(1))
                .andExpect(jsonPath("$.data.optimizedAt").exists());
    }

    @Test
    @DisplayName("최적화 실행(24) 정산할 것이 없으면 → 200, 빈 목록")
    void optimize_empty() throws Exception {
        memberOf(1L);
        given(settlementService.optimize(1L)).willReturn(List.of());

        mockMvc.perform(post("/api/groups/1/settlements/optimize"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settlements").isEmpty())
                .andExpect(jsonPath("$.data.transactionCount").value(0));
    }

    @Test
    @DisplayName("최적화 실행(24) 없는 모임 → 404(ENTITY_NOT_FOUND)")
    void optimize_notFound() throws Exception {
        loginAs(USER_ID);
        willThrow(new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "모임을 찾을 수 없습니다."))
                .given(groupAccessGuard).requireMember(eq(404L), anyLong());

        mockMvc.perform(post("/api/groups/404/settlements/optimize"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("모임을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("최적화 실행(24) 비참여자 → 403, 서비스는 호출되지 않음")
    void optimize_forbiddenForNonMember() throws Exception {
        notMemberOf(1L);

        mockMvc.perform(post("/api/groups/1/settlements/optimize"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("이 모임의 참여자가 아닙니다."));

        verify(settlementService, never()).optimize(anyLong());
    }

    @Test
    @DisplayName("정산 결과 전체 조회(25) → 200")
    void getSettlements_ok() throws Exception {
        memberOf(1L);
        given(settlementService.getSettlements(1L)).willReturn(List.of(sampleSettlement("PENDING")));

        mockMvc.perform(get("/api/groups/1/settlements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.settlements[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.pendingCount").value(1));
    }

    @Test
    @DisplayName("정산 결과 조회(25) 완료·미완료가 섞이면 건수가 각각 집계된다")
    void getSettlements_countsByStatus() throws Exception {
        memberOf(1L);
        given(settlementService.getSettlements(1L)).willReturn(List.of(
                sampleSettlement("COMPLETED"), sampleSettlement("SENT"), sampleSettlement("PENDING")));

        mockMvc.perform(get("/api/groups/1/settlements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactionCount").value(3))
                .andExpect(jsonPath("$.data.completedCount").value(1))
                // SENT는 아직 상대 확인 전이라 미완료로 센다
                .andExpect(jsonPath("$.data.pendingCount").value(2));
    }

    @Test
    @DisplayName("정산 결과 조회(25) 비참여자 → 403")
    void getSettlements_forbiddenForNonMember() throws Exception {
        notMemberOf(1L);

        mockMvc.perform(get("/api/groups/1/settlements"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("이 모임의 참여자가 아닙니다."));
    }

    @Test
    @DisplayName("인증 principal 없음 → 401(UNAUTHORIZED)")
    void getSettlements_unauthorizedWithoutPrincipal() throws Exception {
        mockMvc.perform(get("/api/groups/1/settlements"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("지원하지 않는 status 파라미터 → 400")
    void getSettlements_invalidStatus() throws Exception {
        memberOf(1L);

        mockMvc.perform(get("/api/groups/1/settlements").param("status", "pendng"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("지원하지 않는 정산 상태입니다: pendng"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("미정산 조회(28, ?status=pending) → 200, PENDING으로 변환되어 전달")
    void getSettlements_pending() throws Exception {
        memberOf(1L);
        given(settlementService.getByStatus(eq(1L), eq(SettlementStatus.PENDING)))
                .willReturn(List.of(sampleSettlement("PENDING")));

        mockMvc.perform(get("/api/groups/1/settlements").param("status", "pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settlements[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.pendingCount").value(1));
    }

    @Test
    @DisplayName("정산 요약(29) 진행 중 → 200, IN_PROGRESS")
    void getSummary_inProgress() throws Exception {
        memberOf(1L);
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
        memberOf(1L);
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
        memberOf(1L);
        given(settlementService.getSummary(1L)).willReturn(SettlementSummaryResponse.of(3, 3));

        mockMvc.perform(get("/api/groups/1/settlements/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.completed").value(3))
                .andExpect(jsonPath("$.data.allCompleted").value(true))
                .andExpect(jsonPath("$.data.status").value("DONE"));
    }

    @Test
    @DisplayName("내 정산 조회(26) → 200, 로그인 사용자 기준 보낼/받을/완료 구조")
    void getMySettlements_ok() throws Exception {
        memberOf(1L);
        MySettlementsResponse mine = new MySettlementsResponse(
                List.of(new MySettlementsResponse.Item(6L, "지은", new BigDecimal("10500"),
                        "PENDING", MySettlementsResponse.Direction.SEND)),
                List.of(),
                List.of());
        given(settlementService.getMySettlements(1L, USER_ID)).willReturn(mine);

        mockMvc.perform(get("/api/groups/1/settlements/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.toSend[0].counterpartName").value("지은"))
                .andExpect(jsonPath("$.data.toSend[0].direction").value("SEND"))
                .andExpect(jsonPath("$.data.toReceive").isEmpty());
    }

    @Test
    @DisplayName("내 정산 조회(26) 비참여자 → 빈 응답이 아니라 403")
    void getMySettlements_forbiddenForNonMember() throws Exception {
        notMemberOf(1L);

        mockMvc.perform(get("/api/groups/1/settlements/me"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("이 모임의 참여자가 아닙니다."));

        verify(settlementService, never())
                .getMySettlements(anyLong(), anyLong());
    }

    @Test
    @DisplayName("상태 변경(27) SEND → 200, 요청자 참여자 id는 로그인 사용자에서 해석")
    void changeStatus_ok() throws Exception {
        memberOf(1L);
        given(settlementService.changeStatus(1L, 5L, Action.SEND, PARTICIPANT_ID))
                .willReturn(sampleSettlement("SENT"));

        mockMvc.perform(patch("/api/groups/1/settlements/5/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"SEND\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SENT"));
    }

    @Test
    @DisplayName("상태 변경(27) 다른 참여자로 위장 시도 → 헤더가 없으므로 principal 기준으로만 동작")
    void changeStatus_usesPrincipalNotClientSuppliedId() throws Exception {
        memberOf(1L);
        given(settlementService.changeStatus(1L, 5L, Action.SEND, PARTICIPANT_ID))
                .willReturn(sampleSettlement("SENT"));

        // 예전 seam(X-Participant-Id)을 보내도 무시되고, 가드가 해석한 참여자 id가 쓰인다.
        mockMvc.perform(patch("/api/groups/1/settlements/5/status")
                        .header("X-Participant-Id", "99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"SEND\"}"))
                .andExpect(status().isOk());

        verify(settlementService)
                .changeStatus(1L, 5L, Action.SEND, PARTICIPANT_ID);
    }

    @Test
    @DisplayName("상태 변경(27) 권한 없음(from/to 아님) → 403")
    void changeStatus_forbidden() throws Exception {
        memberOf(1L);
        given(settlementService.changeStatus(eq(1L), eq(5L), eq(Action.SEND), anyLong()))
                .willThrow(new BusinessException(ErrorCode.ACCESS_DENIED));

        mockMvc.perform(patch("/api/groups/1/settlements/5/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"SEND\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(ErrorCode.ACCESS_DENIED.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("상태 변경(27) 비참여자 → 403, 서비스는 호출되지 않음")
    void changeStatus_forbiddenForNonMember() throws Exception {
        notMemberOf(1L);

        mockMvc.perform(patch("/api/groups/1/settlements/5/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"SEND\"}"))
                .andExpect(status().isForbidden());

        verify(settlementService, never())
                .changeStatus(anyLong(), anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("상태 변경(27) 상태 위반 → 409(INVALID_STATE)")
    void changeStatus_conflict() throws Exception {
        memberOf(1L);
        given(settlementService.changeStatus(1L, 5L, Action.CONFIRM, PARTICIPANT_ID))
                .willThrow(new BusinessException(ErrorCode.INVALID_STATE));

        mockMvc.perform(patch("/api/groups/1/settlements/5/status")
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
        memberOf(1L);
        given(settlementService.changeStatus(1L, 404L, Action.SEND, PARTICIPANT_ID))
                .willThrow(new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "정산 내역을 찾을 수 없습니다."));

        mockMvc.perform(patch("/api/groups/1/settlements/404/status")
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
        memberOf(1L);

        mockMvc.perform(patch("/api/groups/1/settlements/5/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("입력값을 확인해주세요."));
    }
}
