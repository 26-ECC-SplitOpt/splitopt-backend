package com.splitopt.backend.settlement.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 개인별 잔액 목록 응답 (API 23).
 *
 * <p>총 지출은 참여자별 결제액의 합이다. 모든 지출에는 결제자가 정확히 한 명이므로 이 합은
 * 지출 원장의 총액과 같고, 따로 질의하지 않아도 목록에서 구할 수 있다.
 *
 * <p>필드 이름을 {@code totalAmount}로 둔 이유는 통계(30·31)·일정별 지출(37)·모임 목록/상세가
 * 이미 같은 이름을 쓰기 때문이다. 명세 세부 페이지는 {@code totalExpense}로 적혀 있으나, 그
 * 페이지는 정산 참여자를 {@code userId}로 표기하는 초안 세대라(3주차 회의록에서
 * {@code participantId}로 확정) 이름 기준으로 삼지 않았다.
 */
public record BalanceListResponse(
        List<ParticipantBalanceResponse> balances,
        BigDecimal totalAmount
) {
    public static BalanceListResponse of(List<ParticipantBalanceResponse> balances) {
        BigDecimal total = balances.stream()
                .map(ParticipantBalanceResponse::paidAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new BalanceListResponse(balances, total);
    }
}
