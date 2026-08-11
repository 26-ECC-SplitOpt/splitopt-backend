package com.splitopt.backend.settlement.service;

import com.splitopt.backend.group.domain.Group;
import com.splitopt.backend.group.domain.GroupParticipant;
import com.splitopt.backend.settlement.domain.ParticipantBalance;
import com.splitopt.backend.settlement.domain.Settlement;
import com.splitopt.backend.settlement.domain.SettlementStatus;
import com.splitopt.backend.settlement.dto.MySettlementsResponse;
import com.splitopt.backend.settlement.dto.SettlementResponse;
import com.splitopt.backend.user.domain.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 정산 조회 쿼리의 정렬(#33)·쿼리 수(#34)·모임 격리(#35) 테스트.
 *
 * <p>결과값이 맞는지는 {@link SettlementServiceTest}가 본다. 여기서는 <b>어떤 순서로, 몇 번의
 * 쿼리로</b> 내려오는지를 본다 — 정렬을 명시하지 않으면 재실행할 때마다 순서가 뒤바뀌고,
 * fetch join이 빠지면 참여자 이름을 만들 때마다 쿼리가 늘어나기 때문이다. 둘 다 결과값 검증만으로는
 * 잡히지 않아 별도 테스트로 둔다.
 *
 * <p>쿼리 수는 Hibernate 통계로 센다. 통계는 이 테스트에서만 필요해 프로퍼티로 켠다.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class SettlementQueryTest {

    @PersistenceContext
    private EntityManager em;
    @Autowired
    private EntityManagerFactory emf;
    @Autowired
    private SettlementService settlementService;

    private Group group;
    private User u1;
    private GroupParticipant p1;
    private GroupParticipant p2;
    private GroupParticipant p3;
    private GroupParticipant p4;

    /** 표시 이름이 사용자로 폴백되는(=참여자 displayName이 비어 있는) 실제 생성 경로와 같은 조건. */
    @BeforeEach
    void setUp() {
        u1 = user("a@x.com", "A");
        User u2 = user("b@x.com", "B");
        User u3 = user("c@x.com", "C");
        User u4 = user("d@x.com", "D");

        group = group("제주여행", u1);
        p1 = participant(group, u1, GroupParticipant.Role.OWNER);
        p2 = participant(group, u2, GroupParticipant.Role.MEMBER);
        p3 = participant(group, u3, GroupParticipant.Role.MEMBER);
        p4 = participant(group, u4, GroupParticipant.Role.MEMBER);
        em.flush();
    }

    private User user(String email, String name) {
        User u = User.builder().email(email).password("p").name(name).build();
        em.persist(u);
        return u;
    }

    private Group group(String name, User owner) {
        Group g = Group.builder().name(name).owner(owner).build();
        em.persist(g);
        return g;
    }

    private GroupParticipant participant(Group g, User u, GroupParticipant.Role role) {
        GroupParticipant p = GroupParticipant.builder().group(g).user(u).role(role).build();
        em.persist(p);
        return p;
    }

    private Settlement settlement(Group g, GroupParticipant from, GroupParticipant to, String amount) {
        Settlement s = Settlement.builder()
                .group(g).fromParticipant(from).toParticipant(to).amount(new BigDecimal(amount))
                .build();
        em.persist(s);
        return s;
    }

    /**
     * 금액이 뒤섞인 정산 4건. 20000원 두 건은 동액이라 id 오름차순 타이브레이크를 확인한다.
     * 저장 순서(10000 → 20000 → 30000 → 20000)는 기대 순서와 일부러 다르게 둔다.
     */
    private List<Settlement> mixedAmountSettlements() {
        Settlement small = settlement(group, p3, p1, "10000");
        Settlement mid = settlement(group, p4, p1, "20000");
        Settlement big = settlement(group, p2, p1, "30000");
        Settlement midLater = settlement(group, p4, p2, "20000");
        em.flush();
        return List.of(big, mid, midLater, small);
    }

    /** 영속성 컨텍스트를 비워 조회가 DB를 실제로 다시 읽게 한다(1차 캐시 적중으로 쿼리 수가 가려지는 것 방지). */
    private Statistics freshStatistics() {
        em.flush();
        em.clear();
        Statistics statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        return statistics;
    }

    private void assertSortedByAmountDesc(List<BigDecimal> amounts) {
        for (int i = 1; i < amounts.size(); i++) {
            assertTrue(amounts.get(i - 1).compareTo(amounts.get(i)) >= 0,
                    "금액 내림차순이어야 한다: " + amounts);
        }
    }

    @Test
    @DisplayName("전체 조회(25)는 금액 내림차순, 동액은 id 오름차순으로 내려온다")
    void getSettlementsOrdersByAmountThenId() {
        List<Settlement> expected = mixedAmountSettlements();

        List<SettlementResponse> result = settlementService.getSettlements(group.getId());

        assertIterableEquals(expected.stream().map(Settlement::getId).toList(),
                result.stream().map(SettlementResponse::id).toList());
        assertSortedByAmountDesc(result.stream().map(SettlementResponse::amount).toList());
    }

    @Test
    @DisplayName("미정산 조회(28)도 같은 정렬을 따른다 — 동액 두 건이 id 오름차순으로 남는다")
    void getByStatusOrdersByAmountThenId() {
        List<Settlement> all = mixedAmountSettlements();
        Settlement big = all.get(0);
        Settlement mid = all.get(1);
        Settlement midLater = all.get(2);
        all.get(3).markSent(); // 10000 한 건을 PENDING에서 빼 필터가 걸린 상태로 만든다(동액 두 건은 남긴다)
        em.flush();

        List<SettlementResponse> pending =
                settlementService.getByStatus(group.getId(), SettlementStatus.PENDING);

        assertIterableEquals(List.of(big.getId(), mid.getId(), midLater.getId()),
                pending.stream().map(SettlementResponse::id).toList());
        assertSortedByAmountDesc(pending.stream().map(SettlementResponse::amount).toList());
    }

    @Test
    @DisplayName("내 정산(26)은 받을·보낼 묶음 안에서도 금액 내림차순, 동액은 id 오름차순이다")
    void getMySettlementsOrdersWithinBuckets() {
        List<Settlement> all = mixedAmountSettlements();
        Settlement big = all.get(0);
        Settlement mid = all.get(1);
        Settlement midLater = all.get(2);
        Settlement small = all.get(3);

        // p1(u1)은 세 건의 수취인 — 금액이 모두 달라 내림차순만 확인된다
        MySettlementsResponse receiver = settlementService.getMySettlements(group.getId(), u1.getId());
        assertIterableEquals(List.of(big.getId(), mid.getId(), small.getId()),
                receiver.toReceive().stream().map(MySettlementsResponse.Item::settlementId).toList());
        assertTrue(receiver.toSend().isEmpty());

        // p4는 20000원 두 건의 송금인 — 보낼 묶음에서 동액 타이브레이크가 확인된다
        MySettlementsResponse sender =
                settlementService.getMySettlements(group.getId(), p4.getUser().getId());
        assertIterableEquals(List.of(mid.getId(), midLater.getId()),
                sender.toSend().stream().map(MySettlementsResponse.Item::settlementId).toList());
        assertSortedByAmountDesc(sender.toSend().stream()
                .map(MySettlementsResponse.Item::amount).toList());
    }

    @Test
    @DisplayName("내 정산(26)의 완료 묶음도 같은 정렬을 따른다")
    void getMySettlementsOrdersCompletedBucket() {
        List<Settlement> all = mixedAmountSettlements();
        Settlement mid = all.get(1);
        Settlement midLater = all.get(2);
        // p4가 보낸 20000원 두 건을 완료 상태로 — 방향과 무관하게 completed로 모인다
        for (Settlement s : List.of(mid, midLater)) {
            s.markSent();
            s.confirm();
        }
        em.flush();

        MySettlementsResponse sender =
                settlementService.getMySettlements(group.getId(), p4.getUser().getId());

        assertIterableEquals(List.of(mid.getId(), midLater.getId()),
                sender.completed().stream().map(MySettlementsResponse.Item::settlementId).toList());
        assertTrue(sender.toSend().isEmpty(), "완료된 건은 보낼 묶음에서 빠진다");
    }

    @Test
    @DisplayName("최적화 재실행(24)으로 PENDING이 재생성돼도 정렬은 유지된다")
    void orderStaysStableAcrossRerun() {
        List<ParticipantBalance> balances = List.of(
                new ParticipantBalance(p1.getId(), new BigDecimal("60000")),
                new ParticipantBalance(p2.getId(), new BigDecimal("-10000")),
                new ParticipantBalance(p3.getId(), new BigDecimal("-20000")),
                new ParticipantBalance(p4.getId(), new BigDecimal("-30000")));

        settlementService.optimizeAndSave(group.getId(), balances);
        List<BigDecimal> before = settlementService.getSettlements(group.getId()).stream()
                .map(SettlementResponse::amount).toList();

        // 재실행은 기존 PENDING을 지우고 새 id로 다시 넣는다 — 정렬이 없으면 여기서 순서가 흔들린다
        settlementService.optimizeAndSave(group.getId(), balances);
        List<BigDecimal> after = settlementService.getSettlements(group.getId()).stream()
                .map(SettlementResponse::amount).toList();

        assertSortedByAmountDesc(before);
        assertSortedByAmountDesc(after);
        assertIterableEquals(before, after);
    }

    @Test
    @DisplayName("전체 조회(25)는 참여자 수와 무관하게 쿼리 한 번으로 끝난다")
    void getSettlementsRunsSingleQuery() {
        mixedAmountSettlements();
        Statistics statistics = freshStatistics();

        List<SettlementResponse> result = settlementService.getSettlements(group.getId());

        assertEquals(4, result.size());
        assertTrue(result.stream().allMatch(r -> r.fromName() != null && r.toName() != null),
                "표시 이름이 채워져야 쿼리 수 측정이 의미가 있다");
        assertEquals(1, statistics.getPrepareStatementCount(),
                "참여자·사용자를 fetch join으로 함께 읽어야 한다");
    }

    @Test
    @DisplayName("내 정산(26)도 쿼리 한 번으로 끝난다")
    void getMySettlementsRunsSingleQuery() {
        mixedAmountSettlements();
        Statistics statistics = freshStatistics();

        MySettlementsResponse mine = settlementService.getMySettlements(group.getId(), u1.getId());

        assertEquals(3, mine.toReceive().size());
        assertTrue(mine.toReceive().stream().allMatch(i -> i.counterpartName() != null));
        assertEquals(1, statistics.getPrepareStatementCount(),
                "참여자·사용자를 fetch join으로 함께 읽어야 한다");
    }

    @Test
    @DisplayName("내 정산(26)은 여러 모임에 참여한 사용자여도 경로의 모임 것만 돌려준다")
    void getMySettlementsIsolatesByGroup() {
        // 같은 사용자가 참여한 두 번째 모임 — 26은 사용자 id로 조회하므로 여기가 위험 구간이다
        Group other = group("부산여행", u1);
        GroupParticipant otherMe = participant(other, u1, GroupParticipant.Role.OWNER);
        GroupParticipant otherFriend = participant(other, p2.getUser(), GroupParticipant.Role.MEMBER);
        settlement(group, p2, p1, "30000");
        Settlement otherGroupSettlement = settlement(other, otherFriend, otherMe, "77000");
        em.flush();

        MySettlementsResponse mine = settlementService.getMySettlements(group.getId(), u1.getId());

        assertEquals(1, mine.toReceive().size());
        assertEquals(0, new BigDecimal("30000").compareTo(mine.toReceive().get(0).amount()));
        assertTrue(mine.toReceive().stream()
                        .noneMatch(i -> i.settlementId().equals(otherGroupSettlement.getId())),
                "다른 모임의 정산이 섞이면 안 된다");

        // 반대쪽 모임도 자기 것만 본다
        MySettlementsResponse fromOther = settlementService.getMySettlements(other.getId(), u1.getId());
        assertEquals(1, fromOther.toReceive().size());
        assertEquals(otherGroupSettlement.getId(), fromOther.toReceive().get(0).settlementId());
    }
}
