-- V2: 정산 상태 전이(SENT) 도입 (API 27, 개정안 C-4/E)
-- PENDING → SENT(송금 완료) → COMPLETED(송금 확인), SENT → PENDING(취소)
-- 송금 시각(sent_at) 컬럼 추가 및 status 허용값에 'SENT' 반영.

ALTER TABLE settlements
    ADD COLUMN sent_at DATETIME(6) NULL AFTER status;

ALTER TABLE settlements
    DROP CHECK ck_stl_status;

ALTER TABLE settlements
    ADD CONSTRAINT ck_stl_status CHECK (status IN ('PENDING', 'SENT', 'COMPLETED'));
