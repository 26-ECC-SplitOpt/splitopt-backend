package com.splitopt.backend.budget.repository;

import com.splitopt.backend.budget.domain.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    Optional<Budget> findByGroup_Id(Long groupId);

    boolean existsByGroup_Id(Long groupId);

    /**
     * 예산 원자적 upsert (API 38).
     *
     * <p>{@code uk_budgets_group}(group_id UNIQUE) 위에서 삽입·수정을 <b>한 문장</b>으로 처리한다.
     * 조회 후 저장 방식은 같은 모임에 대한 동시 최초 설정 요청에서 두 요청이 모두 "예산 없음"을 보고
     * INSERT를 시도해 한쪽이 UNIQUE 위반으로 실패할 수 있었다. 이 쿼리는 그 창(window)을 없앤다.
     *
     * <p>감사 컬럼은 JPA 감사(AuditingEntityListener)를 우회하므로 SQL에서 직접 채운다.
     * {@code created_at}은 INSERT에서만 설정하고, 수정 경로에서는 {@code updated_at}만 갱신한다.
     *
     * <p>문법은 MySQL 8 기준이며(마이그레이션 SQL과 같은 전제), 테스트 H2는 {@code MODE=MySQL}로
     * 동일하게 동작한다. 갱신값은 MySQL 8.0.20에서 deprecated된 {@code VALUES(amount)} 대신
     * 파라미터를 다시 바인딩해 양쪽 DB에서 모두 유효하게 한다.
     *
     * <p><b>호출 시 주의</b> — {@code clearAutomatically}로 <b>호출 트랜잭션의 영속성 컨텍스트
     * 전체가 비워진다</b>(네이티브 쿼리가 우회한 변경을 이후 조회가 다시 읽게 하려면 필요).
     * 같은 트랜잭션에서 관리 중이던 엔티티는 준영속이 되어 더티 체킹이 끊기므로, 호출 후에는
     * 필요한 엔티티를 다시 조회해야 한다.
     *
     * @return 영향받은 행 수 (MySQL: 삽입 1 / 값이 바뀐 수정 2 / 같은 값 재설정 0)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO budgets (group_id, amount, created_at, updated_at)
            VALUES (:groupId, :amount, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE amount = :amount, updated_at = CURRENT_TIMESTAMP(6)
            """, nativeQuery = true)
    int upsertAmount(@Param("groupId") Long groupId, @Param("amount") BigDecimal amount);
}
