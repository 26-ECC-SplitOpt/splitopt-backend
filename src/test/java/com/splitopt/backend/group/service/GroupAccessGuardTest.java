package com.splitopt.backend.group.service;

import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 모임 인가 가드 테스트.
 *
 * <p>모임 범위 API(정산·잔액·예산)가 groupId만 받는 탓에 생기던 IDOR를 이 가드가 막는다.
 * 참여자/비참여자/탈퇴자/없는 모임 네 경우의 판정이 관심사다.
 */
@SpringBootTest
@Transactional
class GroupAccessGuardTest {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private GroupAccessGuard guard;

    private Group group;
    private User owner;
    private User member;
    private User outsider;
    private User leaver;
    private GroupParticipant memberParticipant;

    @BeforeEach
    void setUp() {
        owner = user("owner@x.com", "주영");
        member = user("member@x.com", "수빈");
        outsider = user("outsider@x.com", "남");
        leaver = user("leaver@x.com", "채빈");

        group = Group.builder().name("제주여행").owner(owner).build();
        em.persist(group);

        participant(owner, GroupParticipant.Role.OWNER);
        memberParticipant = participant(member, GroupParticipant.Role.MEMBER);

        GroupParticipant left = participant(leaver, GroupParticipant.Role.MEMBER);
        left.deactivate();

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

    @Test
    @DisplayName("참여자면 통과하고 참여자 엔티티를 돌려준다 — 27의 요청자 id가 여기서 나온다")
    void returnsParticipantForMember() {
        GroupParticipant resolved = guard.requireActiveParticipant(group.getId(), member.getId());

        assertEquals(memberParticipant.getId(), resolved.getId());
        assertEquals(member.getId(), resolved.getUser().getId());
    }

    @Test
    @DisplayName("개설자도 참여자로 통과한다")
    void returnsParticipantForOwner() {
        assertEquals(owner.getId(),
                guard.requireActiveParticipant(group.getId(), owner.getId()).getUser().getId());
    }

    @Test
    @DisplayName("모임에 속하지 않은 사용자 → 403(ACCESS_DENIED)")
    void deniesOutsider() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> guard.requireActiveParticipant(group.getId(), outsider.getId()));

        assertEquals(ErrorCode.ACCESS_DENIED, e.getErrorCode());
    }

    @Test
    @DisplayName("탈퇴자(is_active=false)는 더 이상 모임 데이터를 볼 수 없다 → 403")
    void deniesLeftParticipant() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> guard.requireActiveParticipant(group.getId(), leaver.getId()));

        assertEquals(ErrorCode.ACCESS_DENIED, e.getErrorCode());
    }

    @Test
    @DisplayName("없는 모임 → 404(ENTITY_NOT_FOUND) — 403보다 먼저 판정한다")
    void notFoundForMissingGroup() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> guard.requireActiveParticipant(999_999L, member.getId()));

        assertEquals(ErrorCode.ENTITY_NOT_FOUND, e.getErrorCode());
    }

    @Test
    @DisplayName("principal이 비어 userId가 null이면 → 401(UNAUTHORIZED)")
    void unauthorizedForNullUser() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> guard.requireActiveParticipant(group.getId(), null));

        assertEquals(ErrorCode.UNAUTHORIZED, e.getErrorCode());
    }

    @Test
    @DisplayName("requireMember는 같은 규칙으로 판정한다(반환값만 없음)")
    void requireMemberSharesTheSameRule() {
        guard.requireMember(group.getId(), member.getId());

        assertEquals(ErrorCode.ACCESS_DENIED,
                assertThrows(BusinessException.class,
                        () -> guard.requireMember(group.getId(), outsider.getId())).getErrorCode());
    }
}
