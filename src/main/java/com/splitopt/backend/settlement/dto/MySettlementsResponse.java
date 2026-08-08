package com.splitopt.backend.settlement.dto;

import com.splitopt.backend.settlement.domain.Settlement;
import com.splitopt.backend.settlement.domain.SettlementStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 내 정산 내역 (API 26, 개정안 C-3). 로그인 사용자 기준으로
 * <b>보낼 것 / 받을 것 / 완료된 것</b>으로 분류한다.
 *
 * <p>분류 규칙: {@code COMPLETED}는 방향과 무관하게 {@code completed}로, 그 외에는
 * 내가 보내는 사람(from)이면 {@code toSend}, 받는 사람(to)이면 {@code toReceive}.
 * 각 건의 {@code status}(PENDING/SENT/COMPLETED)와 {@code direction}으로 프론트가
 * 버튼 상태(보낼=송금 버튼 / 받을=확인 버튼)를 판별한다.
 *
 * @param toSend    내가 보내야 하는(미완료) 정산
 * @param toReceive 내가 받아야 하는(미완료) 정산
 * @param completed 완료(COMPLETED)된 정산 (양방향)
 */
public record MySettlementsResponse(
        List<Item> toSend,
        List<Item> toReceive,
        List<Item> completed
) {
    /** 정산 방향 (로그인 사용자 관점). */
    public enum Direction { SEND, RECEIVE }

    /**
     * @param settlementId    정산 id
     * @param counterpartName 상대방 표시 이름
     * @param amount          금액
     * @param status          정산 상태 (PENDING/SENT/COMPLETED)
     * @param direction       내 관점의 방향 (SEND=내가 보냄 / RECEIVE=내가 받음)
     */
    public record Item(
            Long settlementId,
            String counterpartName,
            BigDecimal amount,
            String status,
            Direction direction
    ) {
    }

    /**
     * 내 정산 목록을 세 갈래로 분류해 응답을 만든다.
     *
     * @param settlements 로그인 사용자가 보내거나 받는 정산 목록
     * @param userId      로그인 사용자 id (방향 판별 기준)
     */
    public static MySettlementsResponse from(List<Settlement> settlements, Long userId) {
        List<Item> toSend = new ArrayList<>();
        List<Item> toReceive = new ArrayList<>();
        List<Item> completed = new ArrayList<>();

        for (Settlement s : settlements) {
            boolean iAmSender = s.getFromParticipant().getUser().getId().equals(userId);
            Direction direction = iAmSender ? Direction.SEND : Direction.RECEIVE;
            String counterpartName = (iAmSender ? s.getToParticipant() : s.getFromParticipant())
                    .getEffectiveDisplayName();
            Item item = new Item(s.getId(), counterpartName, s.getAmount(), s.getStatus().name(), direction);

            if (s.getStatus() == SettlementStatus.COMPLETED) {
                completed.add(item);
            } else if (iAmSender) {
                toSend.add(item);
            } else {
                toReceive.add(item);
            }
        }
        return new MySettlementsResponse(toSend, toReceive, completed);
    }
}
