package com.splitopt.backend.group.domain;

import com.splitopt.backend.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "group_participants",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_group_user",
                        columnNames = {"group_id", "user_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    @Column(name = "display_name", length = 50)
    private String displayName;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    public enum Role {
        OWNER, MEMBER
    }

    @Builder
    public GroupParticipant(Group group, User user, Role role, String displayName) {
        this.group = group;
        this.user = user;
        this.role = role != null ? role : Role.MEMBER;
        this.displayName = displayName;
        this.joinedAt = LocalDateTime.now();
        this.isActive = true;
    }

    public String getEffectiveDisplayName() {
        return displayName != null ? displayName : user.getName();
    }

    public void updateDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void updateRole(Role role) {
        this.role = role;
    }

    // 참여자 소프트 삭제 (Soft-delete: 모임 탈퇴/강퇴)
    public void deactivate() {
        this.isActive = false;
        this.leftAt = LocalDateTime.now();
    }

    // 재참여 (soft-delete 복구)
    public void reactivate(Role role, String displayName) {
        this.isActive = true;
        this.leftAt = null;
        this.role = role != null ? role : Role.MEMBER;
        this.displayName = displayName;
    }
}