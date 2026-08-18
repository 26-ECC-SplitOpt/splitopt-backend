package com.splitopt.backend.budget.repository;

import com.splitopt.backend.budget.domain.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    Optional<Budget> findByGroup_Id(Long groupId);

    boolean existsByGroup_Id(Long groupId);

    /**
     * 모임의 지출 합계 (API 39 사용액).
     *
     * <p>지출 엔티티를 예산 리포지토리에서 조회하는 형태다. 합계만 필요한데 지출 전 건을 메모리로
     * 가져와 더하지 않기 위해서이고, 지출 파트에 집계 메서드가 생기면 그쪽으로 옮긴다.
     * 지출이 하나도 없는 모임에서 null이 아니라 0이 나오도록 {@code coalesce}로 감싼다.
     */
    @Query("select coalesce(sum(e.amount), 0) from Expense e where e.group.id = :groupId")
    BigDecimal sumExpenseAmountByGroupId(@Param("groupId") Long groupId);

    /**
     * 모임 일정 전체를 감싸는 기간 (API 40 예측의 기준 시간축).
     *
     * <p>{@code budgets}에는 기간 컬럼이 없어 "얼마나 지났는지"를 예산만으로는 알 수 없다.
     * 스키마 변경 없이 쓸 수 있는 유일한 시간축이 일정이라 첫 일정 시작 ~ 마지막 일정 종료를
     * 여행 기간으로 본다. 종료 시각이 없는 일정은 시작 시각을 종료로 취급한다.
     *
     * <p>일정이 하나도 없으면 집계 결과가 한 행이되 값은 모두 {@code null}이다 — 호출부에서
     * 예측 불가로 처리한다.
     */
    @Query("""
            select min(s.startAt) as startAt, max(coalesce(s.endAt, s.startAt)) as endAt
            from Schedule s
            where s.group.id = :groupId
            """)
    SchedulePeriod findSchedulePeriodByGroupId(@Param("groupId") Long groupId);

    /**
     * 기간이 시작되기 전에 쓴 금액 (API 40).
     *
     * <p>항공권·숙소처럼 여행 전에 미리 결제한 돈이다. 이미 확정된 지출이라 "하루에 얼마씩 쓰는
     * 중"에 섞으면 안 된다. 섞으면 여행 첫날에 그 금액을 하루치로 보고 전체 일수만큼 곱해,
     * 예상 총액이 실제의 몇 배로 부풀려진다.
     *
     * <p><b>경계는 시각이 아니라 날짜로 본다.</b> 경과·전체 일수를 날짜 단위로 세므로 여기서만
     * 시각으로 자르면 어긋난다 — 첫 일정이 09시에 시작할 때, 같은 날 00시로 기록된 지출이
     * "기간 전"으로 빠져 첫날 지출이 통째로 사라진다.
     */
    @Query("""
            select coalesce(sum(e.amount), 0) from Expense e
            where e.group.id = :groupId and e.spentAt < :periodStartDate
            """)
    BigDecimal sumExpenseAmountBefore(@Param("groupId") Long groupId,
                                      @Param("periodStartDate") LocalDateTime periodStartDate);

    /** 일정 기간 조회 결과. 일정이 없으면 두 값 모두 null이다. */
    interface SchedulePeriod {
        LocalDateTime getStartAt();

        LocalDateTime getEndAt();
    }

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
            INSERT INTO budgets (group_id, budget_type, amount, created_at, updated_at)
            VALUES (:groupId, :budgetType, :amount, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE budget_type = :budgetType, amount = :amount,
                                    updated_at = CURRENT_TIMESTAMP(6)
            """, nativeQuery = true)
    int upsertAmount(@Param("groupId") Long groupId,
                     @Param("budgetType") String budgetType,
                     @Param("amount") BigDecimal amount);
}
