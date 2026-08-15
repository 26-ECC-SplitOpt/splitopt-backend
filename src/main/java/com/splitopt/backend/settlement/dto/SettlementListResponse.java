package com.splitopt.backend.settlement.dto;

import com.splitopt.backend.settlement.domain.SettlementStatus;

import java.util.List;

/**
 * 정산 목록 응답 (API 25 전체 조회 · 28 미정산 조회).
 *
 * <p>목록을 배열로 바로 내리지 않고 객체로 감싸는 이유는, 화면이 목록과 함께 건수 요약을
 * 쓰기 때문이다. 명세는 25와 28의 요약 필드를 다르게 적어 뒀지만(28은 미정산 건수만),
 * 두 응답은 같은 엔드포인트가 {@code ?status=}로 갈라지는 것뿐이라 여기서는 하나로 합친다.
 * 28이 요구하는 {@code pendingCount}가 이 응답에 포함되므로 명세와 어긋나지 않는다.
 *
 * <p>건수는 조회된 목록에서 세므로 별도 질의가 없다. {@code ?status=}로 걸러 조회한 경우
 * 그 범위 안에서의 건수라는 점에 주의한다 — 예를 들어 미정산만 조회하면
 * {@code completedCount}는 0이다.
 *
 * @param transactionCount 목록에 담긴 송금 건수
 * @param completedCount   그중 완료(COMPLETED) 건수
 * @param pendingCount      그중 미완료(PENDING·SENT) 건수
 */
public record SettlementListResponse(
        List<SettlementResponse> settlements,
        int transactionCount,
        int completedCount,
        int pendingCount
) {
    public static SettlementListResponse of(List<SettlementResponse> settlements) {
        int completed = (int) settlements.stream()
                .filter(s -> SettlementStatus.COMPLETED.name().equals(s.status()))
                .count();
        return new SettlementListResponse(
                settlements, settlements.size(), completed, settlements.size() - completed);
    }
}
