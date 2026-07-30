package com.splitopt.backend.settlement.domain;

import java.math.BigDecimal;

/**
 * 정산 최적화(API 24)의 결과 한 건: "누가 누구에게 얼마를 보낸다".
 *
 * <p>{@code fromParticipantId}(채무자) → {@code toParticipantId}(채권자)로
 * {@code amount}만큼 송금. {@code amount}는 항상 양수다.
 *
 * <p>추후 {@code settlements} 엔티티로 저장되지만, 이 값 객체 자체는 영속성에 의존하지 않는다.
 */
public record SettlementTransfer(Long fromParticipantId, Long toParticipantId, BigDecimal amount) {

    public SettlementTransfer {
        if (fromParticipantId == null || toParticipantId == null) {
            throw new IllegalArgumentException("participant id must not be null");
        }
        if (fromParticipantId.equals(toParticipantId)) {
            throw new IllegalArgumentException("from and to must be different participants");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
