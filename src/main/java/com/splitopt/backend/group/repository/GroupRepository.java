package com.splitopt.backend.group.repository;

import com.splitopt.backend.group.domain.Group;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GroupRepository extends JpaRepository<Group, Long> {

    /**
     * 모임 행 배타 잠금 조회. 모임 단위로 직렬화해야 하는 작업(정산 최적화 재실행 등)에서
     * 읽기 전에 잠가 동시 요청의 read-modify-write 경합을 막는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Group g where g.id = :groupId")
    Optional<Group> findByIdForUpdate(@Param("groupId") Long groupId);
}
