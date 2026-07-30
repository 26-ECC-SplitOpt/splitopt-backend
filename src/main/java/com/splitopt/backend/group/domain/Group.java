package com.splitopt.backend.group.domain;

import com.splitopt.backend.user.domain.User;
import com.splitopt.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "groups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Group extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "invite_code", unique = true, length = 32)
    private String inviteCode;

    //erd엔 없지만 api 명세서에 있어서 추가
    @Column(name = "invite_expires_at")
    private LocalDateTime inviteExpiresAt;

    @Column(nullable = false, length = 3)
    private String currency;

    @Builder
    public Group(String name, String description, User owner, String inviteCode) {
        this.name = name;
        this.description = description;
        this.owner = owner;
        this.inviteCode = inviteCode;
        this.currency = (currency != null && !currency.isBlank()) ? currency : "KRW";
    }

    // 모임 정보 수정용 메서드
    public void updateGroupInfo(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void issueInvite(String inviteCode, LocalDateTime expiresAt) {
        this.inviteCode = inviteCode;
        this.inviteExpiresAt = expiresAt;
    }

    //코드 만료 여부 확인
    public boolean isInviteValid() {
        return inviteCode != null
                && inviteExpiresAt != null
                && inviteExpiresAt.isAfter(LocalDateTime.now());
    }

}