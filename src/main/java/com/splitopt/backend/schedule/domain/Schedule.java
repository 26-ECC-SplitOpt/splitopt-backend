package com.splitopt.backend.schedule.domain;

import com.splitopt.backend.global.entity.BaseEntity;
import com.splitopt.backend.group.domain.Group;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Schedule extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 200)
    private String location;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    @Column(columnDefinition = "TEXT")
    private String memo;

    @Builder
    public Schedule(Group group, String title, String location,
                    LocalDateTime startAt, LocalDateTime endAt, String memo) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (startAt == null) {
            throw new IllegalArgumentException("startAt must not be null");
        }
        if (endAt != null && endAt.isBefore(startAt)) {
            throw new IllegalArgumentException("endAt must be after startAt");
        }
        this.group = group;
        this.title = title;
        this.location = location;
        this.startAt = startAt;
        this.endAt = endAt;
        this.memo = memo;
    }

    public void update(String title, String location, LocalDateTime startAt,
                       LocalDateTime endAt, String memo) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (startAt == null) {
            throw new IllegalArgumentException("startAt must not be null");
        }
        if (endAt != null && endAt.isBefore(startAt)) {
            throw new IllegalArgumentException("endAt must be after startAt");
        }
        this.title = title;
        this.location = location;
        this.startAt = startAt;
        this.endAt = endAt;
        this.memo = memo;
    }
}