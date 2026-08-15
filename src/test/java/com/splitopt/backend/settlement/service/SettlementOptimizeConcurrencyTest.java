package com.splitopt.backend.settlement.service;

import com.splitopt.backend.expense.domain.Expense;
import com.splitopt.backend.expense.domain.ExpenseCategory;
import com.splitopt.backend.expense.domain.ExpenseShare;
import com.splitopt.backend.group.domain.Group;
import com.splitopt.backend.group.domain.GroupParticipant;
import com.splitopt.backend.settlement.domain.SettlementStatus;
import com.splitopt.backend.settlement.dto.SettlementResponse;
import com.splitopt.backend.settlement.dto.SettlementStatusChangeRequest.Action;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 정산 최적화(API 24) 동시 실행 검증.
 *
 * <p>최적화는 "잔액 조회 → 기존 PENDING 삭제 → 새 PENDING 저장"의 read-modify-write다.
 * 모임 행을 잠그지 않으면 동시 요청이 서로의 미커밋 PENDING을 보지 못해 각자 삭제할 것이
 * 없다고 판단하고 각자 저장한다 — 같은 청구가 요청 수만큼 쌓인다.
 *
 * <p>동시성을 실제로 재현해야 하므로 테스트 트랜잭션(@Transactional 롤백)을 쓰지 않는다.
 * 픽스처는 {@link TransactionTemplate}으로 커밋하고 각 테스트 후 직접 정리한다.
 */
@SpringBootTest
class SettlementOptimizeConcurrencyTest {

    private static final int THREADS = 6;

    @PersistenceContext
    private EntityManager em;
    @Autowired
    private SettlementService settlementService;
    @Autowired
    private PlatformTransactionManager txManager;

    private TransactionTemplate tx;
    private Long groupId;
    private List<Long> userIds;

