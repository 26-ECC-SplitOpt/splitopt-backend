package com.splitopt.backend.group.controller;

import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.global.exception.GlobalExceptionHandler;
import com.splitopt.backend.global.security.JwtTokenProvider;
import com.splitopt.backend.global.security.UserPrincipal;
import com.splitopt.backend.group.dto.AddParticipantResponse;
import com.splitopt.backend.group.dto.GroupParticipantItemResponse;
import com.splitopt.backend.group.dto.ParticipantStatusResponse;
import com.splitopt.backend.group.service.ParticipantService;
import com.splitopt.backend.user.dto.MessageResponse;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ParticipantController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class ParticipantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ParticipantService participantService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(Long userId) {
        var auth = new UsernamePasswordAuthenticationToken(
                new UserPrincipal(userId), null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("참여자 추가 → 201")
    void add_ok() throws Exception {
        loginAs(1L);
        AddParticipantResponse res = AddParticipantResponse.builder()
                .participantId(5L)
                .userId(2L)
                .name("김철수")
                .role("MEMBER")
                .joinedAt(LocalDateTime.of(2026, 7, 21, 14, 10))
                .build();
        given(participantService.add(eq(10L), eq(1L), any())).willReturn(res);

        mockMvc.perform(post("/api/groups/10/participants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.participantId").value(5))
                .andExpect(jsonPath("$.data.userId").value(2))
                .andExpect(jsonPath("$.data.name").value("김철수"))
                .andExpect(jsonPath("$.data.role").value("MEMBER"));
    }

    @Test
    @DisplayName("참여자 추가 — userId 없으면 400")
    void add_validation() throws Exception {
        loginAs(1L);

        mockMvc.perform(post("/api/groups/10/participants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("참여자 추가 — OWNER 아님 403")
    void add_denied() throws Exception {
        loginAs(3L);
        given(participantService.add(eq(10L), eq(3L), any()))
                .willThrow(new BusinessException(
                        ErrorCode.ACCESS_DENIED, "모임 개설자만 참여자를 추가할 수 있습니다."));

        mockMvc.perform(post("/api/groups/10/participants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":2}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("참여자 목록 → 200")
    void list_ok() throws Exception {
        loginAs(1L);
        given(participantService.list(10L, 1L)).willReturn(List.of(
                GroupParticipantItemResponse.builder()
                        .participantId(5L)
                        .userId(1L)
                        .name("소유자")
                        .role("OWNER")
                        .build()
        ));

        mockMvc.perform(get("/api/groups/10/participants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].participantId").value(5))
                .andExpect(jsonPath("$.data[0].role").value("OWNER"));
    }

    @Test
    @DisplayName("참여자 삭제 → 200")
    void remove_ok() throws Exception {
        loginAs(1L);
        given(participantService.remove(10L, 1L, 2L))
                .willReturn(new MessageResponse("참여자가 모임에서 제외되었습니다."));

        mockMvc.perform(delete("/api/groups/10/participants/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.message").value("참여자가 모임에서 제외되었습니다."));
    }

    @Test
    @DisplayName("참여자 삭제 — OWNER 아님 403")
    void remove_denied() throws Exception {
        loginAs(2L);
        given(participantService.remove(10L, 2L, 3L))
                .willThrow(new BusinessException(
                        ErrorCode.ACCESS_DENIED, "모임 개설자만 참여자를 삭제할 수 있습니다."));

        mockMvc.perform(delete("/api/groups/10/participants/3"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("참여자 삭제 — 미정산 채무 있으면 409")
    void remove_unsettledBalance() throws Exception {
        loginAs(1L);
        given(participantService.remove(10L, 1L, 2L))
                .willThrow(new BusinessException(
                        ErrorCode.INVALID_STATE, "미정산 채무가 남아 있어 삭제할 수 없습니다."));

        mockMvc.perform(delete("/api/groups/10/participants/2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("참여자 삭제 — OWNER 본인 삭제는 409")
    void remove_ownerSelf() throws Exception {
        loginAs(1L);
        given(participantService.remove(10L, 1L, 1L))
                .willThrow(new BusinessException(
                        ErrorCode.INVALID_STATE, "모임 개설자는 삭제할 수 없습니다."));

        mockMvc.perform(delete("/api/groups/10/participants/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("참여자 정산 현황 → 200")
    void status_ok() throws Exception {
        loginAs(1L);
        given(participantService.status(10L, 1L, 4L)).willReturn(
                ParticipantStatusResponse.builder()
                        .userId(4L)
                        .name("박민수")
                        .paidAmount(BigDecimal.ZERO)
                        .burdenAmount(new BigDecimal("80000"))
                        .balance(new BigDecimal("-80000"))
                        .toSend(List.of())
                        .toReceive(List.of())
                        .settlementStatus("NOT_STARTED")
                        .build()
        );

        mockMvc.perform(get("/api/groups/10/participants/4/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(4))
                .andExpect(jsonPath("$.data.paidAmount").value(0))
                .andExpect(jsonPath("$.data.burdenAmount").value(80000))
                .andExpect(jsonPath("$.data.balance").value(-80000))
                .andExpect(jsonPath("$.data.toSend").isArray())
                .andExpect(jsonPath("$.data.settlementStatus").value("NOT_STARTED"));
    }
}
