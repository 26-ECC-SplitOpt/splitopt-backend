package com.splitopt.backend.settlement.domain;

/**
 * 정산 건 상태 (개정안 E 전이 규칙).
 * <pre>
 * PENDING ──SEND(from)──▶ SENT ──CONFIRM(to)──▶ COMPLETED
 *                           └──CANCEL(from, 확인 전에만)──▶ PENDING
 * </pre>
 * <ul>
 *   <li>{@code PENDING} : 최적화 생성 직후 — 아직 송금 전</li>
 *   <li>{@code SENT} : 보내는 사람이 송금 완료 — 받는 사람의 확인 대기</li>
 *   <li>{@code COMPLETED} : 받는 사람이 확인 — 양측 정산 완료</li>
 * </ul>
 */
public enum SettlementStatus {
    PENDING,
    SENT,
    COMPLETED
}
