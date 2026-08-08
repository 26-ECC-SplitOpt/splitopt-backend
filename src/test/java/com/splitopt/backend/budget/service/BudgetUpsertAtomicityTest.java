package com.splitopt.backend.budget.service;

import com.splitopt.backend.budget.domain.Budget;
import com.splitopt.backend.budget.repository.BudgetRepository;
import com.splitopt.backend.group.domain.Group;
import com.splitopt.backend.user.domain.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 예산 설정(API 38) 원자적 upsert 검증 — 이슈 #5 회귀 테스트.
 *
 * <p>동시성을 실제로 재현해야 하므로 테스트 트랜잭션(@Transactional 롤백)을 쓰지 않는다.
 * 픽스처는 {@link TransactionTemplate}으로 커밋해 다른 스레드에서도 보이게 하고,
 * 각 테스트 후 직접 정리한다.
 */
@SpringBootTest
class BudgetUpsertAtomicityTest {

    private static final int THREADS = 8;

    @PersistenceContext
    private EntityManager em;
    @Autowired
    private BudgetService budgetService;
    @Autowired
    private BudgetRepository budgetRepository;
    @Autowired
    private PlatformTransactionManager txManager;

    private TransactionTemplate tx;
    private Long groupId;
    private Long userId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(status -> {
            // 이메일 UNIQUE 충돌로 테스트가 서로 간섭하지 않도록 픽스처마다 다른 값을 쓴다.
            User owner = User.builder()
                    .email("atomicity-" + UUID.randomUUID() + "@x.com")
                    .password("p").name("O").build();
            em.persist(owner);
            Group group = Group.builder().name("워크샵").owner(owner).build();
            em.persist(group);
            em.flush();
            userId = owner.getId();
            groupId = group.getId();
        });
    }

    @AfterEach
    void tearDown() {
        // 테스트 트랜잭션 롤백이 없으므로 커밋된 픽스처를 직접 지운다.
        // (네이티브 SQL 대신 JPA 삭제 — `groups`는 예약어라 DB별 인용 방식이 달라진다.)
        // setUp이 커밋 전에 실패하면 id가 null이므로 건너뛴다 — 정리 중 2차 예외가
        // 원래 실패 원인을 가리지 않게 한다.
        tx.executeWithoutResult(status -> {
            if (groupId != null) {
                budgetRepository.findByGroup_Id(groupId).ifPresent(budgetRepository::delete);
                budgetRepository.flush();
                em.remove(em.getReference(Group.class, groupId));
            }
            if (userId != null) {
                em.remove(em.getReference(User.class, userId));
            }
        });
    }

    @Test
    @DisplayName("같은 모임에 동시 최초 설정 요청이 몰려도 모두 성공하고 예산은 1개만 남는다")
    void concurrentFirstTimeUpsertsAllSucceedWithSingleRow() throws Exception {
        // 8개 스레드가 같은 모임의 "아직 없는" 예산을 동시에 설정 — 조회 후 저장 방식에서는
        // 둘 이상이 "없음"을 보고 INSERT해 uk_budgets_group 위반이 발생할 수 있던 구간이다.
        List<BigDecimal> amounts = IntStream.range(0, THREADS)
                .mapToObj(i -> new BigDecimal((i + 1) * 100_000))
                .toList();

        List<Throwable> failures = runConcurrently(amounts.stream()
                .map(amount -> (Callable<Void>) () -> {
                    budgetService.upsert(groupId, amount);
                    return null;
                })
                .toList());

        Budget saved = readBudget();
        assertAll(
                () -> assertTrue(failures.isEmpty(),
                        () -> "동시 upsert가 실패했다: " + describe(failures)),
                () -> assertEquals(1L, countBudgetRows(), "모임당 예산 행은 1개"),
                () -> assertNotNull(saved, "예산이 저장되어야 한다"),
                () -> assertTrue(
                        amounts.stream().anyMatch(a -> a.compareTo(saved.getAmount()) == 0),
                        () -> "최종 금액은 요청된 값 중 하나여야 한다: " + saved.getAmount())
        );
    }

    @Test
    @DisplayName("이미 예산이 있는 모임에 동시 수정 요청이 몰려도 행이 늘지 않는다")
    void concurrentUpdatesDoNotDuplicateRow() throws Exception {
        budgetService.upsert(groupId, new BigDecimal("10000"));

        Set<BigDecimal> amounts = IntStream.range(0, THREADS)
                .mapToObj(i -> new BigDecimal((i + 1) * 7_000))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        List<Throwable> failures = runConcurrently(amounts.stream()
                .map(amount -> (Callable<Void>) () -> {
                    budgetService.upsert(groupId, amount);
                    return null;
                })
                .toList());

        Budget saved = readBudget();
        assertAll(
                () -> assertTrue(failures.isEmpty(),
                        () -> "동시 수정이 실패했다: " + describe(failures)),
                () -> assertEquals(1L, countBudgetRows(), "수정은 행을 늘리지 않는다"),
                () -> assertTrue(amounts.stream().anyMatch(a -> a.compareTo(saved.getAmount()) == 0),
                        () -> "최종 금액은 요청된 값 중 하나여야 한다: " + saved.getAmount())
        );
    }

    @Test
    @DisplayName("수정 시 created_at은 최초 설정 시각으로 보존된다 (감사 이력 유지)")
    void updatePreservesCreatedAt() {
        budgetService.upsert(groupId, new BigDecimal("100000"));
        Budget initial = readBudget();
        LocalDateTime createdAt = initial.getCreatedAt();
        LocalDateTime firstUpdatedAt = initial.getUpdatedAt();
        assertNotNull(createdAt, "최초 설정 시 created_at이 기록되어야 한다");

        // 별도 트랜잭션으로 수정 — H2는 CURRENT_TIMESTAMP가 트랜잭션 시작 시각으로 고정된다.
        budgetService.upsert(groupId, new BigDecimal("250000"));

        Budget updated = readBudget();
        assertAll(
                () -> assertEquals(createdAt, updated.getCreatedAt(),
                        "수정이 created_at을 덮어써서는 안 된다"),
                () -> assertNotNull(updated.getUpdatedAt()),
                () -> assertFalse(updated.getUpdatedAt().isBefore(firstUpdatedAt),
                        "updated_at은 이전 값보다 앞설 수 없다"),
                // 금액이 바뀐 것이 UPDATE 경로가 실제로 실행됐다는 증거다.
                // (updated_at의 전진은 시각 해상도에 의존하므로 단언하지 않는다)
                () -> assertEquals(0, updated.getAmount().compareTo(new BigDecimal("250000")))
        );
    }

    @Test
    @DisplayName("같은 금액으로 다시 설정해도 성공 응답과 단일 행을 유지한다 (영향 행 0)")
    void reUpsertWithIdenticalAmountSucceeds() {
        BigDecimal amount = new BigDecimal("120000.50");
        budgetService.upsert(groupId, amount);

        // MySQL은 값이 바뀌지 않은 upsert에 대해 영향 행 0을 반환한다.
        // 서비스가 이 값을 성공 여부로 오해하지 않아야 한다.
        assertEquals(0, budgetService.upsert(groupId, amount).amount().compareTo(amount));
        assertEquals(1L, countBudgetRows());
    }

    /** 주어진 작업들을 동시에 실행하고, 발생한 예외 목록을 반환한다. */
    private List<Throwable> runConcurrently(List<Callable<Void>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();
        try {
            for (Callable<Void> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    // 대기가 타임아웃되면 스레드가 순차 실행되어 동시성 검증이 조용히 무력화된다.
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("동시 시작 신호 대기 시간 초과");
                    }
                    return task.call();
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS), "스레드 준비 실패");
            start.countDown();

            List<Throwable> failures = new ArrayList<>();
            for (Future<Void> future : futures) {
                try {
                    future.get(10, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                }
            }
            return failures;
        } finally {
            pool.shutdownNow();
        }
    }

    private Budget readBudget() {
        return tx.execute(status -> budgetRepository.findByGroup_Id(groupId).orElse(null));
    }

    private long countBudgetRows() {
        Number count = tx.execute(status -> (Number) em
                .createNativeQuery("SELECT COUNT(*) FROM budgets WHERE group_id = :groupId")
                .setParameter("groupId", groupId)
                .getSingleResult());
        return count.longValue();
    }

    private String describe(List<Throwable> failures) {
        return failures.stream()
                .map(t -> t.getClass().getSimpleName() + ": " + t.getMessage())
                .collect(Collectors.joining(" | "));
    }
}
