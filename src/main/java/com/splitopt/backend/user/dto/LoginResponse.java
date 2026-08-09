package com.splitopt.backend.user.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;   // Bearer
    private long expiresIn;     // 초
    private LoginUserResponse user;
}