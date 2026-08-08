package com.splitopt.backend.expense.domain;

import com.splitopt.backend.global.entity.BaseEntity;
import com.splitopt.backend.group.domain.Group;
import com.splitopt.backend.group.domain.GroupParticipant;
import com.splitopt.backend.schedule.domain.Schedule;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "expenses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Expense extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payer_id", nullable = false)
    private GroupParticipant payer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id")
    private Schedule schedule;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExpenseCategory category;

    @Column(columnDefinition = "TEXT")
    private String memo;

    @Column(name = "spent_at", nullable = false)
    private LocalDateTime spentAt;

    @Builder
    public Expense(Group group, GroupParticipant payer, Schedule schedule, String title,
                   BigDecimal amount, ExpenseCategory category,
                   String memo, LocalDateTime spentAt) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        this.group = group;
        this.payer = payer;
        this.schedule = schedule;
        this.title = title;
        this.amount = amount;
        this.category = category;
        this.memo = memo;
        this.spentAt = spentAt;
    }

    public void update(String title, BigDecimal amount, ExpenseCategory category,
                       String memo, LocalDateTime spentAt) {
        this.title = title;
        this.amount = amount;
        this.category = category;
        this.memo = memo;
        this.spentAt = spentAt;
    }

    public boolean isPayer(Long participantId) {
        return this.payer.getId().equals(participantId);
    }

    /** 일정 삭제 시 연결만 해제 (지출 데이터 자체는 유지). 36번 API에서 사용. */
    public void clearSchedule() {
        this.schedule = null;
    }

    /** 일정을 새로 연결하거나 다른 일정으로 변경. update(20)에서 scheduleId가 있을 때 사용. */
    public void assignSchedule(Schedule schedule) {
        this.schedule = schedule;
    }
}