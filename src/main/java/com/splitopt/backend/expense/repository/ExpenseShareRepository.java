package com.splitopt.backend.expense.repository;

import com.splitopt.backend.expense.domain.ExpenseShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseShareRepository extends JpaRepository<ExpenseShare, Long> {

    List<ExpenseShare> findAllByExpenseId(Long expenseId);

    /**
     * 한 지출의 부담 내역을 모두 지운다.
     *
     * <p>엔티티를 실제로 지우므로 영속성 컨텍스트에서도 함께 빠진다. 벌크 삭제(JPQL delete)로
     * 바꾸면 컨텍스트를 우회해, 같은 트랜잭션에 남아 있는 {@code ExpenseShare}가 이미 지워진
     * {@code Expense}를 가리키는 상태가 된다(모임 삭제 경로에서 실제로 깨진다).
     *
     * <p><b>주의</b>: 이 호출은 DELETE를 <b>예약</b>할 뿐이고 실제 실행은 flush 시점이다.
     * 지우고 다시 넣는 흐름에서는 호출 직후 flush가 필요하다 —
     * {@code ExpenseService#updateExpense} 참고.
     */
    void deleteAllByExpenseId(Long expenseId);

    List<ExpenseShare> findAllByExpense_GroupId(Long groupId);
}
