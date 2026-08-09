package com.splitopt.backend.user.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginUserResponse {
    private Long userId;
    private String email;
    private String name;
}