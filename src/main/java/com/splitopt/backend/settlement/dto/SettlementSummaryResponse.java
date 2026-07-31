package com.splitopt.backend.settlement.dto;

/**
 * 전체 정산 완료 여부 요약 (API 29).
 *
 * @param total          정산 건수
 * @param completed      완료된 건수
 * @param pending        미완료 건수
 * @param completionRate 완료율 (0.0 ~ 1.0)
 * @param allCompleted   전체 완료 여부
 */
public record SettlementSummaryResponse(
        long total,
        long completed,
        long pending,
        double completionRate,
        boolean allCompleted
) {
    public static SettlementSummaryResponse of(long total, long completed) {
        long pending = total - completed;
        double rate = (total == 0) ? 1.0 : (double) completed / total;
        return new SettlementSummaryResponse(total, completed, pending, rate, pending == 0);
    }
}
