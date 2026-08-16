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

    /** 저장 금액이 무엇을 뜻하는지. 총예산 계산 기준이 달라진다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "budget_type", nullable = false, length = 16)
    private BudgetType budgetType;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Builder
    public Budget(Group group, BudgetType budgetType, BigDecimal amount) {
        if (group == null) {
            throw new IllegalArgumentException("group must not be null");
        }
        validateAmount(amount);
        this.group = group;
        this.budgetType = budgetType != null ? budgetType : BudgetType.TOTAL;
        this.amount = amount;
    }

    /** 예산 단위·금액 수정 (API 38). */
    public void update(BudgetType budgetType, BigDecimal amount) {
        validateAmount(amount);
        this.budgetType = budgetType != null ? budgetType : BudgetType.TOTAL;
        this.amount = amount;
    }

    /**
     * 모임 전체 기준 총예산. 사용률·잔여·초과 판정은 모두 이 값을 기준으로 한다.
     *
     * @param participantCount 활성 참여자 수. {@code PER_PERSON}일 때만 쓰인다.
     */
    public BigDecimal totalBudget(long participantCount) {
        return budgetType == BudgetType.PER_PERSON
                ? amount.multiply(BigDecimal.valueOf(participantCount))
                : amount;
    }

    /** 1인당 예산. {@code TOTAL}로 잡았다면 1인당 금액이라는 개념이 없어 null이다. */
    public BigDecimal budgetPerPerson() {
        return budgetType == BudgetType.PER_PERSON ? amount : null;
    }

    /**
     * 예산 금액 검증 (0 이상 · DECIMAL(12,2) 범위).
     *
     * <p>엔티티를 거치지 않는 원자적 upsert 경로({@code BudgetRepository#upsertAmount})에서도
     * 같은 규칙을 적용하기 위해 공개한다. 검증 규칙의 단일 출처는 이 메서드다.
     */
    public static void validateAmount(BigDecimal amount) {
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
