package com.splitopt.backend.statistics.controller;

import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.global.exception.GlobalExceptionHandler;
import com.splitopt.backend.global.security.JwtTokenProvider;
import com.splitopt.backend.global.security.UserPrincipal;
import com.splitopt.backend.group.service.GroupAccessGuard;
import com.splitopt.backend.statistics.dto.GroupStatisticsResponse;
import com.splitopt.backend.statistics.service.StatisticsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 지출 통계 API(30~32)의 인가.
 *
 * <p>로그인만 하면 {@code groupId}를 아는 누구나 남의 모임 통계를 볼 수 있었다. 저장을 하지
 * 않는 조회라 무해해 보이지만, 참여자별 통계(32)는 <b>참여자 이름과 각자의 결제·부담 금액</b>을
 * 그대로 내려준다 — 잔액(23)이 비참여자에게 닫혀 있는 것과 같은 데이터가 옆문으로 열려 있었다.
 *
 * <p>인가는 컨트롤러의 {@link GroupAccessGuard} 호출 한 곳에 있으므로, 세 엔드포인트 각각에
 * 대해 비참여자가 막히고 서비스까지 닿지 않는다는 것을 남긴다.
 */
@WebMvcTest(controllers = StatisticsController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class StatisticsAccessControlTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StatisticsService statisticsService;

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
    @DisplayName("모임 전체 통계(30) 비참여자 → 403, 서비스는 호출되지 않음")
    void groupStatistics_forbiddenForNonMember() throws Exception {
        mockMvc.perform(get("/api/groups/1/statistics"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("이 모임의 참여자가 아닙니다."));

        verifyNoInteractions(statisticsService);
    }

    @Test
    @DisplayName("카테고리별 통계(31) 비참여자 → 403")
    void categoryStatistics_forbiddenForNonMember() throws Exception {
        mockMvc.perform(get("/api/groups/1/statistics/categories"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(statisticsService);
    }

    @Test
    @DisplayName("참여자별 통계(32) 비참여자 → 403, 참여자 이름·금액이 새어 나가지 않는다")
    void participantStatistics_forbiddenForNonMember() throws Exception {
        mockMvc.perform(get("/api/groups/1/statistics/participants"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data").doesNotExist());

        verifyNoInteractions(statisticsService);
    }

    @Test
    @DisplayName("없는 모임의 통계(30) → 404")
    void groupStatistics_notFound() throws Exception {
        willThrow(new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "모임을 찾을 수 없습니다."))
                .given(groupAccessGuard).requireMember(eq(404L), anyLong());

        mockMvc.perform(get("/api/groups/404/statistics"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("모임을 찾을 수 없습니다."));

        verifyNoInteractions(statisticsService);
    }

    @Test
    @DisplayName("인증 principal 없음 → 401")
    void unauthorizedWithoutPrincipal() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/groups/1/statistics"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(statisticsService);
    }

    @Test
    @DisplayName("가드가 통과시키면(참여자) 서비스가 호출된다")
    void memberPassesThrough() throws Exception {
        // 모임 2는 @BeforeEach가 막아 둔 모임(1)이 아니라 가드가 그대로 통과시킨다.
        given(statisticsService.getGroupStatistics(2L))
                .willReturn(new GroupStatisticsResponse(new BigDecimal("71000"), 2, new BigDecimal("23666.67")));

        mockMvc.perform(get("/api/groups/2/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(71000));

        verify(statisticsService).getGroupStatistics(2L);
    }
}
