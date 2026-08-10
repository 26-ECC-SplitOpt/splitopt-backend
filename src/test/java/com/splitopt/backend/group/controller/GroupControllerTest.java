package com.splitopt.backend.group.controller;

import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.global.exception.GlobalExceptionHandler;
import com.splitopt.backend.global.security.JwtTokenProvider;
import com.splitopt.backend.global.security.UserPrincipal;
import com.splitopt.backend.group.dto.*;
import com.splitopt.backend.group.service.GroupService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GroupController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class GroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GroupService groupService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /** addFilters=false 에선 SecurityContextHolder에 직접 넣어야 @AuthenticationPrincipal 이 채워진다. */
    private void loginAs(Long userId) {
        var auth = new UsernamePasswordAuthenticationToken(
                new UserPrincipal(userId), null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("모임 생성 → 201")
    void create_ok() throws Exception {
        loginAs(1L);
        GroupResponse res = GroupResponse.builder()
                .groupId(10L)
                .name("강릉 당일치기")
                .description("메모")
                .currency("KRW")
                .ownerId(1L)
                .memberCount(1)
                .inviteCode("ABCD1234")
                .inviteExpiresAt(LocalDateTime.of(2026, 7, 24, 14, 5))
                .createdAt(LocalDateTime.of(2026, 7, 21, 14, 5))
                .build();
        given(groupService.create(eq(1L), any())).willReturn(res);

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"강릉 당일치기\",\"description\":\"메모\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.groupId").value(10))
                .andExpect(jsonPath("$.data.currency").value("KRW"))
                .andExpect(jsonPath("$.data.inviteCode").value("ABCD1234"));
    }

    @Test
    @DisplayName("모임 생성 이름 누락 → 400")
    void create_blankName() throws Exception {
        loginAs(1L);
        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"description\":\"메모\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("입력값을 확인해주세요."));
    }

    @Test
    @DisplayName("내 모임 목록 → 200")
    void list_ok() throws Exception {
        loginAs(1L);
        GroupListResponse res = GroupListResponse.builder()
                .groups(List.of(GroupListItemResponse.builder()
                        .groupId(10L)
                        .name("강릉 당일치기")
                        .memberCount(1)
                        .totalExpense(BigDecimal.ZERO)
                        .myBalance(BigDecimal.ZERO)
                        .settledStatus("NOT_STARTED")
                        .createdAt(LocalDateTime.of(2026, 7, 21, 14, 5))
                        .build()))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .build();
        given(groupService.getMyGroups(1L, 0, 20)).willReturn(res);

        mockMvc.perform(get("/api/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.groups[0].settledStatus").value("NOT_STARTED"));
    }

    @Test
    @DisplayName("모임 상세 → 200")
    void detail_ok() throws Exception {
        loginAs(1L);
        GroupDetailResponse res = GroupDetailResponse.builder()
                .groupId(10L)
                .name("강릉 당일치기")
                .description("메모")
                .currency("KRW")
                .ownerId(1L)
                .inviteCode("ABCD1234")
                .inviteExpiresAt(LocalDateTime.of(2026, 7, 24, 14, 5))
                .participants(List.of(GroupParticipantItemResponse.builder()
                        .participantId(7L).userId(1L).name("지은").role("OWNER").build()))
                .totalExpense(BigDecimal.ZERO)
                .createdAt(LocalDateTime.of(2026, 7, 21, 14, 5))
                .build();
        given(groupService.getDetail(10L, 1L)).willReturn(res);

        mockMvc.perform(get("/api/groups/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inviteCode").value("ABCD1234"))
                .andExpect(jsonPath("$.data.participants[0].participantId").value(7))
                .andExpect(jsonPath("$.data.participants[0].userId").value(1))
                .andExpect(jsonPath("$.data.participants[0].role").value("OWNER"))
                .andExpect(jsonPath("$.data.totalExpense").value(0));
    }

    @Test
    @DisplayName("모임 상세 없는 groupId → 404")
    void detail_notFound() throws Exception {
        loginAs(1L);
        given(groupService.getDetail(999999L, 1L))
                .willThrow(new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "모임을 찾을 수 없습니다."));

        mockMvc.perform(get("/api/groups/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("모임 수정 OWNER 아님 → 403")
    void update_forbidden() throws Exception {
        loginAs(2L);
        given(groupService.update(eq(10L), eq(2L), any()))
                .willThrow(new BusinessException(ErrorCode.ACCESS_DENIED, "모임 개설자만 수정할 수 있습니다."));

        mockMvc.perform(put("/api/groups/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"불가\",\"description\":\"x\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("모임 수정 없는 groupId → 404")
    void update_notFound() throws Exception {
        loginAs(1L);
        given(groupService.update(eq(999999L), eq(1L), any()))
                .willThrow(new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "모임을 찾을 수 없습니다."));

        mockMvc.perform(put("/api/groups/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"이름\",\"description\":\"설명\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("모임 삭제 → 200")
    void delete_ok() throws Exception {
        loginAs(1L);
        given(groupService.delete(10L, 1L))
                .willReturn(new MessageResponse("모임이 삭제되었습니다."));

        mockMvc.perform(delete("/api/groups/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("모임이 삭제되었습니다."));
    }

    @Test
    @DisplayName("모임 삭제 없는 groupId → 404")
    void delete_notFound() throws Exception {
        loginAs(1L);
        given(groupService.delete(999999L, 1L))
                .willThrow(new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "모임을 찾을 수 없습니다."));

        mockMvc.perform(delete("/api/groups/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("초대코드 재발급 → 201")
    void reissueInvite_ok() throws Exception {
        loginAs(1L);
        given(groupService.reissueInvite(eq(10L), eq(1L), any()))
                .willReturn(IssueInviteResponse.builder()
                        .inviteCode("NEWCODE1")
                        .expiresAt(LocalDateTime.of(2026, 7, 25, 14, 0))
                        .build());

        mockMvc.perform(post("/api/groups/10/invite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiresInHours\":48}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.inviteCode").value("NEWCODE1"));
    }

    @Test
    @DisplayName("초대코드 참여 → 200")
    void join_ok() throws Exception {
        loginAs(2L);
        given(groupService.joinByInviteCode(eq(2L), any()))
                .willReturn(JoinGroupResponse.builder()
                        .groupId(10L)
                        .name("제주도 여행")
                        .role("MEMBER")
                        .joinedAt(LocalDateTime.of(2026, 7, 21, 14, 20))
                        .build());

        mockMvc.perform(post("/api/groups/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\":\"JEJU2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupId").value(10))
                .andExpect(jsonPath("$.data.role").value("MEMBER"));
    }
}
