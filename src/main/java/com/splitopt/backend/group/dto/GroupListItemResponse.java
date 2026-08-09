package com.splitopt.backend.group.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class GroupListItemResponse {
    private Long groupId;
    private String name;
    private int memberCount;
    private BigDecimal totalExpense;
    private BigDecimal myBalance;
    private String settledStatus; // NOT_STARTED / IN_PROGRESS / DONE
    private LocalDateTime createdAt;
}