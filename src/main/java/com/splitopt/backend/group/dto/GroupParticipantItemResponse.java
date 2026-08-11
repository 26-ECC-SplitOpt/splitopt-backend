package com.splitopt.backend.group.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GroupParticipantItemResponse {
    /** group_participants.id — 지출·정산 API의 payerId / participantId */
    private Long participantId;
    private Long userId;
    private String name;
    private String role; // OWNER / MEMBER
}