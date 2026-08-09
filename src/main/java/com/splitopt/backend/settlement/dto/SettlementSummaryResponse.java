package com.splitopt.backend.settlement.dto;

/**
 * 전체 정산 완료 여부 요약 (API 29, 개정안 C-5).
 *
 * <p>"완료"는 {@code COMPLETED}(양측 확인) 기준이며, 정산을 한 번도 돌리지 않은 모임
 * ({@code total == 0})은 완료가 아니라 <b>정산 전(NOT_STARTED)</b>으로 구분한다. 모임 목록(6)
 * 배지가 이 {@code status}로 "정산 전 / 진행 중 / 완료"를 표시한다.
 *
 * @param total          정산 건수
 * @param completed      완료된 건수 (COMPLETED)
 * @param pending        미완료 건수
 * @param completionRate 완료율 (0.0 ~ 1.0, 정산 전은 0.0)
 * @param allCompleted   전체 완료 여부 (status == DONE)
 * @param status         진행 상태 (NOT_STARTED / IN_PROGRESS / DONE)
 */
public record SettlementSummaryResponse(
        long total,
        long completed,
        long pending,
        double completionRate,
        boolean allCompleted,
        Status status
) {
    /** 정산 진행 상태 (개정안 C-5). */
    public enum Status {
        /** 정산을 한 번도 실행하지 않음 (total == 0). */
        NOT_STARTED,
        /** 정산 건은 있으나 아직 전부 완료되지 않음. */
        IN_PROGRESS,
        /** 모든 정산이 COMPLETED. */
        DONE
    }

    public static SettlementSummaryResponse of(long total, long completed) {
        long pending = total - completed;
        Status status;
        if (total == 0) {
            status = Status.NOT_STARTED;
        } else if (completed == total) {
            status = Status.DONE;
        } else {
            status = Status.IN_PROGRESS;
        }
        double rate = (total == 0) ? 0.0 : (double) completed / total;
        return new SettlementSummaryResponse(total, completed, pending, rate, status == Status.DONE, status);
    }
}
