-- =====================================================================
-- SplitOpt 초기 스키마 (Flyway V1)
-- 대상: MySQL 8 (배포: Railway MySQL)
-- 범위: API index 1-40 (추가기능 3·5·6 = 41-49 제외)
-- 근거: 2주차 회의 DB 문서 + 엔티티(PR #2 이후)
--
-- 주의:
--  - `groups`는 MySQL 8 예약어(GROUPS)라 백틱(`)으로 감쌈.
--  - 타임스탬프는 JPA LocalDateTime(마이크로초)에 맞춰 DATETIME(6) 사용.
--  - 운영은 spring.jpa.hibernate.ddl-auto=none 으로 두고 스키마는 Flyway가 전담.
--  - FK 생성 순서: users → groups → group_participants → schedules
--                  → expenses → expense_shares → settlements → budgets
-- =====================================================================

-- 1) 사용자 -----------------------------------------------------------
CREATE TABLE users (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    email             VARCHAR(255) NOT NULL,
    password          VARCHAR(255) NOT NULL,
    name              VARCHAR(50)  NOT NULL,
    profile_image_url VARCHAR(500) NULL,
    created_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2) 모임 -------------------------------------------------------------
CREATE TABLE `groups` (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    name              VARCHAR(100) NOT NULL,
    description       VARCHAR(500) NULL,
    owner_id          BIGINT       NOT NULL,
    invite_code       VARCHAR(32)  NULL,
    invite_expires_at DATETIME(6)  NULL,               -- 초대 코드 만료(엔티티 PR #2)
    currency          VARCHAR(3)   NOT NULL DEFAULT 'KRW',
    created_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_groups_invite_code UNIQUE (invite_code),
    CONSTRAINT fk_groups_owner FOREIGN KEY (owner_id)
        REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3) 참여자(모임 멤버십) ----------------------------------------------
CREATE TABLE group_participants (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    group_id     BIGINT      NOT NULL,
    user_id      BIGINT      NOT NULL,
    role         VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
    display_name VARCHAR(50) NULL,
    joined_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    is_active    BOOLEAN     NOT NULL DEFAULT TRUE,     -- soft-delete 플래그
    left_at      DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_group_user UNIQUE (group_id, user_id),
    CONSTRAINT fk_gp_group FOREIGN KEY (group_id)
        REFERENCES `groups` (id) ON DELETE CASCADE,
    CONSTRAINT fk_gp_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_gp_role CHECK (role IN ('OWNER', 'MEMBER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4) 일정(추가기능 1) -------------------------------------------------
CREATE TABLE schedules (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    group_id   BIGINT       NOT NULL,
    title      VARCHAR(100) NOT NULL,
    location   VARCHAR(200) NULL,
    start_at   DATETIME(6)  NOT NULL,
    end_at     DATETIME(6)  NULL,
    memo       TEXT         NULL,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_sch_group FOREIGN KEY (group_id)
        REFERENCES `groups` (id) ON DELETE CASCADE,
    CONSTRAINT ck_sch_period CHECK (end_at IS NULL OR end_at >= start_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5) 지출 -------------------------------------------------------------
CREATE TABLE expenses (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    group_id    BIGINT        NOT NULL,
    payer_id    BIGINT        NOT NULL,                -- group_participants.id (결제자)
    schedule_id BIGINT        NULL,                    -- 연결된 일정(선택)
    title       VARCHAR(100)  NOT NULL,
    amount      DECIMAL(12,2) NOT NULL,
    category    VARCHAR(30)   NOT NULL,
    memo        TEXT          NULL,
    spent_at    DATETIME(6)   NOT NULL,
    created_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_exp_group FOREIGN KEY (group_id)
        REFERENCES `groups` (id) ON DELETE CASCADE,
    CONSTRAINT fk_exp_payer FOREIGN KEY (payer_id)
        REFERENCES group_participants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_exp_schedule FOREIGN KEY (schedule_id)
        REFERENCES schedules (id) ON DELETE SET NULL,
    CONSTRAINT ck_exp_amount CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6) 지출 부담 분배 ---------------------------------------------------
CREATE TABLE expense_shares (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    expense_id     BIGINT        NOT NULL,
    participant_id BIGINT        NOT NULL,             -- group_participants.id (부담자)
    share_amount   DECIMAL(12,2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_share_expense_participant UNIQUE (expense_id, participant_id),
    CONSTRAINT fk_share_expense FOREIGN KEY (expense_id)
        REFERENCES expenses (id) ON DELETE CASCADE,
    CONSTRAINT fk_share_participant FOREIGN KEY (participant_id)
        REFERENCES group_participants (id) ON DELETE RESTRICT,
    CONSTRAINT ck_share_amount CHECK (share_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7) 정산(송금 관계) --------------------------------------------------
CREATE TABLE settlements (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    group_id            BIGINT        NOT NULL,
    from_participant_id BIGINT        NOT NULL,        -- 보내는 사람
    to_participant_id   BIGINT        NOT NULL,        -- 받는 사람
    amount              DECIMAL(12,2) NOT NULL,
    status              VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    created_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at        DATETIME(6)   NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_stl_group FOREIGN KEY (group_id)
        REFERENCES `groups` (id) ON DELETE CASCADE,
    CONSTRAINT fk_stl_from FOREIGN KEY (from_participant_id)
        REFERENCES group_participants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_stl_to FOREIGN KEY (to_participant_id)
        REFERENCES group_participants (id) ON DELETE RESTRICT,
    CONSTRAINT ck_stl_amount CHECK (amount > 0),
    CONSTRAINT ck_stl_status CHECK (status IN ('PENDING', 'COMPLETED')),
    CONSTRAINT ck_stl_distinct CHECK (from_participant_id <> to_participant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8) 예산(추가기능 1) -------------------------------------------------
CREATE TABLE budgets (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    group_id   BIGINT        NOT NULL,                 -- 모임당 1개(UNIQUE)
    amount     DECIMAL(12,2) NOT NULL,
    created_at DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_budgets_group UNIQUE (group_id),
    CONSTRAINT fk_budget_group FOREIGN KEY (group_id)
        REFERENCES `groups` (id) ON DELETE CASCADE,
    CONSTRAINT ck_budget_amount CHECK (amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 조회 성능용 보조 인덱스 ---------------------------------------------
-- (단일 FK 컬럼은 InnoDB가 자동 인덱싱하므로 복합/필터용만 명시)
CREATE INDEX idx_expenses_group_category ON expenses (group_id, category);   -- 카테고리별 통계(31)
CREATE INDEX idx_settlements_group_status ON settlements (group_id, status);  -- 미정산 필터(28)·요약(29)
