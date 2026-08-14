package com.splitopt.backend.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CORS 설정 테스트.
 *
 * <p>CORS는 <b>브라우저만</b> 강제하는 규칙이라 curl·Postman으로는 문제가 드러나지 않는다.
 * 그래서 설정이 빠져도 서버 쪽에서는 멀쩡해 보이고, 프론트에서만 전부 실패한다. 여기서는 실제
 * 시큐리티 필터 체인을 태워(=필터를 끄지 않고) 응답 헤더를 직접 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CorsConfigTest {

    private static final String LOCAL_ORIGIN = "http://localhost:5173";
    private static final String DEPLOY_ORIGIN = "https://splitopt.netlify.app";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("프론트 개발 서버 프리플라이트 → 토큰 없이도 허용된다")
    void preflightFromLocalDevServer() throws Exception {
        mockMvc.perform(options("/api/groups")
                        .header(HttpHeaders.ORIGIN, LOCAL_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, LOCAL_ORIGIN));
    }

    @Test
    @DisplayName("배포 사이트 프리플라이트 → 허용된다")
    void preflightFromDeployedSite() throws Exception {
        mockMvc.perform(options("/api/groups")
                        .header(HttpHeaders.ORIGIN, DEPLOY_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, DEPLOY_ORIGIN));
    }

    @Test
    @DisplayName("PATCH 프리플라이트 → 허용된다 (정산 상태 변경 27이 이 메서드를 쓴다)")
    void preflightAllowsPatch() throws Exception {
        mockMvc.perform(options("/api/groups/1/settlements/5/status")
                        .header(HttpHeaders.ORIGIN, DEPLOY_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, DEPLOY_ORIGIN));
    }

    @Test
    @DisplayName("Authorization 헤더 프리플라이트 → 허용된다 (빠지면 로그인 이후 전부 실패한다)")
    void preflightAllowsAuthorizationHeader() throws Exception {
        mockMvc.perform(options("/api/auth/me")
                        .header(HttpHeaders.ORIGIN, DEPLOY_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().stringValues(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "Authorization"));
    }

    @Test
    @DisplayName("인증 실패(401) 응답에도 CORS 헤더가 붙는다")
    void corsHeaderPresentOnUnauthorizedResponse() throws Exception {
        // 헤더가 없으면 브라우저는 401 대신 CORS 오류만 보여줘, 프론트가 원인을 못 찾는다.
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.ORIGIN, DEPLOY_ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, DEPLOY_ORIGIN));
    }

    @Test
    @DisplayName("허용 목록에 없는 출처는 거부된다")
    void rejectsUnknownOrigin() throws Exception {
        mockMvc.perform(options("/api/groups")
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("허용 출처에는 credentials 허용 헤더가 함께 내려간다")
    void allowsCredentials() throws Exception {
        // 프론트가 axios withCredentials를 켜도 동작하도록 하기 위한 것이다.
        mockMvc.perform(options("/api/groups")
                        .header(HttpHeaders.ORIGIN, DEPLOY_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }
}
