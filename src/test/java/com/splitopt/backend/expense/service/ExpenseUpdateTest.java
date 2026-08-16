package com.splitopt.backend.expense.service;

import com.splitopt.backend.expense.domain.ExpenseCategory;
import com.splitopt.backend.expense.dto.ExpenseCreateRequest;
import com.splitopt.backend.expense.dto.ExpenseResponse;
import com.splitopt.backend.expense.repository.ExpenseShareRepository;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 지출 수정(API 20)의 부담 내역 재저장 테스트.
 *
 * <p>수정은 기존 부담 내역을 지우고 새로 만든다. 이때 부담자가 그대로면 <b>같은
 * (expense_id, participant_id) 조합</b>이 다시 들어가는데, 그 조합에는 UNIQUE 제약이 걸려 있다.
 * 삭제가 저장보다 먼저 실행되지 않으면 제약을 위반해 수정이 통째로 실패한다.
 *
 * <p>금액만 바꾸는 것이 가장 흔한 수정이라 이 경로가 막히면 수정 기능 전체가 막힌다.
 */
@SpringBootTest
@Transactional
class ExpenseUpdateTest {

    @PersistenceContext
    private EntityManager em;
    @Autowired
    private ExpenseService expenseService;
    @Autowired
    private ExpenseShareRepository expenseShareRepository;

    private Group group;
    private GroupParticipant payer;
    private GroupParticipant other;

    @BeforeEach
    void setUp() {
        User u1 = user("a@x.com", "주영1");
        User u2 = user("b@x.com", "주영2");
        group = Group.builder().name("강릉 당일치기").owner(u1).build();
        em.persist(group);
        payer = participant(u1, GroupParticipant.Role.OWNER);
        other = participant(u2, GroupParticipant.Role.MEMBER);
        em.flush();
    }

    private User user(String email, String name) {
        User u = User.builder().email(email).password("p").name(name).build();
        em.persist(u);
        return u;
    }

    private GroupParticipant participant(User u, GroupParticipant.Role role) {
        GroupParticipant p = GroupParticipant.builder().group(group).user(u).role(role).build();
        em.persist(p);
        return p;
    }

    /** 부담자 2명에게 직접 입력으로 나눈 지출 요청. */
    private ExpenseCreateRequest request(String amount, String payerShare, String otherShare) {
        return new ExpenseCreateRequest(
                "q1", new BigDecimal(amount), ExpenseCategory.ETC, "q2",
                LocalDate.of(2026, 7, 30), null,
                ExpenseCreateRequest.SplitMethod.DIRECT,
                List.of(
                        new ExpenseCreateRequest.ShareInput(payer.getId(), new BigDecimal(payerShare)),
                        new ExpenseCreateRequest.ShareInput(other.getId(), new BigDecimal(otherShare))));
    }

    private Map<Long, BigDecimal> savedShares(Long expenseId) {
        return expenseShareRepository.findAllByExpenseId(expenseId).stream()
                .collect(Collectors.toMap(
                        share -> share.getParticipant().getId(),
                        share -> share.getShareAmount(),
                        (a, b) -> a));
    }

    @Test
    @DisplayName("부담자를 그대로 두고 금액만 바꿔도 수정된다")
    void updateKeepingSameParticipants() {
        ExpenseResponse created = expenseService.createExpense(
                group.getId(), payer.getId(), request("150000", "75000", "75000"));

        expenseService.updateExpense(group.getId(), created.id(), payer.getId(),
                request("150002", "75002", "75000"));
        // 실제 SQL이 나가야 제약 위반이 드러난다. 트랜잭션 테스트라 명시적으로 밀어낸다.
        em.flush();

        Map<Long, BigDecimal> shares = savedShares(created.id());
        assertEquals(2, shares.size(), "부담 내역이 중복되지 않고 2건이어야 한다");
        assertEquals(0, new BigDecimal("75002").compareTo(shares.get(payer.getId())));
        assertEquals(0, new BigDecimal("75000").compareTo(shares.get(other.getId())));
    }

    @Test
    @DisplayName("부담자를 바꾸는 수정도 된다 — 빠진 참여자의 부담 내역은 사라진다")
    void updateChangingParticipants() {
        ExpenseResponse created = expenseService.createExpense(
                group.getId(), payer.getId(), request("150000", "75000", "75000"));

        ExpenseCreateRequest onlyPayer = new ExpenseCreateRequest(
                "q1", new BigDecimal("150000"), ExpenseCategory.ETC, "q2",
                LocalDate.of(2026, 7, 30), null,
                ExpenseCreateRequest.SplitMethod.DIRECT,
                List.of(new ExpenseCreateRequest.ShareInput(payer.getId(), new BigDecimal("150000"))));
        expenseService.updateExpense(group.getId(), created.id(), payer.getId(), onlyPayer);
        em.flush();

        Map<Long, BigDecimal> shares = savedShares(created.id());
        assertEquals(1, shares.size());
        assertEquals(0, new BigDecimal("150000").compareTo(shares.get(payer.getId())));
    }
}
