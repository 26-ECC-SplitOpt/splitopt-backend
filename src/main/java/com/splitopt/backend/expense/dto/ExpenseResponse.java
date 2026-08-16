package com.splitopt.backend.expense.dto;

import com.splitopt.backend.expense.domain.Expense;
import com.splitopt.backend.expense.domain.ExpenseShare;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ExpenseResponse(
        Long id,
        String title,
        BigDecimal amount,
        String category,
        String memo,
        LocalDate expenseDate,
        PayerInfo payer,
        ScheduleInfo schedule,
        List<ShareInfo> shares
) {
    public record PayerInfo(Long participantId, String name) {}

    /**
     * 연결된 일정. 연결하지 않은 지출은 {@code null}이다.
     *
     * <p>등록·수정 요청은 {@code scheduleId} 하나만 받지만 응답은 이름까지 담는다. 결제자·부담자를
     * id와 이름으로 함께 내려주는 것과 같은 방식이며, 화면이 일정 이름을 보여주려고 일정 목록(34)을
     * 다시 부르지 않아도 된다.
     *
     * <p>수정 화면은 여기 담긴 {@code scheduleId}를 그대로 다시 보내야 연결이 유지된다.
     * 요청에서 이 값을 빼면 서버는 연결 해제로 처리한다.
     */
    public record ScheduleInfo(Long scheduleId, String title) {}

    public record ShareInfo(Long participantId, String name, BigDecimal amount) {}

    /**
     * Entity → DTO 변환. Service에서 이 메서드를 호출해서 응답을 만든다.
     *
     * <p>결제자·일정은 지연 로딩이라 트랜잭션 안에서 호출해야 한다. 목록 조회처럼 여러 건을
     * 변환할 때는 조회 쿼리에서 함께 읽어 와야 건수만큼 쿼리가 늘지 않는다.
     */
    public static ExpenseResponse from(Expense expense, List<ExpenseShare> shares) {
        List<ShareInfo> shareInfos = shares.stream()
                .map(s -> new ShareInfo(
                        s.getParticipant().getId(),
                        s.getParticipant().getEffectiveDisplayName(),
                        s.getShareAmount()))
                .toList();

        return new ExpenseResponse(
                expense.getId(),
                expense.getTitle(),
                expense.getAmount(),
                expense.getCategory().name(),
                expense.getMemo(),
                expense.getSpentAt().toLocalDate(),
                new PayerInfo(expense.getPayer().getId(), expense.getPayer().getEffectiveDisplayName()),
                expense.getSchedule() == null ? null
                        : new ScheduleInfo(expense.getSchedule().getId(), expense.getSchedule().getTitle()),
                shareInfos
        );
    }
}