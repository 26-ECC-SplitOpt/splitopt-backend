package com.splitopt.backend.group.service;

import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.group.domain.GroupParticipant;
import com.splitopt.backend.group.dto.AddParticipantRequest;
import com.splitopt.backend.group.dto.AddParticipantResponse;
import com.splitopt.backend.group.dto.CreateGroupRequest;
import com.splitopt.backend.group.dto.GroupResponse;
import com.splitopt.backend.group.repository.GroupParticipantRepository;
import com.splitopt.backend.expense.domain.ExpenseCategory;
import com.splitopt.backend.settlement.service.SettlementService;
import com.splitopt.backend.settlement.dto.SettlementStatusChangeRequest;
import com.splitopt.backend.settlement.dto.SettlementResponse;
import com.splitopt.backend.expense.dto.ExpenseCreateRequest;
import com.splitopt.backend.expense.service.ExpenseService;
import com.splitopt.backend.user.domain.User;
import com.splitopt.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class ParticipantServiceTest {

    @Autowired
    private ParticipantService participantService;
    @Autowired
    private GroupService groupService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private GroupParticipantRepository groupParticipantRepository;
    @Autowired
    private ExpenseService expenseService;
    @Autowired
    private SettlementService settlementService;

    private User owner;
    private User member;
    private User other;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(User.builder()
                .email("owner-" + System.nanoTime() + "@ex.com")
                .password("password")
                .name("소유자")
                .build());
        member = userRepository.save(User.builder()
                .email("member-" + System.nanoTime() + "@ex.com")
                .password("password")
                .name("김철수")
                .build());
        other = userRepository.save(User.builder()
                .email("other-" + System.nanoTime() + "@ex.com")
                .password("password")
                .name("다른유저")
                .build());
    }

    private CreateGroupRequest groupReq(String name) {
        CreateGroupRequest r = new CreateGroupRequest();
        ReflectionTestUtils.setField(r, "name", name);
        ReflectionTestUtils.setField(r, "description", null);
        return r;
    }

    private AddParticipantRequest addReq(Long userId) {
        AddParticipantRequest r = new AddParticipantRequest();
        ReflectionTestUtils.setField(r, "userId", userId);
        return r;
    }

    @Test
    @DisplayName("참여자 추가 — OWNER가 가입 유저를 MEMBER로 등록")
    void add_ok() {
        GroupResponse group = groupService.create(owner.getId(), groupReq("강릉"));

        AddParticipantResponse res =
                participantService.add(group.getGroupId(), owner.getId(), addReq(member.getId()));

        assertNotNull(res.getParticipantId());
        assertEquals(member.getId(), res.getUserId());
        assertEquals("김철수", res.getName());
        assertEquals("MEMBER", res.getRole());
        assertNotNull(res.getJoinedAt());
    }

    @Test
    @DisplayName("참여자 추가 — MEMBER는 403")
    void add_byMember_denied() {
        Long groupId = groupService.create(owner.getId(), groupReq("모임")).getGroupId();
        participantService.add(groupId, owner.getId(), addReq(member.getId()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> participantService.add(groupId, member.getId(), addReq(other.getId())));
        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
    }

    @Test
    @DisplayName("참여자 추가 — 비참여자 403")
    void add_denied() {
        Long groupId = groupService.create(owner.getId(), groupReq("모임")).getGroupId();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> participantService.add(groupId, other.getId(), addReq(member.getId())));
        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
    }

    @Test
    @DisplayName("참여자 추가 — 이미 활성 참여자면 409")
    void add_duplicate() {
        Long groupId = groupService.create(owner.getId(), groupReq("모임")).getGroupId();
        participantService.add(groupId, owner.getId(), addReq(member.getId()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> participantService.add(groupId, owner.getId(), addReq(member.getId())));
        assertEquals(ErrorCode.INVALID_STATE, ex.getErrorCode());
    }

    @Test
    @DisplayName("참여자 추가 — soft-delete 후 재추가 시 재활성화")
    void add_reactivate() {
        Long groupId = groupService.create(owner.getId(), groupReq("모임")).getGroupId();
        AddParticipantResponse first =
                participantService.add(groupId, owner.getId(), addReq(member.getId()));

        GroupParticipant row = groupParticipantRepository.findById(first.getParticipantId()).orElseThrow();
        row.deactivate();

        AddParticipantResponse again =
                participantService.add(groupId, owner.getId(), addReq(member.getId()));

        assertEquals(first.getParticipantId(), again.getParticipantId());
        assertEquals("MEMBER", again.getRole());
        assertTrue(groupParticipantRepository.findById(again.getParticipantId()).orElseThrow().isActive());
    }

    @Test
    @DisplayName("참여자 추가 — 없는 userId 404")
    void add_userNotFound() {
        Long groupId = groupService.create(owner.getId(), groupReq("모임")).getGroupId();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> participantService.add(groupId, owner.getId(), addReq(999_999L)));
        assertEquals(ErrorCode.ENTITY_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("참여자 추가 — 없는 groupId 404")
    void add_groupNotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> participantService.add(999_999L, owner.getId(), addReq(member.getId())));
        assertEquals(ErrorCode.ENTITY_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("참여자 목록 — 활성 멤버만, OWNER 포함")
    void list_ok() {
        Long groupId = groupService.create(owner.getId(), groupReq("모임")).getGroupId();
        participantService.add(groupId, owner.getId(), addReq(member.getId()));

        var list = participantService.list(groupId, owner.getId());

        assertEquals(2, list.size());
        assertTrue(list.stream().anyMatch(p -> "OWNER".equals(p.getRole())));
        assertTrue(list.stream().anyMatch(p -> member.getId().equals(p.getUserId())));
        assertNotNull(list.get(0).getParticipantId());
    }

    @Test
    @DisplayName("참여자 목록 — 비참여자 403")
    void list_denied() {
        Long groupId = groupService.create(owner.getId(), groupReq("모임")).getGroupId();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> participantService.list(groupId, other.getId()));
        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
    }

    @Test
    @DisplayName("참여자 삭제 — OWNER가 MEMBER soft-delete")
    void remove_ok() {
        Long groupId = groupService.create(owner.getId(), groupReq("모임")).getGroupId();
        AddParticipantResponse added =
                participantService.add(groupId, owner.getId(), addReq(member.getId()));

        var res = participantService.remove(groupId, owner.getId(), member.getId());

        assertEquals("참여자가 모임에서 제외되었습니다.", res.getMessage());
        GroupParticipant row = groupParticipantRepository.findById(added.getParticipantId()).orElseThrow();
        assertFalse(row.isActive());
        assertNotNull(row.getLeftAt());
        assertEquals(1, participantService.list(groupId, owner.getId()).size());
    }

    @Test
    @DisplayName("참여자 삭제 — 미정산 채무 있으면 409")
    void remove_unsettledBalance() {
        Long groupId = groupService.create(owner.getId(), groupReq("모임")).getGroupId();
        AddParticipantResponse added =
                participantService.add(groupId, owner.getId(), addReq(member.getId()));

        Long ownerParticipantId = groupParticipantRepository
                .findByGroupIdAndUserId(groupId, owner.getId()).orElseThrow().getId();
        expenseService.createExpense(groupId, ownerParticipantId,
                new ExpenseCreateRequest(
                        "점심",
                        new BigDecimal("10000"),
                        ExpenseCategory.FOOD,
                        null,
                        LocalDate.now(),
                        null,
                        ExpenseCreateRequest.SplitMethod.EQUAL,
                        List.of(
                                new ExpenseCreateRequest.ShareInput(ownerParticipantId, null),
                                new ExpenseCreateRequest.ShareInput(added.getParticipantId(), null)
                        )));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> participantService.remove(groupId, owner.getId(), member.getId()));
        assertEquals(ErrorCode.INVALID_STATE, ex.getErrorCode());
        assertTrue(groupParticipantRepository.findById(added.getParticipantId()).orElseThrow().isActive());
    }

    @Test
    @DisplayName("참여자 삭제 — MEMBER는 403")
    void remove_denied_member() {
        Long groupId = groupService.create(owner.getId(), groupReq("모임")).getGroupId();
        participantService.add(groupId, owner.getId(), addReq(member.getId()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> participantService.remove(groupId, member.getId(), member.getId()));
        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
    }

    @Test
    @DisplayName("참여자 삭제 — OWNER 본인 삭제는 409")
    void remove_ownerForbidden() {
        Long groupId = groupService.create(owner.getId(), groupReq("모임")).getGroupId();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> participantService.remove(groupId, owner.getId(), owner.getId()));
        assertEquals(ErrorCode.INVALID_STATE, ex.getErrorCode());
    }

    @Test
    @DisplayName("참여자 삭제 — 한 번도 참여하지 않은 사용자 404")
    void remove_notFound() {
        Long groupId = groupService.create(owner.getId(), groupReq("모임")).getGroupId();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> participantService.remove(groupId, owner.getId(), other.getId()));
        assertEquals(ErrorCode.ENTITY_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("참여자 삭제 — 이미 탈퇴한 참여자 재삭제 404")
    void remove_alreadyInactive() {
        Long groupId = groupService.create(owner.getId(), groupReq("모임")).getGroupId();
        participantService.add(groupId, owner.getId(), addReq(member.getId()));
        participantService.remove(groupId, owner.getId(), member.getId());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> participantService.remove(groupId, owner.getId(), member.getId()));
        assertEquals(ErrorCode.ENTITY_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("참여자 정산 현황 — 멤버 조회")
    void status_ok() {
        Long groupId = groupService.create(owner.getId(), groupReq("모임")).getGroupId();
        participantService.add(groupId, owner.getId(), addReq(member.getId()));

        var status = participantService.status(groupId, owner.getId(), member.getId());

        assertEquals(member.getId(), status.getUserId());
        assertEquals("김철수", status.getName());
        assertEquals("NOT_STARTED", status.getSettlementStatus());
        assertNotNull(status.getPaidAmount());
        assertNotNull(status.getToSend());
        assertNotNull(status.getToReceive());
    }

    @Test
    @DisplayName("참여자 정산 현황 — 비참여자 403")
    void status_denied() {
        Long groupId = groupService.create(owner.getId(), groupReq("모임")).getGroupId();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> participantService.status(groupId, other.getId(), owner.getId()));
        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
    }

    /** 지출을 만들고, 최적화해 생긴 정산 건들을 돌려준다. */
    private List<SettlementResponse> settlementsAfterExpense(Long groupId, Long ownerParticipantId,
                                                             Long memberParticipantId) {
        expenseService.createExpense(groupId, ownerParticipantId,
                new ExpenseCreateRequest("점심", new BigDecimal("10000"), ExpenseCategory.FOOD, null,
                        LocalDate.now(), null, ExpenseCreateRequest.SplitMethod.EQUAL,
                        List.of(new ExpenseCreateRequest.ShareInput(ownerParticipantId, null),
                                new ExpenseCreateRequest.ShareInput(memberParticipantId, null))));
        return settlementService.optimize(groupId);
    }

    @Test
    @DisplayName("참여자 삭제 — 보냈다고 표시만 한 상태(SENT)로는 나갈 수 없다")
    void remove_blockedWhileSentButNotConfirmed() {
        Long groupId = groupService.create(owner.getId(), groupReq("모임")).getGroupId();
        AddParticipantResponse added =
                participantService.add(groupId, owner.getId(), addReq(member.getId()));
        Long ownerPid = groupParticipantRepository
                .findByGroupIdAndUserId(groupId, owner.getId()).orElseThrow().getId();

        List<SettlementResponse> settlements =
                settlementsAfterExpense(groupId, ownerPid, added.getParticipantId());
        SettlementResponse debt = settlements.get(0);

        // 보내는 사람이 "보냈다"고 표시만 한 상태. 받는 사람은 아직 확인하지 않았고,
        // 이 상태는 취소해서 PENDING으로 되돌릴 수도 있다.
        settlementService.changeStatus(groupId, debt.settlementId(),
                SettlementStatusChangeRequest.Action.SEND, debt.fromParticipantId());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> participantService.remove(groupId, owner.getId(), member.getId()),
                "확인 전인데 나갈 수 있으면, 돈을 안 보내고 표시만 해도 빠져나간다");
        assertEquals(ErrorCode.INVALID_STATE, ex.getErrorCode());
        assertTrue(groupParticipantRepository.findById(added.getParticipantId()).orElseThrow().isActive());
    }

    @Test
    @DisplayName("참여자 삭제 — 받는 사람이 확인(COMPLETED)하면 나갈 수 있다")
    void remove_allowedAfterConfirmed() {
        Long groupId = groupService.create(owner.getId(), groupReq("모임")).getGroupId();
        AddParticipantResponse added =
                participantService.add(groupId, owner.getId(), addReq(member.getId()));
        Long ownerPid = groupParticipantRepository
                .findByGroupIdAndUserId(groupId, owner.getId()).orElseThrow().getId();

        List<SettlementResponse> settlements =
                settlementsAfterExpense(groupId, ownerPid, added.getParticipantId());
        SettlementResponse debt = settlements.get(0);

        settlementService.changeStatus(groupId, debt.settlementId(),
                SettlementStatusChangeRequest.Action.SEND, debt.fromParticipantId());
        settlementService.changeStatus(groupId, debt.settlementId(),
                SettlementStatusChangeRequest.Action.CONFIRM, debt.toParticipantId());

        participantService.remove(groupId, owner.getId(), member.getId());

        assertFalse(groupParticipantRepository.findById(added.getParticipantId()).orElseThrow().isActive());
    }
}
