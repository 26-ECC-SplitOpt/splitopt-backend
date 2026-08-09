package com.splitopt.backend.settlement.service;

import com.splitopt.backend.expense.domain.Expense;
import com.splitopt.backend.expense.domain.ExpenseCategory;
import com.splitopt.backend.expense.domain.ExpenseShare;
import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.group.domain.Group;
import com.splitopt.backend.group.domain.GroupParticipant;
import com.splitopt.backend.settlement.domain.ParticipantBalance;
import com.splitopt.backend.settlement.dto.ParticipantBalanceResponse;
import com.splitopt.backend.settlement.dto.SettlementResponse;
import com.splitopt.backend.settlement.dto.SettlementStatusChangeRequest.Action;
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
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 개인별 잔액(API 23)·순잔액(API 24 입력) 계산 테스트.
 *
 * <p>핵심 관심사는 두 가지다.
 * <ul>
 *   <li>총잔액 = 결제 − 부담이 지출 원장과 맞는가, 활동이 없는 참여자도 빠짐없이 나오는가</li>
 *   <li>순잔액이 <b>이미 오간 돈</b>(SENT·COMPLETED)만 상계하고 PENDING은 건드리지 않는가</li>
 * </ul>
 */
@SpringBootTest
@Transactional
class BalanceServiceTest {

    @PersistenceContext
    private EntityManager em;
    @Autowired
    private BalanceService balanceService;
    @Autowired
    private SettlementService settlementService;

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

    /** 지출 1건 + 부담 내역을 그대로 저장한다(금액 합은 호출자가 맞춘다). */
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

    private Map<Long, ParticipantBalanceResponse> balancesById() {
        return balanceService.getBalances(group.getId()).stream()
                .collect(Collectors.toMap(ParticipantBalanceResponse::participantId, Function.identity()));
    }

    private Map<Long, BigDecimal> netById() {
        return balanceService.getNetBalances(group.getId()).stream()
                .collect(Collectors.toMap(ParticipantBalance::participantId, ParticipantBalance::amount));
    }

    private void assertAmount(String expected, BigDecimal actual) {
        assertAmount(expected, actual, null);
    }

