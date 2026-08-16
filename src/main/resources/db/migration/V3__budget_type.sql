-- V3: 예산 단위(budget_type) 도입 (API 38·39·40)
-- 저장 금액이 모임 전체 예산인지(TOTAL) 1인당 예산인지(PER_PERSON)를 구분한다.
-- 기존 행은 모두 모임 전체 예산으로 저장돼 있었으므로 TOTAL로 채운다.

ALTER TABLE budgets
    ADD COLUMN budget_type VARCHAR(16) NOT NULL DEFAULT 'TOTAL' AFTER group_id;

ALTER TABLE budgets
    ADD CONSTRAINT ck_budget_type CHECK (budget_type IN ('TOTAL', 'PER_PERSON'));
