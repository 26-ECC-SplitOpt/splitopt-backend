package com.splitopt.backend.settlement.service;

import com.splitopt.backend.group.domain.Group;
import com.splitopt.backend.group.domain.GroupParticipant;
import com.splitopt.backend.settlement.domain.ParticipantBalance;
import com.splitopt.backend.settlement.domain.SettlementStatus;
import com.splitopt.backend.settlement.dto.SettlementResponse;
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
        settlementService.complete(first.get(0).id());

        settlementService.optimizeAndSave(group.getId(), balances);

        long completed = settlementRepository.countByGroup_IdAndStatus(group.getId(), SettlementStatus.COMPLETED);
        long pending = settlementRepository.countByGroup_IdAndStatus(group.getId(), SettlementStatus.PENDING);
        assertEquals(1, completed, "완료된 건은 보존");
        assertEquals(1, pending, "PENDING은 삭제 후 재생성 1건");
    }

    @Test
    @DisplayName("정산 완료 처리 시 상태·완료시각이 기록된다")
    void completeMarksStatusAndTime() {
        List<SettlementResponse> saved = settlementService.optimizeAndSave(
                group.getId(), List.of(bal(p1, "10000"), bal(p2, "-10000")));

        SettlementResponse completed = settlementService.complete(saved.get(0).id());

        assertEquals("COMPLETED", completed.status());
        assertNotNull(completed.completedAt());
    }

    @Test
    @DisplayName("이미 완료된 건을 다시 완료 처리하면 예외")
    void completingTwiceFails() {
        List<SettlementResponse> saved = settlementService.optimizeAndSave(
                group.getId(), List.of(bal(p1, "10000"), bal(p2, "-10000")));
        Long id = saved.get(0).id();
        settlementService.complete(id);
        assertThrows(IllegalStateException.class, () -> settlementService.complete(id));
    }

    @Test
    @DisplayName("요약: 완료율·전체 완료 여부를 계산한다")
    void summaryCountsAndRate() {
        List<SettlementResponse> saved = settlementService.optimizeAndSave(
                group.getId(), List.of(bal(p1, "120000"), bal(p2, "-40000"), bal(p3, "-80000")));
        settlementService.complete(saved.get(0).id());

        SettlementSummaryResponse summary = settlementService.getSummary(group.getId());

        assertEquals(2, summary.total());
        assertEquals(1, summary.completed());
        assertEquals(1, summary.pending());
        assertFalse(summary.allCompleted());
        assertEquals(0.5, summary.completionRate(), 0.0001);
    }

    @Test
    @DisplayName("정산 건이 없으면 요약은 완료율 1.0·전체완료 true")
    void summaryEmptyIsAllCompleted() {
        SettlementSummaryResponse summary = settlementService.getSummary(group.getId());
        assertEquals(0, summary.total());
        assertTrue(summary.allCompleted());
        assertEquals(1.0, summary.completionRate(), 0.0001);
    }

    @Test
    @DisplayName("미정산 조회는 PENDING만 반환한다")
    void getPendingReturnsOnlyPending() {
        List<SettlementResponse> saved = settlementService.optimizeAndSave(
                group.getId(), List.of(bal(p1, "120000"), bal(p2, "-40000"), bal(p3, "-80000")));
        settlementService.complete(saved.get(0).id());

        List<SettlementResponse> pending = settlementService.getPending(group.getId());
        assertEquals(1, pending.size());
        assertEquals("PENDING", pending.get(0).status());
    }

    @Test
    @DisplayName("내 정산 내역은 내가 보내거나 받는 건만 반환한다")
    void getMineReturnsInvolvingUser() {
        settlementService.optimizeAndSave(
                group.getId(), List.of(bal(p1, "120000"), bal(p2, "-40000"), bal(p3, "-80000")));

        Long u2Id = p2.getUser().getId();
        List<SettlementResponse> mine = settlementService.getMySettlements(group.getId(), u2Id);
        assertEquals(1, mine.size());
        assertEquals(p2.getId(), mine.get(0).fromParticipantId());
    }
}
