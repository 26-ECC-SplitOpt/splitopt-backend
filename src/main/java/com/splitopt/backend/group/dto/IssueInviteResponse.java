package com.splitopt.backend.group.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class IssueInviteResponse {
    private String inviteCode;
    /** 미구현 — 명세 optional */
    private String inviteUrl;
    private LocalDateTime expiresAt;
}
