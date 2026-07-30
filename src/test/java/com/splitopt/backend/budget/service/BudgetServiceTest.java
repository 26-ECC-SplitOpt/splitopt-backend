package com.splitopt.backend.budget.service;

import com.splitopt.backend.budget.dto.BudgetResponse;
import com.splitopt.backend.budget.repository.BudgetRepository;
import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.group.domain.Group;
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
}
