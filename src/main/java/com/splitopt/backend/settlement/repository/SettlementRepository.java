package com.splitopt.backend.settlement.repository;

import com.splitopt.backend.settlement.domain.Settlement;
import com.splitopt.backend.settlement.domain.SettlementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    List<Settlement> findByGroup_Id(Long groupId);

    /** 정산 건을 모임 범위로 조회 — 다른 모임의 정산을 건드리지 못하게 한다. */
    Optional<Settlement> findByIdAndGroup_Id(Long id, Long groupId);

    List<Settlement> findByGroup_IdAndStatus(Long groupId, SettlementStatus status);

    long countByGroup_Id(Long groupId);

    long countByGroup_IdAndStatus(Long groupId, SettlementStatus status);

    void deleteByGroup_IdAndStatus(Long groupId, SettlementStatus status);

    /** 내 정산 내역(API 26): 로그인 사용자가 보내거나 받는 정산. */
    @Query("""
            select s from Settlement s
            where s.group.id = :groupId
              and (s.fromParticipant.user.id = :userId or s.toParticipant.user.id = :userId)
            """)
    List<Settlement> findMine(@Param("groupId") Long groupId, @Param("userId") Long userId);
}
