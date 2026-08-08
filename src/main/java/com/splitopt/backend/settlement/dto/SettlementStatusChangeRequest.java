package com.splitopt.backend.settlement.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 정산 상태 변경 요청 (API 27, 개정안 C-4). 경로는 그대로 두고 body의 {@code action}으로 전이한다.
 *
 * <pre>
 * PATCH /api/groups/{groupId}/settlements/{settlementId}/status
 * { "action": "SEND" }   // SEND(송금 완료) | CONFIRM(송금 확인) | CANCEL(송금 완료 취소)
 * </pre>
 */
public record SettlementStatusChangeRequest(
        @NotNull(message = "action은 필수입니다.") Action action
) {
    /** 정산 상태 전이 명령. */
    public enum Action {
        /** 송금 완료: PENDING → SENT (보내는 사람만). */
        SEND,
        /** 송금 확인: SENT → COMPLETED (받는 사람만). */
        CONFIRM,
        /** 송금 완료 취소: SENT → PENDING (보내는 사람만, 확인 전에만). */
        CANCEL
    }
}
