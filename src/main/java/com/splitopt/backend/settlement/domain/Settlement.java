package com.splitopt.backend.settlement.domain;

import com.splitopt.backend.group.domain.Group;
import com.splitopt.backend.group.domain.GroupParticipant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 정산 최적화(API 24) 결과 한 건: "누가 누구에게 얼마" (settlements 테이블).
 * <p>from(채무자) → to(채권자)로 {@code amount} 송금. 같은 참여자 테이블을 두 번 참조하는 self-관계.
 * <p>created_at만 있고 updated_at은 없어(BaseEntity 미상속) {@link CreationTimestamp}로 생성 시각만 기록한다.
 */
@Entity
@Table(name = "settlements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_participant_id", nullable = false)
    private GroupParticipant fromParticipant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_participant_id", nullable = false)
    private GroupParticipant toParticipant;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SettlementStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Builder
    public Settlement(Group group, GroupParticipant fromParticipant, GroupParticipant toParticipant, BigDecimal amount) {
        if (group == null || fromParticipant == null || toParticipant == null) {
            throw new IllegalArgumentException("group, from, to must not be null");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        this.group = group;
        this.fromParticipant = fromParticipant;
        this.toParticipant = toParticipant;
        this.amount = amount;
        this.status = SettlementStatus.PENDING;
    }

    /**
     * 송금 완료 (API 27 SEND). {@code PENDING → SENT}, 송금 시각 기록.
     * 이미 송금했거나 완료된 건이면 상태 위반 예외.
     */
    public void markSent() {
        if (this.status != SettlementStatus.PENDING) {
            throw new IllegalStateException("송금 완료할 수 없는 상태입니다: " + this.status);
        }
        this.status = SettlementStatus.SENT;
        this.sentAt = LocalDateTime.now();
    }

    /**
     * 송금 확인 (API 27 CONFIRM). {@code SENT → COMPLETED}, 완료 시각 기록.
     * 아직 송금 전(PENDING)이거나 이미 완료면 상태 위반 예외.
     */
    public void confirm() {
        if (this.status != SettlementStatus.SENT) {
            throw new IllegalStateException("송금 확인할 수 없는 상태입니다(송금 전): " + this.status);
        }
        this.status = SettlementStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * 송금 완료 취소 (API 27 CANCEL, 권장). {@code SENT → PENDING}, 송금 시각 복원(NULL).
     * 받는 사람 확인 전(SENT)에서만 허용 — 완료된 건은 취소 불가.
     */
    public void cancelSend() {
        if (this.status != SettlementStatus.SENT) {
            throw new IllegalStateException("송금 취소할 수 없는 상태입니다: " + this.status);
        }
        this.status = SettlementStatus.PENDING;
        this.sentAt = null;
    }

    public boolean isPending() {
        return this.status == SettlementStatus.PENDING;
    }
}
