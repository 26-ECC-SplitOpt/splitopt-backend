package com.splitopt.backend.user.controller;

import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.global.exception.GlobalExceptionHandler;
import com.splitopt.backend.user.dto.SignupResponse;
import com.splitopt.backend.user.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

@WebMvcTest(controllers = AuthController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

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
                .andExpect(jsonPath("$.message").value(ErrorCode.DUPLICATE_EMAIL.getMessage()));
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
                .andExpect(jsonPath("$.message", containsString("password")));
    }
}