package com.splitopt.backend.group.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AddParticipantResponse {
    /** group_participants.id — 지출·정산 API 연동용 (명세 외 additive) */
    private Long participantId;
    private Long userId;
    private String name;
    private String role;
    private LocalDateTime joinedAt;
}
