package com.splitopt.backend.statistics.dto;

import java.math.BigDecimal;
import java.util.List;

/** 참여자별 결제·부담 통계 (API 32). */
public record ParticipantStatisticsResponse(
        List<ParticipantItem> participants
) {
    public record ParticipantItem(
            Long participantId,
            String name,
            BigDecimal paidAmount,   // 이 사람이 결제한 총액
            BigDecimal owedAmount,   // 이 사람이 부담해야 할 총액
            BigDecimal balance       // paidAmount - owedAmount (양수면 받을 돈, 음수면 낼 돈)
    ) {}
}