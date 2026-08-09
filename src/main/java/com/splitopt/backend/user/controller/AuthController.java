package com.splitopt.backend.user.controller;

import com.splitopt.backend.global.response.ApiResponse;
import com.splitopt.backend.global.security.UserPrincipal;
import com.splitopt.backend.user.dto.*;
import com.splitopt.backend.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request
    ) {
        LoginResponse data = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MeResponse>> me(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        MeResponse data = authService.getMe(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<MeResponse>> updateMe(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateMeRequest request
    ) {
        MeResponse data = authService.updateMe(principal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<MessageResponse>> logout(
            @Valid @RequestBody LogoutRequest request
    ) {
        MessageResponse data = authService.logout(request);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

}