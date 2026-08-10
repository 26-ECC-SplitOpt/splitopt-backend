package com.splitopt.backend.group.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class JoinGroupResponse {
    private Long groupId;
    private Long participantId;
    private String name;
    private String role;
    private LocalDateTime joinedAt;
}
