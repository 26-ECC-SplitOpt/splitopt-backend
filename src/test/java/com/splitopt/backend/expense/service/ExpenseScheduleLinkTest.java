package com.splitopt.backend.expense.service;

import com.splitopt.backend.expense.domain.ExpenseCategory;
import com.splitopt.backend.expense.dto.ExpenseCreateRequest;
import com.splitopt.backend.expense.dto.ExpenseResponse;
import com.splitopt.backend.expense.repository.ExpenseShareRepository;
import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.group.domain.Group;
import com.splitopt.backend.group.domain.GroupParticipant;
import com.splitopt.backend.schedule.domain.Schedule;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 지출–일정 연결 변경 테스트.
 *
 * <p>일정 상세 화면에서 지출을 연결하기 위한 경로다. 수정(20)으로 대신할 수 없다 — 그쪽은
 * 제목·금액·부담 내역을 모두 요구해서, 일정만 바꾸려고 부르면 나머지가 지워진다.
 */
@SpringBootTest
@Transactional
class ExpenseScheduleLinkTest {

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
        group = Group.builder().name("제주 3박 4일").owner(u1).build();
        em.persist(group);
        payer = participant(group, u1, GroupParticipant.Role.OWNER);
        other = participant(group, u2, GroupParticipant.Role.MEMBER);
        em.flush();
    }

    private User user(String email, String name) {
        User u = User.builder().email(email).password("p").name(name).build();
        em.persist(u);
        return u;
    }

    private GroupParticipant participant(Group g, User u, GroupParticipant.Role role) {
        GroupParticipant p = GroupParticipant.builder().group(g).user(u).role(role).build();
        em.persist(p);
        return p;
    }

    private Schedule schedule(Group g, String title) {
        Schedule s = Schedule.builder().group(g).title(title)
                .startAt(LocalDateTime.of(2026, 8, 20, 9, 0)).build();
        em.persist(s);
        em.flush();
        return s;
    }

    /** 일정 연결 없이 만든 지출. */
    private ExpenseResponse anExpense() {
        return expenseService.createExpense(group.getId(), payer.getId(), new ExpenseCreateRequest(
                "숙박", new BigDecimal("150000"), ExpenseCategory.ETC, null,
                LocalDate.of(2026, 8, 20), null,
                ExpenseCreateRequest.SplitMethod.DIRECT,
                List.of(new ExpenseCreateRequest.ShareInput(payer.getId(), new BigDecimal("75000")),
                        new ExpenseCreateRequest.ShareInput(other.getId(), new BigDecimal("75000")))));
    }

    @Test
    @DisplayName("일정을 연결하면 응답에 일정 정보가 담긴다")
    void link() {
        ExpenseResponse created = anExpense();
        Schedule lunch = schedule(group, "맛집 탐방");

        ExpenseResponse linked = expenseService.linkSchedule(
                group.getId(), created.id(), payer.getId(), lunch.getId());
        em.flush();

        assertNotNull(linked.schedule());
        assertEquals(lunch.getId(), linked.schedule().scheduleId());
        assertEquals("맛집 탐방", linked.schedule().title());
    }

    @Test
    @DisplayName("scheduleId를 null로 보내면 연결이 해제된다")
    void unlink() {
        ExpenseResponse created = anExpense();
        Schedule lunch = schedule(group, "맛집 탐방");
        expenseService.linkSchedule(group.getId(), created.id(), payer.getId(), lunch.getId());

        ExpenseResponse cleared = expenseService.linkSchedule(
                group.getId(), created.id(), payer.getId(), null);
        em.flush();

        assertNull(cleared.schedule());
    }

    @Test
    @DisplayName("이미 연결된 지출을 다른 일정으로 옮길 수 있다")
    void move() {
        ExpenseResponse created = anExpense();
        Schedule lunch = schedule(group, "맛집 탐방");
        Schedule cafe = schedule(group, "카페");
        expenseService.linkSchedule(group.getId(), created.id(), payer.getId(), lunch.getId());

        ExpenseResponse moved = expenseService.linkSchedule(
                group.getId(), created.id(), payer.getId(), cafe.getId());
        em.flush();

        assertEquals(cafe.getId(), moved.schedule().scheduleId());
    }

    @Test
    @DisplayName("연결만 바꾸고 금액·부담 내역은 그대로 둔다")
    void keepsAmountAndShares() {
        ExpenseResponse created = anExpense();
        Schedule lunch = schedule(group, "맛집 탐방");

        ExpenseResponse linked = expenseService.linkSchedule(
                group.getId(), created.id(), payer.getId(), lunch.getId());
        em.flush();

        assertEquals(0, new BigDecimal("150000").compareTo(linked.amount()));
        assertEquals("숙박", linked.title());
        assertEquals(2, expenseShareRepository.findAllByExpenseId(created.id()).size(),
                "부담 내역은 건드리지 않으므로 그대로 2건이어야 한다");
    }

    @Test
    @DisplayName("결제자가 아니면 연결할 수 없다 — 수정(20)과 같은 권한 규칙")
    void onlyPayerMayLink() {
        ExpenseResponse created = anExpense();
        Schedule lunch = schedule(group, "맛집 탐방");

        BusinessException e = assertThrows(BusinessException.class, () ->
                expenseService.linkSchedule(group.getId(), created.id(), other.getId(), lunch.getId()));
        assertEquals(ErrorCode.ACCESS_DENIED, e.getErrorCode());
    }

    @Test
    @DisplayName("다른 모임의 일정은 연결할 수 없다")
    void rejectsScheduleFromAnotherGroup() {
        ExpenseResponse created = anExpense();
        User outsider = user("c@x.com", "주영3");
        Group otherGroup = Group.builder().name("남의 모임").owner(outsider).build();
        em.persist(otherGroup);
        Schedule foreign = schedule(otherGroup, "남의 일정");

        BusinessException e = assertThrows(BusinessException.class, () ->
                expenseService.linkSchedule(group.getId(), created.id(), payer.getId(), foreign.getId()));
        assertEquals(ErrorCode.ENTITY_NOT_FOUND, e.getErrorCode());
    }

    @Test
    @DisplayName("없는 지출이면 404")
    void missingExpense() {
        Schedule lunch = schedule(group, "맛집 탐방");

        BusinessException e = assertThrows(BusinessException.class, () ->
                expenseService.linkSchedule(group.getId(), 999999L, payer.getId(), lunch.getId()));
        assertEquals(ErrorCode.ENTITY_NOT_FOUND, e.getErrorCode());
    }

    @Test
    @DisplayName("없는 일정이면 404이고, 기존 연결은 유지된다")
    void missingScheduleKeepsExistingLink() {
        ExpenseResponse created = anExpense();
        Schedule lunch = schedule(group, "맛집 탐방");
        expenseService.linkSchedule(group.getId(), created.id(), payer.getId(), lunch.getId());

        assertThrows(BusinessException.class, () ->
                expenseService.linkSchedule(group.getId(), created.id(), payer.getId(), 999999L));

        assertEquals(lunch.getId(),
                expenseService.getExpense(group.getId(), created.id()).schedule().scheduleId(),
                "실패한 요청이 기존 연결을 지워서는 안 된다");
    }
}
