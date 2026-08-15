package com.splitopt.backend.user.controller;

import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.global.exception.GlobalExceptionHandler;
import com.splitopt.backend.global.security.JwtTokenProvider;
import com.splitopt.backend.user.dto.LoginResponse;
import com.splitopt.backend.user.dto.LoginUserResponse;
import com.splitopt.backend.user.dto.MessageResponse;
import com.splitopt.backend.user.dto.SignupResponse;
import com.splitopt.backend.user.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.splitopt.backend.global.security.UserPrincipal;
import com.splitopt.backend.user.dto.MeResponse;
import com.splitopt.backend.user.dto.UpdateMeRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@WebMvcTest(controllers = AuthController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("회원가입 성공 시 201")
    void signup_success() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 1, 12, 0);
        given(authService.signup(any())).willReturn(
                SignupResponse.builder()
                        .userId(1L)
                        .email("user@example.com")
                        .name("홍길동")
                        .createdAt(createdAt)
                        .build()
        );

        String body = """
                {"email":"user@example.com","password":"password1234","name":"홍길동"}
                """;

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.email").value("user@example.com"))
                .andExpect(jsonPath("$.data.name").value("홍길동"))
                .andExpect(jsonPath("$.data.createdAt").exists());
    }

    @Test
    @DisplayName("이메일 중복 시 409")
    void signup_duplicate() throws Exception {
        given(authService.signup(any()))
                .willThrow(new BusinessException(ErrorCode.DUPLICATE_EMAIL));

        String body = """
                {"email":"user@example.com","password":"password1234","name":"홍길동"}
                """;

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("입력값을 확인해주세요."))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].code").value("EMAIL_DUPLICATED"))
                .andExpect(jsonPath("$.errors[0].message").value(ErrorCode.DUPLICATE_EMAIL.getMessage()));
    }

    @Test
    @DisplayName("이메일에 도메인 점이 없으면 400")
    void signup_emailInvalid_noDot() throws Exception {
        String body = """
            {"email":"eeee@gggg","password":"password1234","name":"홍길동"}
            """;

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("입력값을 확인해주세요."))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].code").value("EMAIL_INVALID"));
    }

    @Test
    @DisplayName("비밀번호가 짧으면 400")
    void signup_validation() throws Exception {
        String body = """
                {"email":"user@example.com","password":"short","name":"홍길동"}
                """;

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("입력값을 확인해주세요."))
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andExpect(jsonPath("$.errors[0].code").value("PASSWORD_TOO_SHORT"));
    }

    @Test
    @DisplayName("로그인 성공 시 200")
    void login_success() throws Exception {
        given(authService.login(any())).willReturn(
                LoginResponse.builder()
                        .accessToken("access-token")
                        .refreshToken("refresh-token")
                        .tokenType("Bearer")
                        .expiresIn(3600)
                        .user(LoginUserResponse.builder()
                                .userId(1L)
                                .email("user@example.com")
                                .name("홍길동")
                                .build())
                        .build()
        );

        String body = """
            {"email":"user@example.com","password":"password1234"}
            """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(3600))
                .andExpect(jsonPath("$.data.user.userId").value(1))
                .andExpect(jsonPath("$.data.user.email").value("user@example.com"))
                .andExpect(jsonPath("$.data.user.name").value("홍길동"));
    }

    @Test
    @DisplayName("로그인 실패 시 401")
    void login_failed() throws Exception {
        given(authService.login(any()))
                .willThrow(new BusinessException(ErrorCode.LOGIN_FAILED));

        String body = """
            {"email":"user@example.com","password":"wrongpassword"}
            """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(ErrorCode.LOGIN_FAILED.getMessage()))
                .andExpect(jsonPath("$.errors[0].code").value("LOGIN_FAILED"))
                .andExpect(jsonPath("$.errors[0].message").value(ErrorCode.LOGIN_FAILED.getMessage()));
    }

    @Test
    @DisplayName("로그아웃 성공 시 200")
    void logout_success() throws Exception {
        given(authService.logout(any()))
                .willReturn(new MessageResponse("로그아웃 되었습니다."));

        String body = """
            {"refreshToken":"refresh-token"}
            """;

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.message").value("로그아웃 되었습니다."));
    }

    @Test
    @DisplayName("내 정보 이름 수정 성공 시 200")
    void updateMe_success() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 1, 12, 0);
        given(authService.updateMe(eq(1L), any(UpdateMeRequest.class))).willReturn(
                MeResponse.builder()
                        .userId(1L)
                        .email("user@example.com")
                        .name("새이름")
                        .createdAt(createdAt)
                        .build()
        );

        Authentication auth = new UsernamePasswordAuthenticationToken(
                new UserPrincipal(1L), null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        try {
            String body = """
                    {"name":"새이름"}
                    """;

            mockMvc.perform(put("/api/auth/me")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.userId").value(1))
                    .andExpect(jsonPath("$.data.email").value("user@example.com"))
                    .andExpect(jsonPath("$.data.name").value("새이름"))
                    .andExpect(jsonPath("$.data.createdAt").exists());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("이름 수정 시 이름이 비어 있으면 400")
    void updateMe_blankName() throws Exception {
        String body = """
                {"name":""}
                """;

        mockMvc.perform(put("/api/auth/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("입력값을 확인해주세요."))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_INPUT"));
    }
}
