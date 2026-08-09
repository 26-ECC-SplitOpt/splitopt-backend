package com.splitopt.backend.expense.repository;

import com.splitopt.backend.expense.domain.ExpenseShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseShareRepository extends JpaRepository<ExpenseShare, Long> {
    List<ExpenseShare> findAllByExpenseId(Long expenseId);
    void deleteAllByExpenseId(Long expenseId);
    List<ExpenseShare> findAllByExpense_GroupId(Long groupId);
}
