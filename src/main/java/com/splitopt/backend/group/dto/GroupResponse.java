package com.splitopt.backend.group.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GroupResponse {
    private Long groupId;
    private String name;
    private String description;
    private String currency;
    private Long ownerId;
    private int memberCount;
    private String inviteCode;
    private LocalDateTime inviteExpiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}