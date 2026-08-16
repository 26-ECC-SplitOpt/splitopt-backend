package com.splitopt.backend.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flyway 마이그레이션(V1)을 실제 MySQL 8에 적용해 검증한다.
 *
 * <p>Testcontainers로 일회용 MySQL 컨테이너를 띄워 CI에서도 자동 실행된다.
 * 도커가 없는 환경(로컬 등)에서는 {@code disabledWithoutDocker}로 자동 스킵된다.
 */
@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("splitopt");

    @Test
    @DisplayName("V1~V3가 MySQL 8에 오류 없이 적용되고, 8개 도메인 테이블이 생성된다")
    void v1AppliesCleanlyOnMySql8() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load();

        MigrateResult result = flyway.migrate();
        assertEquals(3, result.migrationsExecuted, "V1~V3 마이그레이션 3개가 적용되어야 한다");

        // 체크섬·버전 정합성 검증
        assertDoesNotThrow(flyway::validate);

        // 8개 도메인 테이블 생성 확인
        String[] expectedTables = {
                "users", "groups", "group_participants", "expenses",
                "expense_shares", "settlements", "schedules", "budgets"
        };
        try (Connection conn = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            String schema = conn.getCatalog();
            for (String table : expectedTables) {
                try (ResultSet rs = conn.getMetaData().getTables(schema, null, table, null)) {
                    assertTrue(rs.next(), table + " 테이블이 생성되어야 한다");
                }
            }

            // 스키마 계약: FK·CHECK 제약이 실제로 생성됐는지 검증 (파라미터 바인딩)
            try (PreparedStatement fkStmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM information_schema.referential_constraints" +
                            " WHERE constraint_schema = ?")) {
                fkStmt.setString(1, schema);
                try (ResultSet fk = fkStmt.executeQuery()) {
                    fk.next();
                    assertEquals(13, fk.getInt(1), "외래키(FK) 13개가 생성되어야 한다");
                }
            }
            try (PreparedStatement ckStmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM information_schema.table_constraints" +
                            " WHERE table_schema = ? AND constraint_type = 'CHECK'")) {
                ckStmt.setString(1, schema);
                try (ResultSet ck = ckStmt.executeQuery()) {
                    ck.next();
                    assertEquals(9, ck.getInt(1), "CHECK 제약 9개가 생성되어야 한다");
                }
            }

            // 제약이 개수만이 아니라 실제로 '동작'하는지: 잘못된 INSERT가 거부되어야 한다
            try (Statement seed = conn.createStatement()) {
                seed.executeUpdate("INSERT INTO users(email, password, name) VALUES('t@t.com','p','T')");
                seed.executeUpdate("INSERT INTO users(email, password, name) VALUES('u@u.com','p','U')");
                seed.executeUpdate("INSERT INTO `groups`(name, owner_id) VALUES('g', 1)");
                seed.executeUpdate("INSERT INTO group_participants(group_id, user_id, role) VALUES(1, 1, 'OWNER')");
                seed.executeUpdate("INSERT INTO group_participants(group_id, user_id, role) VALUES(1, 2, 'MEMBER')");
            }
            // amount <= 0 은 CHECK(ck_exp_amount)로 거부되어야 함
            assertThrows(SQLException.class, () -> {
                try (Statement bad = conn.createStatement()) {
                    bad.executeUpdate("INSERT INTO expenses(group_id, payer_id, title, amount, category, spent_at)" +
                            " VALUES(1, 1, 'x', -1, 'c', NOW())");
                }
            }, "음수 금액 지출은 CHECK 제약으로 거부되어야 한다");

            // V2: 정산 상태 전이(SENT) — sent_at 컬럼 존재 + status CHECK가 'SENT'를 허용해야 함
            try (ResultSet rs = conn.getMetaData().getColumns(schema, null, "settlements", "sent_at")) {
                assertTrue(rs.next(), "V2로 settlements.sent_at 컬럼이 추가되어야 한다");
            }
            assertDoesNotThrow(() -> {
                try (Statement ok = conn.createStatement()) {
                    ok.executeUpdate("INSERT INTO settlements(group_id, from_participant_id, to_participant_id," +
                            " amount, status, sent_at) VALUES(1, 1, 2, 100, 'SENT', NOW(6))");
                }
            }, "V2 이후 'SENT' 상태 정산은 허용되어야 한다");
            // 정의되지 않은 상태값은 갱신된 CHECK(ck_stl_status)로 거부되어야 함
            assertThrows(SQLException.class, () -> {
                try (Statement bad = conn.createStatement()) {
                    bad.executeUpdate("INSERT INTO settlements(group_id, from_participant_id, to_participant_id," +
                            " amount, status) VALUES(1, 2, 1, 100, 'FOO')");
                }
            }, "정의되지 않은 정산 상태는 CHECK 제약으로 거부되어야 한다");

            // V3: 예산 단위(budget_type) — 컬럼이 있고, 기존 행 호환을 위해 기본값이 TOTAL이어야 함
            try (ResultSet rs = conn.getMetaData().getColumns(schema, null, "budgets", "budget_type")) {
                assertTrue(rs.next(), "V3로 budgets.budget_type 컬럼이 추가되어야 한다");
            }
            try (Statement ok = conn.createStatement()) {
                // 컬럼을 지정하지 않아도 들어가야 한다 — V3 이전에 저장된 예산과 같은 해석(TOTAL)
                ok.executeUpdate("INSERT INTO budgets(group_id, amount) VALUES(1, 100000)");
            }
            try (Statement q = conn.createStatement();
                 ResultSet rs = q.executeQuery("SELECT budget_type FROM budgets WHERE group_id = 1")) {
                assertTrue(rs.next());
                assertEquals("TOTAL", rs.getString(1), "기본값은 TOTAL이어야 한다");
            }
            // 정의되지 않은 예산 단위는 CHECK(ck_budget_type)로 거부되어야 함.
            // 예산은 모임당 1개(UNIQUE)라 같은 모임에 또 넣으면 CHECK가 아니라 중복키로 실패한다.
            // 제약을 정확히 겨냥하기 위해 다른 모임을 하나 만들어 쓴다.
            try (Statement seed2 = conn.createStatement()) {
                seed2.executeUpdate("INSERT INTO `groups`(name, owner_id) VALUES('g2', 1)");
            }
            assertThrows(SQLException.class, () -> {
                try (Statement bad = conn.createStatement()) {
                    bad.executeUpdate("INSERT INTO budgets(group_id, budget_type, amount)" +
                            " VALUES(2, 'WEEKLY', 100000)");
                }
            }, "정의되지 않은 예산 단위는 CHECK 제약으로 거부되어야 한다");
        }
    }
}