    /** 커밋 성공 후에만 필드로 옮기기 위한 setUp 반환값. */
    private record Fixture(Long groupId, List<Long> userIds) {
    }

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        Fixture fixture = tx.execute(status -> {
            List<User> users = IntStream.range(0, 3)
                    .mapToObj(i -> User.builder()
                            .email("optimize-" + UUID.randomUUID() + "@x.com")
                            .password("p").name("U" + i).build())
                    .toList();
            users.forEach(em::persist);

            Group group = Group.builder().name("워크샵").owner(users.get(0)).build();
            em.persist(group);

            List<GroupParticipant> participants = users.stream()
                    .map(u -> GroupParticipant.builder().group(group).user(u)
                            .role(GroupParticipant.Role.MEMBER).build())
                    .toList();
            participants.forEach(em::persist);

            // 첫 참여자가 30000을 결제하고 셋이 균등 부담 — 나머지 둘이 10000씩 보내야 한다.
            Expense expense = Expense.builder()
                    .group(group)
                    .payer(participants.get(0))
                    .title("숙소")
                    .amount(new BigDecimal("30000"))
                    .category(ExpenseCategory.ACCOMMODATION)
                    .spentAt(LocalDateTime.now())
                    .build();
            em.persist(expense);
            participants.forEach(p -> em.persist(ExpenseShare.builder()
                    .expense(expense).participant(p)
                    .shareAmount(new BigDecimal("10000")).build()));

            em.flush();
            return new Fixture(group.getId(), users.stream().map(User::getId).toList());
        });
        groupId = fixture.groupId();
        userIds = fixture.userIds();
    }

    @AfterEach
    void tearDown() {
        // 테스트 트랜잭션 롤백이 없으므로 커밋된 픽스처를 직접 지운다(FK 역순).
        // setUp이 커밋에 실패하면 id가 null이므로 건너뛴다 — 정리 중 2차 예외가
        // 원래 실패 원인을 가리지 않게 한다.
        tx.executeWithoutResult(status -> {
            if (groupId != null) {
                em.createQuery("delete from Settlement s where s.group.id = :gid")
                        .setParameter("gid", groupId).executeUpdate();
                em.createQuery("""
                                delete from ExpenseShare es
                                where es.expense.id in (select e.id from Expense e where e.group.id = :gid)
                                """)
                        .setParameter("gid", groupId).executeUpdate();
                em.createQuery("delete from Expense e where e.group.id = :gid")
                        .setParameter("gid", groupId).executeUpdate();
                em.createQuery("delete from GroupParticipant p where p.group.id = :gid")
                        .setParameter("gid", groupId).executeUpdate();
                em.remove(em.getReference(Group.class, groupId));
                em.flush();
            }
            if (userIds != null) {
                userIds.forEach(id -> em.remove(em.getReference(User.class, id)));
            }
        });
    }

    @Test
    @DisplayName("같은 모임에 최적화 요청이 동시에 몰려도 정산은 한 번 실행한 결과만 남는다")
    void concurrentOptimizeLeavesSingleResult() throws Exception {
        List<Throwable> failures = runConcurrently(IntStream.range(0, THREADS)
                .mapToObj(i -> (Callable<Void>) () -> {
                    settlementService.optimize(groupId);
                    return null;
                })
                .toList());

        assertAll(
                () -> assertTrue(failures.isEmpty(),
                        () -> "동시 최적화가 실패했다: " + describe(failures)),
                () -> assertEquals(2L, countSettlements(),
                        "직렬화되지 않으면 요청 수만큼 청구가 쌓인다"),
                () -> assertEquals(0, new BigDecimal("20000").compareTo(pendingTotal()),
                        "미정산 청구 총액은 실제 채무 20000이어야 한다")
        );
    }

    @Test
    @DisplayName("완료된 정산이 있는 상태로 동시 재실행해도 완료분은 보존되고 재청구되지 않는다")
    void concurrentRerunKeepsCompletedAndDoesNotRebill() throws Exception {
        SettlementResponse first = tx.execute(status -> settlementService.optimize(groupId)).get(0);
        tx.executeWithoutResult(status ->
                settlementService.changeStatus(groupId, first.settlementId(), Action.SEND, first.fromParticipantId()));
        tx.executeWithoutResult(status ->
                settlementService.changeStatus(groupId, first.settlementId(), Action.CONFIRM, first.toParticipantId()));

        List<Throwable> failures = runConcurrently(IntStream.range(0, THREADS)
                .mapToObj(i -> (Callable<Void>) () -> {
                    settlementService.optimize(groupId);
                    return null;
                })
                .toList());

        assertAll(
                () -> assertTrue(failures.isEmpty(),
                        () -> "동시 재실행이 실패했다: " + describe(failures)),
                () -> assertEquals(1L, countByStatus(SettlementStatus.COMPLETED), "완료분은 보존"),
                () -> assertEquals(1L, countByStatus(SettlementStatus.PENDING),
                        "남은 채무 1건만 청구되어야 한다"),
                () -> assertEquals(0, new BigDecimal("10000").compareTo(pendingTotal()))
        );
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
                    future.get(20, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                }
            }
            return failures;
        } finally {
            pool.shutdownNow();
        }
    }

    private long countSettlements() {
        Number count = tx.execute(status -> (Number) em
                .createNativeQuery("SELECT COUNT(*) FROM settlements WHERE group_id = :groupId")
                .setParameter("groupId", groupId)
                .getSingleResult());
        return count.longValue();
    }

    private long countByStatus(SettlementStatus status) {
        Number count = tx.execute(ts -> (Number) em
                .createNativeQuery("SELECT COUNT(*) FROM settlements WHERE group_id = :groupId AND status = :status")
                .setParameter("groupId", groupId)
                .setParameter("status", status.name())
                .getSingleResult());
        return count.longValue();
    }

    private BigDecimal pendingTotal() {
        return tx.execute(status -> em
                .createQuery("""
                        select coalesce(sum(s.amount), 0)
                        from Settlement s
                        where s.group.id = :gid and s.status = :status
                        """, BigDecimal.class)
                .setParameter("gid", groupId)
                .setParameter("status", SettlementStatus.PENDING)
                .getSingleResult());
    }

    private String describe(List<Throwable> failures) {
        return failures.stream()
                .map(t -> t.getClass().getSimpleName() + ": " + t.getMessage())
                .collect(Collectors.joining(" | "));
    }
}
