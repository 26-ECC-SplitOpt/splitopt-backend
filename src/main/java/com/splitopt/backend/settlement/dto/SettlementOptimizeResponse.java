package com.splitopt.backend.settlement.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 정산 최적화 실행 응답 (API 24).
 *
 * <p>조회(25)와 달리 "방금 만들어진 송금 목록"이라 완료/미완료 건수 대신 실행 시각을 담는다.
 * 최적화 직후에는 모든 건이 PENDING이므로 완료 건수가 의미가 없다.
 *
 * @param transactionCount 이번 실행으로 만들어진 송금 건수
 * @param optimizedAt      최적화를 실행한 시각
 */
public record SettlementOptimizeResponse(
        List<SettlementResponse> settlements,
        int transactionCount,
        LocalDateTime optimizedAt
) {
    public static SettlementOptimizeResponse of(List<SettlementResponse> settlements) {
        return new SettlementOptimizeResponse(settlements, settlements.size(), LocalDateTime.now());
    }
}
