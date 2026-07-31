package com.splitopt.backend.settlement.dto;

import com.splitopt.backend.settlement.domain.Settlement;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 정산 1건 응답 (API 25·26·27). 참여자 표시 이름 포함.
 * 지연 로딩(participant.user)에 접근하므로 반드시 트랜잭션 내부에서 생성한다.
 */
public record SettlementResponse(
        Long id,
        Long fromParticipantId,
        String fromName,
        Long toParticipantId,
        String toName,
        BigDecimal amount,
        String status,
        LocalDateTime completedAt
) {
    public static SettlementResponse from(Settlement s) {
        return new SettlementResponse(
                s.getId(),
                s.getFromParticipant().getId(),
                s.getFromParticipant().getEffectiveDisplayName(),
                s.getToParticipant().getId(),
                s.getToParticipant().getEffectiveDisplayName(),
                s.getAmount(),
                s.getStatus().name(),
                s.getCompletedAt()
        );
    }
}
