package com.splitopt.backend.user.controller;

import com.splitopt.backend.global.response.ApiResponse;
import com.splitopt.backend.user.dto.SignupRequest;
import com.splitopt.backend.user.dto.SignupResponse;
import com.splitopt.backend.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 API.
 * <p>로그인 전에도 호출되어야 하는 가입을 제공한다.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 신규 계정을 생성한다.
     * <p>성공 시 201과 함께 저장된 회원 정보를 반환해,
     * 클라이언트가 가입 완료 화면·다음 단계로 넘어갈 수 있게 한다.
     */
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(
            @Valid @RequestBody SignupRequest request
    ) {
        SignupResponse data = authService.signup(request);
        return ResponseEntity
                .status(HttpStatus.CREATED) // 201
                .body(ApiResponse.success(data));
    }
}