package com.splitopt.backend.settlement.domain;

/**
 * 정산 건 상태.
 * <ul>
 *   <li>{@code PENDING} : 아직 송금이 완료되지 않음</li>
 *   <li>{@code COMPLETED} : 송금 완료 처리됨</li>
 * </ul>
 */
public enum SettlementStatus {
    PENDING,
    COMPLETED
}
