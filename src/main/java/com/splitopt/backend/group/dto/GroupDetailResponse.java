package com.splitopt.backend.group.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class GroupDetailResponse {
    private Long groupId;
    private String name;
    private String description;
    private String currency;
    private Long ownerId;
    private String inviteCode;
    private LocalDateTime inviteExpiresAt;
    private List<GroupParticipantItemResponse> participants;
    private BigDecimal totalExpense;
    private LocalDateTime createdAt;
}