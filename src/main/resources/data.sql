INSERT INTO users (email, password, name, created_at, updated_at)
VALUES ('juyoung@test.com', 'test1234', '주영', NOW(), NOW());
INSERT INTO users (email, password, name, created_at, updated_at)
VALUES ('subin@test.com', 'test1234', '수빈', NOW(), NOW());
INSERT INTO users (email, password, name, created_at, updated_at)
VALUES ('chaebin@test.com', 'test1234', '채빈', NOW(), NOW());

INSERT INTO "groups" (name, description, owner_id, currency, created_at, updated_at)
VALUES ('제주도 여행', '테스트 모임', 1, 'KRW', NOW(), NOW());

INSERT INTO group_participants (group_id, user_id, role, joined_at, is_active)
VALUES (1, 1, 'OWNER', NOW(), true);
INSERT INTO group_participants (group_id, user_id, role, joined_at, is_active)
VALUES (1, 2, 'MEMBER', NOW(), true);
INSERT INTO group_participants (group_id, user_id, role, joined_at, is_active)
VALUES (1, 3, 'MEMBER', NOW(), true);