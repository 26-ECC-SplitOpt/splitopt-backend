package com.splitopt.backend.group.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IssueInviteResponse {
    private String inviteCode;
    /** 미구현 — 명세 optional */
    private String inviteUrl;
    private LocalDateTime expiresAt;
}
