package com.splitopt.backend.settlement.service;

import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.group.domain.Group;
import com.splitopt.backend.group.domain.GroupParticipant;
import com.splitopt.backend.settlement.domain.ParticipantBalance;
import com.splitopt.backend.settlement.domain.SettlementStatus;
import com.splitopt.backend.settlement.dto.MySettlementsResponse;
import com.splitopt.backend.settlement.dto.SettlementResponse;
import com.splitopt.backend.settlement.dto.SettlementStatusChangeRequest.Action;
import com.splitopt.backend.settlement.dto.SettlementSummaryResponse;
import com.splitopt.backend.settlement.repository.SettlementRepository;
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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class SettlementServiceTest {

    @PersistenceContext
    private EntityManager em;
    @Autowired
    private SettlementService settlementService;
    @Autowired
    private SettlementRepository settlementRepository;

    private Group group;
    private GroupParticipant p1;
    private GroupParticipant p2;
    private GroupParticipant p3;

    @BeforeEach
    void setUp() {
        User u1 = User.builder().email("a@x.com").password("p").name("A").build();
        User u2 = User.builder().email("b@x.com").password("p").name("B").build();
        User u3 = User.builder().email("c@x.com").password("p").name("C").build();
        em.persist(u1);
        em.persist(u2);
        em.persist(u3);
        group = Group.builder().name("제주여행").owner(u1).build();
        em.persist(group);
        p1 = GroupParticipant.builder().group(group).user(u1).role(GroupParticipant.Role.OWNER).build();
        p2 = GroupParticipant.builder().group(group).user(u2).role(GroupParticipant.Role.MEMBER).build();
        p3 = GroupParticipant.builder().group(group).user(u3).role(GroupParticipant.Role.MEMBER).build();
        em.persist(p1);
        em.persist(p2);
        em.persist(p3);
        em.flush();
    }

    private ParticipantBalance bal(GroupParticipant p, String amount) {
        return new ParticipantBalance(p.getId(), new BigDecimal(amount));
    }

    /** 전이 헬퍼: 보내는 사람(from)이 SEND → 받는 사람(to)이 CONFIRM 하여 COMPLETED로 만든다. */
    private SettlementResponse completeFully(SettlementResponse s) {
        settlementService.changeStatus(group.getId(), s.settlementId(), Action.SEND, s.fromParticipantId());
        return settlementService.changeStatus(group.getId(), s.settlementId(), Action.CONFIRM, s.toParticipantId());
    }

    @Test
    @DisplayName("최적화 실행 시 송금 목록이 PENDING 상태로 저장된다")
    void optimizeAndSavePersistsTransfers() {
        List<ParticipantBalance> balances = List.of(
                bal(p1, "120000"), bal(p2, "-40000"), bal(p3, "-80000"));

        List<SettlementResponse> result = settlementService.optimizeAndSave(group.getId(), balances);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(r -> r.status().equals("PENDING")));
        assertTrue(result.stream().allMatch(r -> r.toParticipantId().equals(p1.getId())));
        BigDecimal received = result.stream().map(SettlementResponse::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, received.compareTo(new BigDecimal("120000")));
        assertEquals(2, settlementRepository.countByGroup_Id(group.getId()));
    }

    @Test
    @DisplayName("재실행 시 기존 PENDING은 삭제되고 COMPLETED는 보존된다")
    void rerunDeletesPendingKeepsCompleted() {
        List<ParticipantBalance> balances = List.of(bal(p1, "50000"), bal(p2, "-50000"));
        List<SettlementResponse> first = settlementService.optimizeAndSave(group.getId(), balances);
        completeFully(first.get(0));

        settlementService.optimizeAndSave(group.getId(), balances);

        long completed = settlementRepository.countByGroup_IdAndStatus(group.getId(), SettlementStatus.COMPLETED);
        long pending = settlementRepository.countByGroup_IdAndStatus(group.getId(), SettlementStatus.PENDING);
        assertEquals(1, completed, "완료된 건은 보존");
        assertEquals(1, pending, "PENDING은 삭제 후 재생성 1건");
    }

    @Test
    @DisplayName("SEND: 보내는 사람이 송금 완료하면 SENT·송금시각 기록")
    void sendMarksSentAndTime() {
        SettlementResponse s = settlementService.optimizeAndSave(
                group.getId(), List.of(bal(p1, "10000"), bal(p2, "-10000"))).get(0);

        SettlementResponse sent = settlementService.changeStatus(
                group.getId(), s.settlementId(), Action.SEND, s.fromParticipantId());

        assertEquals("SENT", sent.status());
        assertNotNull(sent.sentAt());
        assertNull(sent.completedAt());
    }

    @Test
    @DisplayName("CONFIRM: 받는 사람이 확인하면 COMPLETED·완료시각 기록")
    void confirmMarksCompleted() {
        SettlementResponse s = settlementService.optimizeAndSave(
                group.getId(), List.of(bal(p1, "10000"), bal(p2, "-10000"))).get(0);
        settlementService.changeStatus(group.getId(), s.settlementId(), Action.SEND, s.fromParticipantId());

        SettlementResponse completed = settlementService.changeStatus(
                group.getId(), s.settlementId(), Action.CONFIRM, s.toParticipantId());

        assertEquals("COMPLETED", completed.status());
        assertNotNull(completed.completedAt());
    }

    @Test
    @DisplayName("CANCEL: 확인 전(SENT) 보내는 사람이 취소하면 PENDING·송금시각 복원")
    void cancelRestoresPending() {
        SettlementResponse s = settlementService.optimizeAndSave(
                group.getId(), List.of(bal(p1, "10000"), bal(p2, "-10000"))).get(0);
        settlementService.changeStatus(group.getId(), s.settlementId(), Action.SEND, s.fromParticipantId());

        SettlementResponse cancelled = settlementService.changeStatus(
                group.getId(), s.settlementId(), Action.CANCEL, s.fromParticipantId());

        assertEquals("PENDING", cancelled.status());
        assertNull(cancelled.sentAt());
    }

    @Test
    @DisplayName("권한: SEND는 보내는 사람만 — 받는 사람이 하면 403(ACCESS_DENIED)")
    void sendByNonSenderForbidden() {
        SettlementResponse s = settlementService.optimizeAndSave(
                group.getId(), List.of(bal(p1, "10000"), bal(p2, "-10000"))).get(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> settlementService.changeStatus(group.getId(), s.settlementId(), Action.SEND, s.toParticipantId()));
        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
    }

    @Test
    @DisplayName("권한: CONFIRM은 받는 사람만 — 보내는 사람이 하면 403(ACCESS_DENIED)")
    void confirmByNonReceiverForbidden() {
        SettlementResponse s = settlementService.optimizeAndSave(
                group.getId(), List.of(bal(p1, "10000"), bal(p2, "-10000"))).get(0);
        settlementService.changeStatus(group.getId(), s.settlementId(), Action.SEND, s.fromParticipantId());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> settlementService.changeStatus(group.getId(), s.settlementId(), Action.CONFIRM, s.fromParticipantId()));
        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
    }

    @Test
    @DisplayName("권한: CANCEL은 보내는 사람만 — 받는 사람이 하면 403이고 상태는 SENT 유지")
    void cancelByNonSenderForbidden() {
        SettlementResponse s = settlementService.optimizeAndSave(
                group.getId(), List.of(bal(p1, "10000"), bal(p2, "-10000"))).get(0);
        settlementService.changeStatus(group.getId(), s.settlementId(), Action.SEND, s.fromParticipantId());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> settlementService.changeStatus(group.getId(), s.settlementId(), Action.CANCEL, s.toParticipantId()));
        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());

        // 거부됐으므로 상태는 SENT 그대로여야 함(PENDING으로 되돌아가지 않음)
        assertEquals(1, settlementService.getByStatus(group.getId(), SettlementStatus.SENT).size());
        assertTrue(settlementService.getByStatus(group.getId(), SettlementStatus.PENDING).isEmpty());
    }

    @Test
    @DisplayName("상태충돌: PENDING에서 CONFIRM(송금 전 확인)은 409(INVALID_STATE)")
    void confirmBeforeSendConflict() {
        SettlementResponse s = settlementService.optimizeAndSave(
                group.getId(), List.of(bal(p1, "10000"), bal(p2, "-10000"))).get(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> settlementService.changeStatus(group.getId(), s.settlementId(), Action.CONFIRM, s.toParticipantId()));
        assertEquals(ErrorCode.INVALID_STATE, ex.getErrorCode());
    }

    @Test
    @DisplayName("상태충돌: COMPLETED 건의 CANCEL은 409(INVALID_STATE)")
    void cancelCompletedConflict() {
        SettlementResponse s = settlementService.optimizeAndSave(
                group.getId(), List.of(bal(p1, "10000"), bal(p2, "-10000"))).get(0);
        completeFully(s);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> settlementService.changeStatus(group.getId(), s.settlementId(), Action.CANCEL, s.fromParticipantId()));
        assertEquals(ErrorCode.INVALID_STATE, ex.getErrorCode());
    }

    @Test
    @DisplayName("다른 모임 id로는 상태를 변경할 수 없다(ENTITY_NOT_FOUND)")
    void cannotChangeStatusFromAnotherGroup() {
        SettlementResponse s = settlementService.optimizeAndSave(
                group.getId(), List.of(bal(p1, "10000"), bal(p2, "-10000"))).get(0);
        Long otherGroupId = group.getId() + 999;
        BusinessException ex = assertThrows(BusinessException.class,
                () -> settlementService.changeStatus(otherGroupId, s.settlementId(), Action.SEND, s.fromParticipantId()));
        assertEquals(ErrorCode.ENTITY_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("상태충돌: 이미 SENT인 건에 다시 SEND하면 409(INVALID_STATE)")
    void sendTwiceConflict() {
        SettlementResponse s = settlementService.optimizeAndSave(
                group.getId(), List.of(bal(p1, "10000"), bal(p2, "-10000"))).get(0);
        settlementService.changeStatus(group.getId(), s.settlementId(), Action.SEND, s.fromParticipantId());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> settlementService.changeStatus(group.getId(), s.settlementId(), Action.SEND, s.fromParticipantId()));
        assertEquals(ErrorCode.INVALID_STATE, ex.getErrorCode());
    }

    @Test
    @DisplayName("요약: 일부만 완료면 IN_PROGRESS·완료율·allCompleted false")
    void summaryCountsAndRate() {
        List<SettlementResponse> saved = settlementService.optimizeAndSave(
                group.getId(), List.of(bal(p1, "120000"), bal(p2, "-40000"), bal(p3, "-80000")));
        completeFully(saved.get(0));

        SettlementSummaryResponse summary = settlementService.getSummary(group.getId());

        assertEquals(2, summary.total());
        assertEquals(1, summary.completed());
        assertEquals(1, summary.pending());
        assertFalse(summary.allCompleted());
        assertEquals(0.5, summary.completionRate(), 0.0001);
        assertEquals(SettlementSummaryResponse.Status.IN_PROGRESS, summary.status());
    }

    @Test
    @DisplayName("요약: 모든 정산이 COMPLETED면 DONE·allCompleted true")
    void summaryAllCompletedIsDone() {
        List<SettlementResponse> saved = settlementService.optimizeAndSave(
                group.getId(), List.of(bal(p1, "10000"), bal(p2, "-10000")));
        completeFully(saved.get(0));

        SettlementSummaryResponse summary = settlementService.getSummary(group.getId());

        assertEquals(1, summary.total());
        assertEquals(1, summary.completed());
        assertEquals(0, summary.pending());
        assertTrue(summary.allCompleted());
        assertEquals(1.0, summary.completionRate(), 0.0001);
        assertEquals(SettlementSummaryResponse.Status.DONE, summary.status());
    }

    @Test
    @DisplayName("정산을 한 번도 안 돌린 모임은 NOT_STARTED('정산 전')·완료 아님")
    void summaryEmptyIsNotStarted() {
        SettlementSummaryResponse summary = settlementService.getSummary(group.getId());
        assertEquals(0, summary.total());
        assertFalse(summary.allCompleted());
        assertEquals(0.0, summary.completionRate(), 0.0001);
        assertEquals(SettlementSummaryResponse.Status.NOT_STARTED, summary.status());
    }

    @Test
    @DisplayName("미정산 조회는 PENDING만 반환한다")
    void getPendingReturnsOnlyPending() {
        List<SettlementResponse> saved = settlementService.optimizeAndSave(
                group.getId(), List.of(bal(p1, "120000"), bal(p2, "-40000"), bal(p3, "-80000")));
        completeFully(saved.get(0));

        List<SettlementResponse> pending =
                settlementService.getByStatus(group.getId(), SettlementStatus.PENDING);
        assertEquals(1, pending.size());
        assertEquals("PENDING", pending.get(0).status());
    }

    @Test
    @DisplayName("내 정산(26): 받는 사람은 미완료 건이 toReceive에, 방향 RECEIVE로 분류된다")
    void mineReceiverGoesToReceive() {
        // p1(수령자) ← p2, p3(송금자) : p1 관점 두 건 모두 받을 내역
        settlementService.optimizeAndSave(
                group.getId(), List.of(bal(p1, "120000"), bal(p2, "-40000"), bal(p3, "-80000")));

        MySettlementsResponse mine = settlementService.getMySettlements(group.getId(), p1.getUser().getId());

        assertEquals(2, mine.toReceive().size());
        assertTrue(mine.toSend().isEmpty());
        assertTrue(mine.completed().isEmpty());
        assertTrue(mine.toReceive().stream()
                .allMatch(i -> i.direction() == MySettlementsResponse.Direction.RECEIVE));
        assertTrue(mine.toReceive().stream().allMatch(i -> i.status().equals("PENDING")));
        // 상대방 이름은 송금자(B, C)
        assertEquals(Set.of("B", "C"),
                mine.toReceive().stream().map(MySettlementsResponse.Item::counterpartName)
                        .collect(Collectors.toSet()));
    }

    @Test
    @DisplayName("내 정산(26): 보내는 사람은 toSend에, 방향 SEND·상대는 수령자 이름")
    void mineSenderGoesToSend() {
        settlementService.optimizeAndSave(
                group.getId(), List.of(bal(p1, "120000"), bal(p2, "-40000"), bal(p3, "-80000")));

        MySettlementsResponse mine = settlementService.getMySettlements(group.getId(), p2.getUser().getId());

        assertEquals(1, mine.toSend().size());
        assertTrue(mine.toReceive().isEmpty());
        MySettlementsResponse.Item item = mine.toSend().get(0);
        assertEquals(MySettlementsResponse.Direction.SEND, item.direction());
        assertEquals("A", item.counterpartName());
        assertEquals("PENDING", item.status());
    }

    @Test
    @DisplayName("내 정산(26): SENT는 아직 미완료라 받는 사람의 toReceive에 SENT로 보인다")
    void mineSentStaysInToReceive() {
        SettlementResponse s = settlementService.optimizeAndSave(
                group.getId(), List.of(bal(p1, "10000"), bal(p2, "-10000"))).get(0);
        settlementService.changeStatus(group.getId(), s.settlementId(), Action.SEND, s.fromParticipantId());

        MySettlementsResponse receiver = settlementService.getMySettlements(group.getId(), p1.getUser().getId());
        assertEquals(1, receiver.toReceive().size());
        assertEquals("SENT", receiver.toReceive().get(0).status());
        assertTrue(receiver.completed().isEmpty());
    }

    @Test
    @DisplayName("내 정산(26): COMPLETED는 방향과 무관하게 양측 completed로 분류된다")
    void mineCompletedGoesToCompleted() {
        SettlementResponse s = settlementService.optimizeAndSave(
                group.getId(), List.of(bal(p1, "10000"), bal(p2, "-10000"))).get(0);
        completeFully(s);

        MySettlementsResponse sender = settlementService.getMySettlements(group.getId(), p2.getUser().getId());
        MySettlementsResponse receiver = settlementService.getMySettlements(group.getId(), p1.getUser().getId());

        assertEquals(1, sender.completed().size());
        assertTrue(sender.toSend().isEmpty());
        assertEquals(MySettlementsResponse.Direction.SEND, sender.completed().get(0).direction());

        assertEquals(1, receiver.completed().size());
        assertTrue(receiver.toReceive().isEmpty());
        assertEquals(MySettlementsResponse.Direction.RECEIVE, receiver.completed().get(0).direction());
    }

    @Test
    @DisplayName("내 정산(26): 정산이 없으면 세 목록 모두 비어 있다")
    void mineEmptyWhenNoSettlements() {
        MySettlementsResponse mine = settlementService.getMySettlements(group.getId(), p1.getUser().getId());
        assertTrue(mine.toSend().isEmpty());
        assertTrue(mine.toReceive().isEmpty());
        assertTrue(mine.completed().isEmpty());
    }
}
