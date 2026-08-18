package com.splitopt.backend.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 운영 스키마(Flyway)와 엔티티가 어긋나지 않는지 확인한다.
 *
 * <p>테스트와 로컬은 엔티티로 스키마를 만들고(ddl-auto) 운영만 Flyway를 쓴다. 그래서 둘이
 * 어긋나도 어떤 테스트도 잡지 못하고, 운영에서만 터진다. 실제로 두 번 그랬다 —
 * {@code expense_shares}의 UNIQUE 제약이 마이그레이션에만 있어 지출 수정이 운영에서만 500이 났다.
 *
 * <p>여기서는 Flyway를 실제 MySQL에 적용한 뒤 Hibernate {@code validate}로 대조한다.
 * 어긋나면 컨텍스트가 뜨지 않는다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class SchemaDriftTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("splitopt");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        // 핵심: 엔티티가 마이그레이션 결과와 맞는지 검증만 하고 스키마를 건드리지 않는다
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.defer-datasource-initialization", () -> "false");
        registry.add("spring.sql.init.mode", () -> "never");
    }

    @Test
    @DisplayName("마이그레이션으로 만든 스키마가 엔티티 정의와 일치한다")
    void entitiesMatchMigratedSchema() {
        // 컨텍스트가 떴다는 것 자체가 검증 결과다 (ddl-auto=validate)
    }
}
