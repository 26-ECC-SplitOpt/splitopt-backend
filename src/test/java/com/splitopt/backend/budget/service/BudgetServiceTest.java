package com.splitopt.backend.budget.service;

import com.splitopt.backend.budget.dto.BudgetResponse;
import com.splitopt.backend.budget.repository.BudgetRepository;
import com.splitopt.backend.expense.domain.Expense;
import com.splitopt.backend.expense.domain.ExpenseCategory;
import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.group.domain.Group;
import com.splitopt.backend.group.domain.GroupParticipant;
import com.splitopt.backend.user.domain.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class BudgetServiceTest {

    @PersistenceContext
    private EntityManager em;
    @Autowired
    private BudgetService budgetService;
    @Autowired
    private BudgetRepository budgetRepository;

    private Group group;
    private final Map<Long, GroupParticipant> payers = new HashMap<>();

    @BeforeEach
    void setUp() {
        User owner = User.builder().email("o@x.com").password("p").name("O").build();
        em.persist(owner);
        group = Group.builder().name("워크샵").owner(owner).build();
        em.persist(group);
        em.flush();
    }

    @Test
    @DisplayName("예산 최초 설정 시 생성된다")
    void createsBudget() {
        BudgetResponse res = budgetService.upsert(group.getId(), new BigDecimal("500000"));

        assertEquals(group.getId(), res.groupId());
        assertEquals(0, res.amount().compareTo(new BigDecimal("500000")));
        assertTrue(budgetRepository.existsByGroup_Id(group.getId()));
    }

    @Test
    @DisplayName("이미 예산이 있으면 새로 만들지 않고 금액만 수정한다 (모임당 1개)")
    void updatesExistingBudget() {
        budgetService.upsert(group.getId(), new BigDecimal("500000"));
        budgetService.upsert(group.getId(), new BigDecimal("700000"));

        assertEquals(1, budgetRepository.count(), "모임당 예산은 1개");
        assertEquals(0, budgetRepository.findByGroup_Id(group.getId()).orElseThrow()
                .getAmount().compareTo(new BigDecimal("700000")));
    }

    @Test
    @DisplayName("예산 조회 - 설정된 금액을 반환한다")
    void getBudgetReturnsAmount() {
        budgetService.upsert(group.getId(), new BigDecimal("300000"));
        BudgetResponse res = budgetService.getBudget(group.getId());
        assertEquals(0, res.amount().compareTo(new BigDecimal("300000")));
    }

    @Test
    @DisplayName("현황(39): 지출이 없으면 사용액 0·잔여는 예산 전액")
    void usageWithoutExpenses() {
        budgetService.upsert(group.getId(), new BigDecimal("300000"));

        BudgetResponse res = budgetService.getBudget(group.getId());

        assertAll(
                () -> assertEquals(0, res.spent().signum()),
                () -> assertEquals(0, res.remaining().compareTo(new BigDecimal("300000"))),
                () -> assertFalse(res.exceeded()),
                () -> assertEquals(0, res.usageRate().compareTo(BigDecimal.ZERO))
        );
    }

    @Test
    @DisplayName("현황(39): 사용액은 모임의 지출 합계, 잔여·사용률이 함께 계산된다")
    void usageSumsGroupExpenses() {
        budgetService.upsert(group.getId(), new BigDecimal("200000"));
        expense("120000");
        expense("30000");

        BudgetResponse res = budgetService.getBudget(group.getId());

        assertAll(
                () -> assertEquals(0, res.spent().compareTo(new BigDecimal("150000"))),
                () -> assertEquals(0, res.remaining().compareTo(new BigDecimal("50000"))),
                () -> assertFalse(res.exceeded()),
                () -> assertEquals(0, res.usageRate().compareTo(new BigDecimal("75.0")))
        );
    }

    @Test
    @DisplayName("현황(39): 예산을 넘으면 잔여가 음수이고 초과로 표시된다")
    void usageMarksExceeded() {
        budgetService.upsert(group.getId(), new BigDecimal("100000"));
        expense("130000");

        BudgetResponse res = budgetService.getBudget(group.getId());

        assertAll(
                () -> assertEquals(0, res.remaining().compareTo(new BigDecimal("-30000")),
                        "얼마나 넘었는지 보여야 하므로 0으로 깎지 않는다"),
                () -> assertTrue(res.exceeded()),
                () -> assertEquals(0, res.usageRate().compareTo(new BigDecimal("130.0")))
        );
    }

    @Test
    @DisplayName("현황(39): 지출이 예산과 정확히 같으면 초과가 아니다 (경계)")
    void usageAtExactBudgetIsNotExceeded() {
        budgetService.upsert(group.getId(), new BigDecimal("100000"));
        expense("100000");

        BudgetResponse res = budgetService.getBudget(group.getId());

        assertAll(
                () -> assertEquals(0, res.remaining().signum()),
                () -> assertFalse(res.exceeded()),
                () -> assertEquals(0, res.usageRate().compareTo(new BigDecimal("100.0")))
        );
    }

    @Test
    @DisplayName("현황(39): 예산 0원에 지출이 있으면 사용률은 0이지만 초과로 표시된다")
    void zeroBudgetWithSpendingIsExceeded() {
        budgetService.upsert(group.getId(), BigDecimal.ZERO);
        expense("5000");

        BudgetResponse res = budgetService.getBudget(group.getId());

        assertAll(
                () -> assertEquals(0, res.usageRate().compareTo(BigDecimal.ZERO), "0으로 나눌 수 없다"),
                () -> assertTrue(res.exceeded()),
                () -> assertEquals(0, res.remaining().compareTo(new BigDecimal("-5000")))
        );
    }

    @Test
    @DisplayName("현황(39): 다른 모임의 지출은 사용액에 섞이지 않는다")
    void usageIsScopedToGroup() {
        budgetService.upsert(group.getId(), new BigDecimal("200000"));
        expense("50000");

        User otherOwner = User.builder().email("o2@x.com").password("p").name("O2").build();
        em.persist(otherOwner);
        Group otherGroup = Group.builder().name("동아리").owner(otherOwner).build();
        em.persist(otherGroup);
        em.flush();
        expense(otherGroup, "999000");

        assertEquals(0, budgetService.getBudget(group.getId()).spent()
                .compareTo(new BigDecimal("50000")));
    }

    @Test
    @DisplayName("설정(38) 응답도 현황을 함께 담는다 — 설정 직후 화면이 다시 조회하지 않도록")
    void upsertResponseCarriesUsage() {
        expense("40000");

        BudgetResponse res = budgetService.upsert(group.getId(), new BigDecimal("100000"));

        assertEquals(0, res.spent().compareTo(new BigDecimal("40000")));
        assertEquals(0, res.remaining().compareTo(new BigDecimal("60000")));
    }

    private void expense(String amount) {
        expense(group, amount);
    }

    /** 결제자·부담 내역은 사용액 집계와 무관하므로 지출 행만 만든다. */
    private void expense(Group targetGroup, String amount) {
        em.persist(Expense.builder()
                .group(targetGroup)
                .payer(payerOf(targetGroup))
                .title("지출")
                .amount(new BigDecimal(amount))
                .category(ExpenseCategory.ETC)
                .spentAt(LocalDateTime.now())
                .build());
        em.flush();
    }

    /** 모임당 참여자는 (모임, 사용자) UNIQUE라 한 번만 만들어 재사용한다. */
    private GroupParticipant payerOf(Group targetGroup) {
        return payers.computeIfAbsent(targetGroup.getId(), id -> {
            GroupParticipant payer = GroupParticipant.builder()
                    .group(targetGroup)
                    .user(targetGroup.getOwner())
                    .role(GroupParticipant.Role.OWNER)
                    .build();
            em.persist(payer);
            return payer;
        });
    }

    @Test
    @DisplayName("예산이 없으면 조회 시 예외")
    void getBudgetThrowsWhenAbsent() {
        assertThrows(BusinessException.class, () -> budgetService.getBudget(group.getId()));
    }

    @Test
    @DisplayName("음수 예산은 거부된다")
    void rejectsNegativeAmount() {
        assertThrows(IllegalArgumentException.class,
                () -> budgetService.upsert(group.getId(), new BigDecimal("-1")));
    }

    @Test
    @DisplayName("소수점 3자리 예산은 도메인에서 거부된다 (서비스 직접 호출)")
    void rejectsExcessiveScale() {
        assertThrows(IllegalArgumentException.class,
                () -> budgetService.upsert(group.getId(), new BigDecimal("100.001")));
    }

    @Test
    @DisplayName("정수 11자리 예산은 도메인에서 거부된다 (DECIMAL(12,2) 초과)")
    void rejectsOversizedInteger() {
        assertThrows(IllegalArgumentException.class,
                () -> budgetService.upsert(group.getId(), new BigDecimal("12345678901")));
    }
}
