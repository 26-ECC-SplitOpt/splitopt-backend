package com.splitopt.backend.expense.repository;

import com.splitopt.backend.expense.domain.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findAllByGroupId(Long groupId);
    Optional<Expense> findByIdAndGroupId(Long expenseId, Long groupId);
}