package com.splitopt.backend.settlement.repository;

import com.splitopt.backend.settlement.domain.Settlement;
import com.splitopt.backend.settlement.domain.SettlementStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    List<Settlement> findByGroup_Id(Long groupId);

    /** 정산 건을 모임 범위로 조회 — 다른 모임의 정산을 건드리지 못하게 한다. */
    Optional<Settlement> findByIdAndGroup_Id(Long id, Long groupId);

    /**
     * 상태 전이(API 27)용 잠금 조회. 동시 SEND/CONFIRM/CANCEL 요청이 같은 SENT 건을 함께 읽어
     * 나중 커밋이 앞선 결과를 덮어쓰는 lost update를 막는다. 두 번째 요청은 첫 커밋을 기다린 뒤
     * 갱신된 상태를 읽어 도메인 상태 가드에서 걸러진다(→ 409).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Settlement s where s.id = :id and s.group.id = :groupId")
    Optional<Settlement> findByIdAndGroup_IdForUpdate(@Param("id") Long id, @Param("groupId") Long groupId);

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
