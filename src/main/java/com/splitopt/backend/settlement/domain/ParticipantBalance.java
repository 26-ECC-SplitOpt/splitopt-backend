package com.splitopt.backend.settlement.domain;

import java.math.BigDecimal;

/**
 * 참여자 1명의 정산 잔액 (API 23: 개인별 잔액).
 *
 * <p>{@code amount = 실제 결제 금액 − 부담해야 할 금액}
 * <ul>
 *   <li>{@code amount > 0} : 받을 돈 (채권자)</li>
 *   <li>{@code amount < 0} : 보낼 돈 (채무자)</li>
 *   <li>{@code amount == 0} : 정산 불필요</li>
 * </ul>
 *
 * <p>영속 엔티티가 아닌 순수 계산용 값 객체다. {@code participantId}는
 * {@code group_participants.id}를 가리키지만, 이 클래스는 해당 엔티티에 의존하지 않는다.
 */
public record ParticipantBalance(Long participantId, BigDecimal amount) {

    public ParticipantBalance {
        if (participantId == null) {
            throw new IllegalArgumentException("participantId must not be null");
        }
        if (amount == null) {
            throw new IllegalArgumentException("amount must not be null");
        }
    }

    public boolean isCreditor() {
        return amount.signum() > 0;
    }

    public boolean isDebtor() {
        return amount.signum() < 0;
    }
}
