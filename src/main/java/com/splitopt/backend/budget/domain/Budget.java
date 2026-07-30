package com.splitopt.backend.budget.domain;

import com.splitopt.backend.global.entity.BaseEntity;
import com.splitopt.backend.group.domain.Group;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 모임 예산 (API 38·39·40, budgets 테이블). 모임당 1개(group_id UNIQUE).
 * 예산 현황·초과 예측은 이 금액과 지출 합계를 비교해 계산(파생)한다.
 */
@Entity
@Table(name = "budgets",
        uniqueConstraints = @UniqueConstraint(name = "uk_budgets_group", columnNames = "group_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Budget extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false, unique = true)
    private Group group;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Builder
    public Budget(Group group, BigDecimal amount) {
        if (group == null) {
            throw new IllegalArgumentException("group must not be null");
        }
        validateAmount(amount);
        this.group = group;
        this.amount = amount;
    }

    /** 예산 금액 수정 (API 38). */
    public void updateAmount(BigDecimal amount) {
        validateAmount(amount);
        this.amount = amount;
    }

    private static void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("amount must be zero or positive");
        }
        // DECIMAL(12,2) 범위를 도메인에서도 강제 — 서비스 직접 호출(MVC 우회) 방어
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("amount scale must be at most 2");
        }
        if (amount.precision() - amount.scale() > 10) {
            throw new IllegalArgumentException("amount integral digits must be at most 10");
        }
    }
}
