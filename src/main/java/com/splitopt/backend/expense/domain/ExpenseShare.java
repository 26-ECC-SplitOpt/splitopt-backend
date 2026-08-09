package com.splitopt.backend.expense.domain;

import com.splitopt.backend.group.domain.GroupParticipant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "expense_shares")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExpenseShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_id", nullable = false)
    private Expense expense;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    private GroupParticipant participant;

    @Column(name = "share_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal shareAmount;

    @Builder
    public ExpenseShare(Expense expense, GroupParticipant participant, BigDecimal shareAmount) {
        if (shareAmount == null || shareAmount.signum() < 0) {
            throw new IllegalArgumentException("shareAmount must not be negative");
        }
        this.expense = expense;
        this.participant = participant;
        this.shareAmount = shareAmount;
    }
}