package com.splitopt.backend.expense.dto;

import com.splitopt.backend.expense.domain.Expense;
import com.splitopt.backend.expense.domain.ExpenseShare;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ExpenseResponse(
        Long id,
        String title,
        BigDecimal amount,
        String category,
        String memo,
        LocalDateTime spentAt,
        PayerInfo payer,
        List<ShareInfo> shares
) {
    public record PayerInfo(Long participantId, String name) {}

    public record ShareInfo(Long participantId, String name, BigDecimal amount) {}

    /** Entity → DTO 변환. Service에서 이 메서드를 호출해서 응답을 만든다. */
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
                expense.getSpentAt(),
                new PayerInfo(expense.getPayer().getId(), expense.getPayer().getEffectiveDisplayName()),
                shareInfos
        );
    }
}