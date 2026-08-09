package com.splitopt.backend.user.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MeResponse {
    private Long userId;
    private String email;
    private String name;
    private LocalDateTime createdAt;
}