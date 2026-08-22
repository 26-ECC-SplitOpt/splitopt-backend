package com.splitopt.backend.group.repository;

import com.splitopt.backend.group.domain.GroupParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GroupParticipantRepository extends JpaRepository<GroupParticipant, Long> {

    // 모임에 속한 "활성" 참여자만 조회 (탈퇴자는 제외)
    List<GroupParticipant> findAllByGroupIdAndIsActiveTrue(Long groupId);

    // 탈퇴자 포함 전체 조회 — 잔액(23)·정산(24)은 탈퇴 전 지출 이력이 남은 참여자도 다뤄야 한다
    List<GroupParticipant> findAllByGroupId(Long groupId);

    // 특정 참여자가 진짜 이 모임 소속이 맞는지 확인할 때 사용
    Optional<GroupParticipant> findByIdAndGroupId(Long participantId, Long groupId);

    Optional<GroupParticipant> findByIdAndGroupIdAndIsActiveTrue(Long participantId, Long groupId);

    long countByGroupIdAndIsActiveTrue(Long groupId);

    Optional<GroupParticipant> findByGroupIdAndUserId(Long groupId, Long userId);

    @EntityGraph(attributePaths = "group")
    Page<GroupParticipant> findAllByUserIdAndIsActiveTrue(Long userId, Pageable pageable);

    //목록 API(6)용 — 그룹별 활성 참여자 수
    @Query("""
        select p.group.id, count(p)
        from GroupParticipant p
        where p.group.id in :groupIds and p.isActive = true
        group by p.group.id
        """)
    List<Object[]> countActiveMembersByGroupIdIn(@Param("groupIds") Collection<Long> groupIds);
}