package com.splitopt.backend.settlement.repository;

import com.splitopt.backend.settlement.domain.Settlement;
import com.splitopt.backend.settlement.domain.SettlementStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 정산 조회 리포지토리.
 *
 * <p><b>화면에 내려가는 목록 조회(25·26·28)의 공통 규칙</b> — 아래 두 가지를 쿼리에서 함께 해결한다.
 * <ul>
 *   <li><b>정렬 고정</b>: 금액 내림차순, 같은 금액은 id 오름차순. 정렬을 명시하지 않으면 DB가
 *       임의 순서로 돌려주는데, 최적화 재실행(24)이 PENDING을 삭제 후 재삽입하므로 같은 내용의
 *       목록이 실행할 때마다 다른 순서로 내려간다.</li>
 *   <li><b>참여자·사용자 함께 로딩</b>: 응답은 참여자 표시 이름을 담는데, 참여자의
 *       {@code displayName}이 비어 있으면 {@code user.getName()}으로 폴백한다. 참여자와 사용자가
 *       모두 지연 로딩이라 fetch join이 없으면 이름을 만들 때마다 추가 쿼리가 나간다.</li>
 * </ul>
 * from·to 참여자와 그 사용자는 모두 NOT NULL이라 inner join으로 충분하며, 컬렉션이 아닌
 * 단일 연관만 조인하므로 결과 행이 부풀지 않는다.
 */
public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    /** 정산 결과 전체 조회 (API 25). */
    @Query("""
            select s from Settlement s
            join fetch s.fromParticipant fp
            join fetch fp.user
            join fetch s.toParticipant tp
            join fetch tp.user
            where s.group.id = :groupId
            order by s.amount desc, s.id asc
            """)
    List<Settlement> findByGroup_Id(@Param("groupId") Long groupId);

    /** 정산 건을 모임 범위로 조회 — 다른 모임의 정산을 건드리지 못하게 한다. */
    Optional<Settlement> findByIdAndGroup_Id(Long id, Long groupId);

    /**
     * 상태 전이(API 27)용 잠금 조회. 동시 SEND/CONFIRM/CANCEL 요청이 같은 SENT 건을 함께 읽어
     * 나중 커밋이 앞선 결과를 덮어쓰는 lost update를 막는다. 두 번째 요청은 첫 커밋을 기다린 뒤
     * 갱신된 상태를 읽어 도메인 상태 가드에서 걸러진다(→ 409).
     *
     * <p>여기에는 <b>fetch join을 쓰지 않는다</b>. 잠금 조회에 조인을 걸면 MySQL이 조인된
     * 참여자·사용자 행까지 함께 잠가, 정산과 무관한 요청이 그 참여자 행에서 막히거나 잠금 순서가
     * 엇갈려 교착이 생길 수 있다. 단건이라 지연 로딩 비용도 작다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Settlement s where s.id = :id and s.group.id = :groupId")
    Optional<Settlement> findByIdAndGroup_IdForUpdate(@Param("id") Long id, @Param("groupId") Long groupId);

    /** 상태별 정산 조회 (API 25 필터 · 28 미정산). */
    @Query("""
            select s from Settlement s
            join fetch s.fromParticipant fp
            join fetch fp.user
            join fetch s.toParticipant tp
            join fetch tp.user
            where s.group.id = :groupId and s.status = :status
            order by s.amount desc, s.id asc
            """)
    List<Settlement> findByGroup_IdAndStatus(@Param("groupId") Long groupId,
                                             @Param("status") SettlementStatus status);

    /**
     * 여러 상태를 한 번에 조회. 순잔액 계산(API 23·24)에서 이미 오간 돈(SENT·COMPLETED)을
     * 지출 원장 잔액에서 상계할 때 사용한다.
     *
     * <p>화면에 나가지 않고 참여자 id와 금액만 쓰므로(표시 이름을 만들지 않는다) 정렬도
     * fetch join도 필요 없다.
     */
    List<Settlement> findByGroup_IdAndStatusIn(Long groupId, Collection<SettlementStatus> statuses);

    long countByGroup_Id(Long groupId);

    long countByGroup_IdAndStatus(Long groupId, SettlementStatus status);

    void deleteByGroup_IdAndStatus(Long groupId, SettlementStatus status);

    /**
     * 내 정산 내역(API 26): 로그인 사용자가 보내거나 받는 정산.
     *
     * <p>모임 id로 먼저 좁히므로 여러 모임에 참여한 사용자여도 경로의 모임 것만 나온다.
     * 사용자 판별은 fetch join으로 이미 가져온 별칭을 그대로 쓴다 — 조건에서 연관 경로를 다시
     * 타면(예: {@code s.fromParticipant.user.id}) 같은 테이블에 조인이 한 벌 더 붙는다.
     */
    @Query("""
            select s from Settlement s
            join fetch s.fromParticipant fp
            join fetch fp.user fu
            join fetch s.toParticipant tp
            join fetch tp.user tu
            where s.group.id = :groupId
              and (fu.id = :userId or tu.id = :userId)
            order by s.amount desc, s.id asc
            """)
    List<Settlement> findMine(@Param("groupId") Long groupId, @Param("userId") Long userId);

    /** 목록 API(6)용 — 그룹별 정산 건수 */
    @Query("""
        select s.group.id, count(s)
        from Settlement s
        where s.group.id in :groupIds
        group by s.group.id
        """)
    List<Object[]> countByGroupIdIn(@Param("groupIds") Collection<Long> groupIds);

    /** 목록 API(6)용 — 그룹별 COMPLETED 정산 건수 */
    @Query("""
        select s.group.id, count(s)
        from Settlement s
        where s.group.id in :groupIds and s.status = :status
        group by s.group.id
        """)
    List<Object[]> countByGroupIdInAndStatus(
            @Param("groupIds") Collection<Long> groupIds,
            @Param("status") SettlementStatus status);
}
