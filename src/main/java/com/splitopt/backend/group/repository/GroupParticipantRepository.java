package com.splitopt.backend.group.repository;

import com.splitopt.backend.group.domain.GroupParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupParticipantRepository extends JpaRepository<GroupParticipant, Long> {

    // 모임에 속한 "활성" 참여자만 조회 (탈퇴자는 제외)
    List<GroupParticipant> findAllByGroupIdAndIsActiveTrue(Long groupId);

    // 특정 참여자가 진짜 이 모임 소속이 맞는지 확인할 때 사용
    Optional<GroupParticipant> findByIdAndGroupId(Long participantId, Long groupId);

    Optional<GroupParticipant> findByIdAndGroupIdAndIsActiveTrue(Long participantId, Long groupId);
}