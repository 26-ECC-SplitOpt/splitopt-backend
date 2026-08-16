package com.splitopt.backend.expense.repository;

import com.splitopt.backend.expense.domain.Expense;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    /**
     * 모임의 지출 전체.
     *
     * <p>응답에 결제자와 연결된 일정을 담으므로 함께 읽어 온다. 둘 다 지연 로딩이라 그냥 두면
     * 지출 건수만큼 조회가 늘어난다. 일정은 연결하지 않은 지출이 많아 outer join이어야 하는데,
     * {@code @EntityGraph}가 그렇게 처리한다.
     *
     * <p>결제 총액 집계(정산 입력)도 이 메서드를 쓴다. 그쪽은 참여자 id만 보므로 추가 로딩이
     * 낭비지만, 목록 조회가 매 건 추가 질의를 하는 쪽이 더 비싸다.
     */
    @EntityGraph(attributePaths = {"payer", "schedule"})
    List<Expense> findAllByGroupId(Long groupId);

    List<Expense> findAllByScheduleId(Long scheduleId);

    /** 지출 1건. 상세 응답이 결제자·일정을 담으므로 함께 읽어 온다. */
    @EntityGraph(attributePaths = {"payer", "schedule"})
    Optional<Expense> findByIdAndGroupId(Long expenseId, Long groupId);
}