    /** BigDecimal은 scale이 달라도 같은 금액이므로 compareTo로 비교한다. */
    private void assertAmount(String expected, BigDecimal actual, String message) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> (message == null ? "" : message + " — ")
                        + "expected " + expected + " but was " + actual.toPlainString());
    }

    @Test
    @DisplayName("잔액(23): 결제자는 부담분을 뺀 만큼 채권자, 나머지는 부담액만큼 채무자")
    void balanceIsPaidMinusOwed() {
        expense(p1, "30000", Map.of(p1, "10000", p2, "10000", p3, "10000"));

        Map<Long, ParticipantBalanceResponse> balances = balancesById();

        assertAmount("30000", balances.get(p1.getId()).paid());
        assertAmount("10000", balances.get(p1.getId()).owed());
        assertAmount("20000", balances.get(p1.getId()).balance());
        assertAmount("0", balances.get(p2.getId()).paid());
        assertAmount("-10000", balances.get(p2.getId()).balance());
        assertAmount("-10000", balances.get(p3.getId()).balance());
    }

    @Test
    @DisplayName("잔액(23): 지출이 여러 건이면 결제·부담이 각각 누적된다")
    void balanceAccumulatesAcrossExpenses() {
        expense(p1, "30000", Map.of(p1, "10000", p2, "10000", p3, "10000"));
        expense(p2, "60000", Map.of(p1, "20000", p2, "20000", p3, "20000"));

        Map<Long, ParticipantBalanceResponse> balances = balancesById();

        assertAmount("30000", balances.get(p1.getId()).paid());
        assertAmount("30000", balances.get(p1.getId()).owed());
        assertAmount("0", balances.get(p1.getId()).balance());
        assertAmount("60000", balances.get(p2.getId()).paid());
        assertAmount("30000", balances.get(p2.getId()).owed());
        assertAmount("30000", balances.get(p2.getId()).balance());
        assertAmount("-30000", balances.get(p3.getId()).balance());
    }

    @Test
    @DisplayName("잔액(23): 총잔액의 합은 항상 0이다")
    void balancesSumToZero() {
        expense(p1, "41000", Map.of(p1, "13668", p2, "13666", p3, "13666"));
        expense(p3, "7000", Map.of(p2, "3500", p3, "3500"));

        BigDecimal sum = balanceService.getBalances(group.getId()).stream()
                .map(ParticipantBalanceResponse::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(0, sum.signum());
    }

    @Test
    @DisplayName("잔액(23): 지출에 한 번도 등장하지 않은 활성 참여자도 0원으로 포함된다")
    void inactiveInLedgerParticipantsStillListed() {
        expense(p1, "20000", Map.of(p1, "10000", p2, "10000"));

        Map<Long, ParticipantBalanceResponse> balances = balancesById();

        assertEquals(3, balances.size(), "활성 참여자 3명 모두 조회되어야 한다");
        ParticipantBalanceResponse untouched = balances.get(p3.getId());
        assertAmount("0", untouched.paid());
        assertAmount("0", untouched.owed());
        assertAmount("0", untouched.balance());
        assertAmount("0", untouched.netBalance());
        assertTrue(untouched.active());
        assertEquals("C", untouched.name());
    }

    @Test
    @DisplayName("잔액(23): 정산이 없으면 순잔액은 총잔액과 같다")
    void netEqualsGrossWithoutSettlements() {
        expense(p1, "30000", Map.of(p1, "10000", p2, "10000", p3, "10000"));

        balanceService.getBalances(group.getId())
                .forEach(b -> assertEquals(0, b.balance().compareTo(b.netBalance())));
    }

    @Test
    @DisplayName("순잔액: PENDING 정산은 아직 오간 돈이 아니라 상계하지 않는다")
    void pendingSettlementDoesNotAffectNet() {
        expense(p1, "30000", Map.of(p1, "10000", p2, "10000", p3, "10000"));
        settlementService.optimize(group.getId());

        Map<Long, BigDecimal> net = netById();

        assertAmount("20000", net.get(p1.getId()));
        assertAmount("-10000", net.get(p2.getId()));
        assertAmount("-10000", net.get(p3.getId()));
    }

    @Test
    @DisplayName("순잔액: SENT 정산은 이미 보낸 돈이므로 상계된다")
    void sentSettlementIsOffset() {
        expense(p1, "30000", Map.of(p1, "10000", p2, "10000", p3, "10000"));
        SettlementResponse fromP2 = settlementFrom(p2);
        settlementService.changeStatus(group.getId(), fromP2.id(), Action.SEND, p2.getId());

        Map<Long, BigDecimal> net = netById();

        assertAmount("0", net.get(p2.getId()), "보낸 사람의 빚은 사라진다");
        assertAmount("10000", net.get(p1.getId()), "받은 사람이 받을 돈도 그만큼 줄어든다");
        assertAmount("-10000", net.get(p3.getId()), "무관한 참여자는 그대로");
    }

    @Test
    @DisplayName("순잔액: COMPLETED 정산도 상계되며, 전부 완료되면 모두 0이 된다")
    void completedSettlementsClearNetBalances() {
        expense(p1, "30000", Map.of(p1, "10000", p2, "10000", p3, "10000"));
        settlementService.optimize(group.getId())
                .forEach(s -> {
                    settlementService.changeStatus(group.getId(), s.id(), Action.SEND, s.fromParticipantId());
                    settlementService.changeStatus(group.getId(), s.id(), Action.CONFIRM, s.toParticipantId());
                });

        assertTrue(balanceService.getNetBalances(group.getId()).stream()
                        .allMatch(b -> b.amount().signum() == 0),
                "정산이 모두 끝나면 더 보낼/받을 돈이 없다");
        // 총잔액(원장)은 그대로 남는다 — 지출 사실 자체는 변하지 않는다
        assertAmount("20000", balancesById().get(p1.getId()).balance());
    }

    @Test
    @DisplayName("순잔액: CANCEL로 송금이 취소되면 상계도 되돌아간다")
    void cancelRestoresNetBalance() {
        expense(p1, "30000", Map.of(p1, "10000", p2, "10000", p3, "10000"));
        SettlementResponse fromP2 = settlementFrom(p2);
        settlementService.changeStatus(group.getId(), fromP2.id(), Action.SEND, p2.getId());
        settlementService.changeStatus(group.getId(), fromP2.id(), Action.CANCEL, p2.getId());

        assertAmount("-10000", netById().get(p2.getId()));
    }

    @Test
    @DisplayName("순잔액의 합도 항상 0이다 — 상계액의 합이 0이기 때문")
    void netBalancesSumToZero() {
        expense(p1, "41000", Map.of(p1, "13668", p2, "13666", p3, "13666"));
        SettlementResponse any = settlementService.optimize(group.getId()).get(0);
        settlementService.changeStatus(group.getId(), any.id(), Action.SEND, any.fromParticipantId());

        BigDecimal sum = balanceService.getNetBalances(group.getId()).stream()
                .map(ParticipantBalance::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(0, sum.signum());
    }

    @Test
    @DisplayName("잔액(23): 탈퇴한 참여자도 지출 이력이 남아 있으면 active=false로 조회된다")
    void withdrawnParticipantWithLedgerIsListed() {
        expense(p1, "30000", Map.of(p1, "10000", p2, "10000", p3, "10000"));
        p3.deactivate();
        em.flush();

        Map<Long, ParticipantBalanceResponse> balances = balancesById();

        assertEquals(3, balances.size());
        ParticipantBalanceResponse withdrawn = balances.get(p3.getId());
        assertFalse(withdrawn.active());
        assertAmount("-10000", withdrawn.balance(), "탈퇴해도 갚아야 할 돈은 남는다");
    }

    @Test
    @DisplayName("잔액(23): 지출 이력이 없는 탈퇴자는 목록에서 빠진다")
    void withdrawnParticipantWithoutLedgerIsExcluded() {
        p3.deactivate();
        em.flush();
        expense(p1, "20000", Map.of(p1, "10000", p2, "10000"));

        assertFalse(balancesById().containsKey(p3.getId()));
    }

    @Test
    @DisplayName("지출이 하나도 없으면 모든 참여자의 잔액이 0이다")
    void noExpensesMeansAllZero() {
        List<ParticipantBalanceResponse> balances = balanceService.getBalances(group.getId());

        assertEquals(3, balances.size());
        assertTrue(balances.stream().allMatch(b -> b.balance().signum() == 0));
    }

    @Test
    @DisplayName("없는 모임의 잔액 조회는 404(ENTITY_NOT_FOUND)")
    void unknownGroupNotFound() {
        Long unknownGroupId = group.getId() + 999;

        BusinessException ex = assertThrows(BusinessException.class,
                () -> balanceService.getBalances(unknownGroupId));
        assertEquals(ErrorCode.ENTITY_NOT_FOUND, ex.getErrorCode());

        BusinessException netEx = assertThrows(BusinessException.class,
                () -> balanceService.getNetBalances(unknownGroupId));
        assertEquals(ErrorCode.ENTITY_NOT_FOUND, netEx.getErrorCode());
    }

    @Test
    @DisplayName("잔액(23): 다른 모임의 지출은 섞이지 않는다")
    void balancesAreScopedToGroup() {
        User other = User.builder().email("d@x.com").password("p").name("D").build();
        em.persist(other);
        Group otherGroup = Group.builder().name("동아리").owner(other).build();
        em.persist(otherGroup);
        GroupParticipant op = participant(otherGroup, other, GroupParticipant.Role.OWNER);
        em.flush();
        expense(op, "50000", Map.of(op, "50000"));
        expense(p1, "20000", Map.of(p1, "10000", p2, "10000"));

        Map<Long, ParticipantBalanceResponse> balances = balancesById();

        assertFalse(balances.containsKey(op.getId()));
        assertAmount("10000", balances.get(p1.getId()).balance());
    }

    /** 해당 참여자가 보내는 PENDING 정산 1건을 만들어 반환한다. */
    private SettlementResponse settlementFrom(GroupParticipant sender) {
        return settlementService.optimize(group.getId()).stream()
                .filter(s -> s.fromParticipantId().equals(sender.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("송금 건이 생성되지 않았다: " + sender.getId()));
    }
}
