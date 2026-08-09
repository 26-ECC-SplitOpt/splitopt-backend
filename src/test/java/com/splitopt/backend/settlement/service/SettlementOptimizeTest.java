package com.splitopt.backend.settlement.service;

import com.splitopt.backend.expense.domain.Expense;
import com.splitopt.backend.expense.domain.ExpenseCategory;
import com.splitopt.backend.expense.domain.ExpenseShare;
import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.group.domain.Group;
import com.splitopt.backend.group.domain.GroupParticipant;
import com.splitopt.backend.settlement.domain.ParticipantBalance;
import com.splitopt.backend.settlement.domain.SettlementStatus;
import com.splitopt.backend.settlement.dto.SettlementResponse;
import com.splitopt.backend.settlement.dto.SettlementStatusChangeRequest.Action;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 정산 최적화 실행(API 24) 통합 테스트 — 지출 원장에서 송금 목록까지.
 *
 * <p>가장 중요한 회귀는 <b>재실행 시 이중 청구</b>다. 이미 오간 돈(SENT·COMPLETED)을 상계하지
 * 않은 채 재최적화하면 같은 빚을 두 번 청구하게 되므로, 재실행 후 남은 청구액이 실제 미정산액과
 * 일치하는지를 매번 확인한다.
 */
@SpringBootTest
@Transactional
class SettlementOptimizeTest {

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
        p1 = participant(group, u1, GroupParticipant.Role.OWNER);
        p2 = participant(group, u2, GroupParticipant.Role.MEMBER);
        p3 = participant(group, u3, GroupParticipant.Role.MEMBER);
        em.flush();
    }

    private GroupParticipant participant(Group g, User u, GroupParticipant.Role role) {
        GroupParticipant p = GroupParticipant.builder().group(g).user(u).role(role).build();
        em.persist(p);
        return p;
    }

    private void expense(GroupParticipant payer, String amount, Map<GroupParticipant, String> shares) {
        Expense expense = Expense.builder()
                .group(payer.getGroup())
                .payer(payer)
                .title("지출")
                .amount(new BigDecimal(amount))
                .category(ExpenseCategory.ETC)
                .spentAt(LocalDateTime.now())
                .build();
        em.persist(expense);
        shares.forEach((participant, shareAmount) -> em.persist(ExpenseShare.builder()
                .expense(expense)
                .participant(participant)
                .shareAmount(new BigDecimal(shareAmount))
                .build()));
        em.flush();
    }

    /** 지금 미정산(PENDING)으로 청구 중인 총액. 이중 청구를 잡는 기준값. */
    private BigDecimal pendingTotal() {
        return settlementRepository.findByGroup_IdAndStatus(group.getId(), SettlementStatus.PENDING).stream()
                .map(s -> s.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private SettlementResponse settlementFrom(List<SettlementResponse> settlements, GroupParticipant sender) {
        return settlements.stream()
                .filter(s -> s.fromParticipantId().equals(sender.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("송금 건이 없다: " + sender.getId()));
    }

    private void complete(SettlementResponse s) {
        settlementService.changeStatus(group.getId(), s.id(), Action.SEND, s.fromParticipantId());
        settlementService.changeStatus(group.getId(), s.id(), Action.CONFIRM, s.toParticipantId());
    }

    @Test
    @DisplayName("최적화(24): 지출 원장에서 채무자→채권자 송금 목록이 PENDING으로 생성된다")
    void optimizeCreatesTransfersFromLedger() {
        expense(p1, "30000", Map.of(p1, "10000", p2, "10000", p3, "10000"));

        List<SettlementResponse> result = settlementService.optimize(group.getId());

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(s -> s.status().equals("PENDING")));
        assertTrue(result.stream().allMatch(s -> s.toParticipantId().equals(p1.getId())),
                "돈을 더 낸 p1이 모두 받는다");
        assertEquals(0, new BigDecimal("20000").compareTo(pendingTotal()));
    }

    @Test
    @DisplayName("최적화(24): 지출이 없으면 생성되는 정산도 없다")
    void optimizeWithoutExpensesCreatesNothing() {
        List<SettlementResponse> result = settlementService.optimize(group.getId());

        assertTrue(result.isEmpty());
        assertEquals(0, settlementRepository.countByGroup_Id(group.getId()));
    }

    @Test
    @DisplayName("최적화(24): 서로 낸 금액이 같으면 송금이 필요 없다")
    void evenLedgerNeedsNoTransfer() {
        expense(p1, "20000", Map.of(p1, "10000", p2, "10000"));
        expense(p2, "20000", Map.of(p1, "10000", p2, "10000"));

        assertTrue(settlementService.optimize(group.getId()).isEmpty());
    }

    @Test
    @DisplayName("재실행(이슈 #6): 완료된 송금은 다시 청구되지 않는다")
    void rerunDoesNotRebillCompletedTransfer() {
        expense(p1, "30000", Map.of(p1, "10000", p2, "10000", p3, "10000"));
        List<SettlementResponse> first = settlementService.optimize(group.getId());
        complete(settlementFrom(first, p2));

        List<SettlementResponse> second = settlementService.optimize(group.getId());

        assertEquals(1, second.size(), "아직 안 낸 p3의 1건만 남아야 한다");
        assertEquals(p3.getId(), second.get(0).fromParticipantId());
        assertEquals(0, new BigDecimal("10000").compareTo(pendingTotal()),
                "완료분까지 다시 청구하면 20000이 되어 이중 청구다");
        assertEquals(1, settlementRepository.countByGroup_IdAndStatus(group.getId(), SettlementStatus.COMPLETED));
    }

    @Test
    @DisplayName("재실행(이슈 #6): 송금 완료(SENT) 상태도 확인 전이지만 이미 오간 돈이라 재청구하지 않는다")
    void rerunDoesNotRebillSentTransfer() {
        expense(p1, "30000", Map.of(p1, "10000", p2, "10000", p3, "10000"));
        List<SettlementResponse> first = settlementService.optimize(group.getId());
        SettlementResponse fromP2 = settlementFrom(first, p2);
        settlementService.changeStatus(group.getId(), fromP2.id(), Action.SEND, p2.getId());

        List<SettlementResponse> second = settlementService.optimize(group.getId());

        assertEquals(1, second.size());
        assertEquals(p3.getId(), second.get(0).fromParticipantId());
        assertEquals(1, settlementRepository.countByGroup_IdAndStatus(group.getId(), SettlementStatus.SENT),
                "SENT 건은 보존된다");
        assertEquals(0, new BigDecimal("10000").compareTo(pendingTotal()));
    }

    @Test
    @DisplayName("재실행(이슈 #6): 전부 완료된 뒤 재실행하면 새 청구가 없다")
    void rerunAfterFullSettlementCreatesNothing() {
        expense(p1, "30000", Map.of(p1, "10000", p2, "10000", p3, "10000"));
        settlementService.optimize(group.getId()).forEach(this::complete);

        List<SettlementResponse> rerun = settlementService.optimize(group.getId());

        assertTrue(rerun.isEmpty());
        assertEquals(0, pendingTotal().signum());
        assertEquals(2, settlementRepository.countByGroup_IdAndStatus(group.getId(), SettlementStatus.COMPLETED));
    }

    @Test
    @DisplayName("재실행: 정산 후 새 지출이 생기면 그 차액만 추가로 청구된다")
    void rerunAfterNewExpenseBillsOnlyTheDifference() {
        expense(p1, "30000", Map.of(p1, "10000", p2, "10000", p3, "10000"));
        settlementService.optimize(group.getId()).forEach(this::complete);

        expense(p1, "9000", Map.of(p1, "3000", p2, "3000", p3, "3000"));
        List<SettlementResponse> rerun = settlementService.optimize(group.getId());

        assertEquals(2, rerun.size());
        assertEquals(0, new BigDecimal("6000").compareTo(pendingTotal()),
                "새 지출로 생긴 6000만 청구되어야 한다");
        assertTrue(rerun.stream().allMatch(s -> s.amount().compareTo(new BigDecimal("3000")) == 0));
    }

    @Test
    @DisplayName("재실행: 취소(CANCEL)된 송금은 다시 청구 대상이 된다")
    void cancelledTransferIsBilledAgain() {
        expense(p1, "30000", Map.of(p1, "10000", p2, "10000", p3, "10000"));
        List<SettlementResponse> first = settlementService.optimize(group.getId());
        SettlementResponse fromP2 = settlementFrom(first, p2);
        settlementService.changeStatus(group.getId(), fromP2.id(), Action.SEND, p2.getId());
        settlementService.changeStatus(group.getId(), fromP2.id(), Action.CANCEL, p2.getId());

        settlementService.optimize(group.getId());

        assertEquals(0, new BigDecimal("20000").compareTo(pendingTotal()));
    }

    @Test
    @DisplayName("소속 검증(이슈 #7): 다른 모임 참여자가 섞인 잔액은 400(INVALID_INPUT)으로 거부된다")
    void rejectsParticipantFromAnotherGroup() {
        User other = User.builder().email("d@x.com").password("p").name("D").build();
        em.persist(other);
        Group otherGroup = Group.builder().name("동아리").owner(other).build();
        em.persist(otherGroup);
        GroupParticipant outsider = participant(otherGroup, other, GroupParticipant.Role.OWNER);
        em.flush();

        List<ParticipantBalance> balances = List.of(
                new ParticipantBalance(p1.getId(), new BigDecimal("10000")),
                new ParticipantBalance(outsider.getId(), new BigDecimal("-10000")));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> settlementService.optimizeAndSave(group.getId(), balances));
        assertEquals(ErrorCode.INVALID_INPUT, ex.getErrorCode());
        assertTrue(ex.getMessage().contains(String.valueOf(outsider.getId())));
    }

    @Test
    @DisplayName("소속 검증(이슈 #7): 존재하지 않는 참여자 id도 거부된다 — 기존 PENDING은 지워지지 않는다")
    void rejectsUnknownParticipantWithoutDeletingExisting() {
        expense(p1, "30000", Map.of(p1, "10000", p2, "10000", p3, "10000"));
        settlementService.optimize(group.getId());
        long before = settlementRepository.countByGroup_IdAndStatus(group.getId(), SettlementStatus.PENDING);

        List<ParticipantBalance> balances = List.of(
                new ParticipantBalance(p1.getId(), new BigDecimal("10000")),
                new ParticipantBalance(p3.getId() + 999, new BigDecimal("-10000")));

        assertThrows(BusinessException.class, () -> settlementService.optimizeAndSave(group.getId(), balances));
        assertEquals(before,
                settlementRepository.countByGroup_IdAndStatus(group.getId(), SettlementStatus.PENDING),
                "검증은 삭제 전에 끝나야 한다");
    }

    @Test
    @DisplayName("소속 검증(이슈 #7): 탈퇴한 참여자는 정산 대상으로 남는다")
    void withdrawnParticipantStillSettleable() {
        expense(p1, "30000", Map.of(p1, "10000", p2, "10000", p3, "10000"));
        p3.deactivate();
        em.flush();

        List<SettlementResponse> result = settlementService.optimize(group.getId());

        assertTrue(result.stream().anyMatch(s -> s.fromParticipantId().equals(p3.getId())),
                "탈퇴해도 갚아야 할 돈은 청구된다");
    }

    @Test
    @DisplayName("생성된 정산의 모임·참여자는 경로의 모임과 일치한다")
    void savedSettlementsBelongToRequestedGroup() {
        expense(p1, "30000", Map.of(p1, "10000", p2, "10000", p3, "10000"));

        settlementService.optimize(group.getId());

        settlementRepository.findByGroup_Id(group.getId()).forEach(s -> {
            assertEquals(group.getId(), s.getGroup().getId());
            assertEquals(group.getId(), s.getFromParticipant().getGroup().getId());
            assertEquals(group.getId(), s.getToParticipant().getGroup().getId());
        });
    }
}
