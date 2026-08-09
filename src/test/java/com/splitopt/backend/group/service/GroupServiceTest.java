package com.splitopt.backend.group.service;

import com.splitopt.backend.budget.domain.Budget;
import com.splitopt.backend.budget.repository.BudgetRepository;
import com.splitopt.backend.expense.domain.Expense;
import com.splitopt.backend.expense.domain.ExpenseCategory;
import com.splitopt.backend.expense.domain.ExpenseShare;
import com.splitopt.backend.expense.repository.ExpenseRepository;
import com.splitopt.backend.expense.repository.ExpenseShareRepository;
import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.group.domain.Group;
import com.splitopt.backend.group.dto.CreateGroupRequest;
import com.splitopt.backend.group.dto.GroupDetailResponse;
import com.splitopt.backend.group.dto.GroupListResponse;
import com.splitopt.backend.group.dto.GroupResponse;
import com.splitopt.backend.group.repository.GroupParticipantRepository;
import com.splitopt.backend.group.repository.GroupRepository;
import com.splitopt.backend.schedule.domain.Schedule;
import com.splitopt.backend.schedule.repository.ScheduleRepository;
import com.splitopt.backend.user.domain.User;
import com.splitopt.backend.user.dto.MessageResponse;
import com.splitopt.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class GroupServiceTest {

    @PersistenceContext
    private EntityManager em;
    @Autowired
    private GroupService groupService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private GroupParticipantRepository groupParticipantRepository;
    @Autowired
    private ExpenseRepository expenseRepository;
    @Autowired
    private ExpenseShareRepository expenseShareRepository;
    @Autowired
    private BudgetRepository budgetRepository;
    @Autowired
    private ScheduleRepository scheduleRepository;

    private User owner;
    private User other;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(User.builder()
                .email("owner-" + System.nanoTime() + "@ex.com")
                .password("password")
                .name("소유자")
                .build());
        other = userRepository.save(User.builder()
                .email("other-" + System.nanoTime() + "@ex.com")
                .password("password")
                .name("다른유저")
                .build());
    }

    private CreateGroupRequest req(String name, String description) {
        CreateGroupRequest r = new CreateGroupRequest();
        ReflectionTestUtils.setField(r, "name", name);
        ReflectionTestUtils.setField(r, "description", description);
        return r;
    }

    @Test
    @DisplayName("모임 생성 — OWNER 참여자 1명, KRW")
    void create() {
        GroupResponse res = groupService.create(owner.getId(), req("강릉 당일치기", "메모"));

        assertNotNull(res.getGroupId());
        assertEquals("강릉 당일치기", res.getName());
        assertEquals("KRW", res.getCurrency());
        assertEquals(owner.getId(), res.getOwnerId());
        assertEquals(1, res.getMemberCount());
    }

    @Test
    @DisplayName("내 모임 목록")
    void list() {
        groupService.create(owner.getId(), req("모임1", null));
        GroupListResponse res = groupService.getMyGroups(owner.getId(), 0, 20);

        assertEquals(1, res.getTotalElements());
        assertEquals("NOT_STARTED", res.getGroups().get(0).getSettledStatus());
    }

    @Test
    @DisplayName("모임 상세 — 참여자·총지출")
    void detail() {
        Long groupId = groupService.create(owner.getId(), req("상세모임", "설명")).getGroupId();
        GroupDetailResponse res = groupService.getDetail(groupId, owner.getId());

        assertEquals(groupId, res.getGroupId());
        assertEquals(1, res.getParticipants().size());
        assertNotNull(res.getParticipants().get(0).getParticipantId());
        assertEquals(owner.getId(), res.getParticipants().get(0).getUserId());
        assertEquals("OWNER", res.getParticipants().get(0).getRole());
        assertEquals(0, res.getTotalExpense().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("모임 상세 — 비참여자 403")
    void detail_denied() {
        Long groupId = groupService.create(owner.getId(), req("비밀모임", null)).getGroupId();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> groupService.getDetail(groupId, other.getId()));
        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
    }

    @Test
    @DisplayName("모임 상세 — 없는 groupId 404")
    void detail_notFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> groupService.getDetail(999_999L, owner.getId()));
        assertEquals(ErrorCode.ENTITY_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("모임 수정 — OWNER 성공, updatedAt만")
    void update_owner() {
        Long groupId = groupService.create(owner.getId(), req("이전이름", "이전")).getGroupId();
        GroupResponse res = groupService.update(groupId, owner.getId(), req("제주도 우정여행", "수정된 설명"));

        assertEquals("제주도 우정여행", res.getName());
        assertEquals("수정된 설명", res.getDescription());
        assertNotNull(res.getUpdatedAt());
        assertNull(res.getCreatedAt());
    }

    @Test
    @DisplayName("모임 수정 — 비OWNER 403")
    void update_denied() {
        Long groupId = groupService.create(owner.getId(), req("모임", null)).getGroupId();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> groupService.update(groupId, other.getId(), req("해킹", "x")));
        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
    }

    @Test
    @DisplayName("모임 수정 — 없는 groupId 404")
    void update_notFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> groupService.update(999_999L, owner.getId(), req("이름", "설명")));
        assertEquals(ErrorCode.ENTITY_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("모임 삭제 — OWNER 삭제 후 없음")
    void delete_owner() {
        Long groupId = groupService.create(owner.getId(), req("삭제할모임", null)).getGroupId();
        MessageResponse res = groupService.delete(groupId, owner.getId());

        assertEquals("모임이 삭제되었습니다.", res.getMessage());
        assertFalse(groupRepository.existsById(groupId));
    }

    @Test
    @DisplayName("모임 삭제 — 지출·예산·일정·참여자까지 함께 삭제")
    void delete_removesChildren() {
        Long groupId = groupService.create(owner.getId(), req("연쇄삭제", null)).getGroupId();
        Group group = groupRepository.findById(groupId).orElseThrow();
        var ownerParticipant = groupParticipantRepository
                .findByGroupIdAndUserId(groupId, owner.getId()).orElseThrow();

        em.persist(Budget.builder().group(group).amount(new BigDecimal("100000")).build());
        em.persist(Schedule.builder()
                .group(group)
                .title("일정")
                .startAt(LocalDateTime.of(2026, 8, 1, 10, 0))
                .build());
        Expense expense = Expense.builder()
                .group(group)
                .payer(ownerParticipant)
                .title("점심")
                .amount(new BigDecimal("10000"))
                .category(ExpenseCategory.FOOD)
                .spentAt(LocalDateTime.of(2026, 8, 1, 12, 0))
                .build();
        em.persist(expense);
        em.persist(ExpenseShare.builder()
                .expense(expense)
                .participant(ownerParticipant)
                .shareAmount(new BigDecimal("10000"))
                .build());
        em.flush();

        assertFalse(expenseRepository.findAllByGroupId(groupId).isEmpty());
        assertFalse(expenseShareRepository.findAllByExpense_GroupId(groupId).isEmpty());
        assertTrue(budgetRepository.existsByGroup_Id(groupId));
        assertFalse(scheduleRepository.findAllByGroupId(groupId).isEmpty());
        assertFalse(groupParticipantRepository.findAllByGroupId(groupId).isEmpty());

        groupService.delete(groupId, owner.getId());
        em.flush();
        em.clear();

        assertFalse(groupRepository.existsById(groupId));
        assertTrue(expenseRepository.findAllByGroupId(groupId).isEmpty());
        assertTrue(expenseShareRepository.findAllByExpense_GroupId(groupId).isEmpty());
        assertFalse(budgetRepository.existsByGroup_Id(groupId));
        assertTrue(scheduleRepository.findAllByGroupId(groupId).isEmpty());
        assertTrue(groupParticipantRepository.findAllByGroupId(groupId).isEmpty());
    }

    @Test
    @DisplayName("모임 삭제 — 비OWNER 403")
    void delete_denied() {
        Long groupId = groupService.create(owner.getId(), req("모임", null)).getGroupId();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> groupService.delete(groupId, other.getId()));
        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
        assertTrue(groupRepository.existsById(groupId));
    }

    @Test
    @DisplayName("모임 삭제 — 없는 groupId 404")
    void delete_notFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> groupService.delete(999_999L, owner.getId()));
        assertEquals(ErrorCode.ENTITY_NOT_FOUND, ex.getErrorCode());
    }
}
