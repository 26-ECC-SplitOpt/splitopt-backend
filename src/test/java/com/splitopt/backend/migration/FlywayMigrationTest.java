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
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    @DisplayName("V1이 MySQL 8에 오류 없이 적용되고, 8개 도메인 테이블이 생성된다")
    void v1AppliesCleanlyOnMySql8() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load();

        MigrateResult result = flyway.migrate();
        assertEquals(1, result.migrationsExecuted, "V1 마이그레이션 1개가 적용되어야 한다");

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

            // 스키마 계약: FK·CHECK 제약이 실제로 생성됐는지 검증
            try (Statement st = conn.createStatement()) {
                try (ResultSet fk = st.executeQuery(
                        "SELECT COUNT(*) FROM information_schema.referential_constraints" +
                                " WHERE constraint_schema = '" + schema + "'")) {
                    fk.next();
                    assertEquals(13, fk.getInt(1), "외래키(FK) 13개가 생성되어야 한다");
                }
                try (ResultSet ck = st.executeQuery(
                        "SELECT COUNT(*) FROM information_schema.table_constraints" +
                                " WHERE table_schema = '" + schema + "' AND constraint_type = 'CHECK'")) {
                    ck.next();
                    assertEquals(8, ck.getInt(1), "CHECK 제약 8개가 생성되어야 한다");
                }
            }
        }
    }
}
