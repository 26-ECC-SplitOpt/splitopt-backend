package com.splitopt.backend.settlement.dto;

import java.math.BigDecimal;

/**
 * 참여자 1명의 잔액 응답 (API 23: 개인별 잔액 조회 — 결제·부담·잔액).
 *
 * <p>두 종류의 잔액을 함께 담는다. 의미가 다르므로 화면 용도에 맞게 골라 쓴다.
 * <ul>
 *   <li>{@code balance} = {@code paid − owed} : 지출 원장만 본 <b>총잔액</b>.
 *       참여자별 지출 통계(API 32)의 잔액과 같은 값이다.</li>
 *   <li>{@code netBalance} : 총잔액에서 <b>이미 오간 돈</b>(송금 완료·확인 완료 정산)을 상계한
 *       <b>순잔액</b>. 정산 최적화(API 24)가 입력으로 쓰는 값이며, "지금 더 보내야/받아야 할 돈"이다.</li>
 * </ul>
 *
 * <p>부호 규약은 {@code balance}·{@code netBalance} 모두 동일하다 — 양수는 받을 돈, 음수는 보낼 돈.
 *
 * @param active 활성 참여자 여부. 탈퇴했지만 지출 이력이 남아 잔액이 0이 아닌 참여자도
 *               정산 대상이라 목록에 포함되므로, 화면에서 구분할 수 있도록 함께 내려준다.
 */
public record ParticipantBalanceResponse(
        Long participantId,
        String name,
        boolean active,
        BigDecimal paid,
        BigDecimal owed,
        BigDecimal balance,
        BigDecimal netBalance
) {
}
