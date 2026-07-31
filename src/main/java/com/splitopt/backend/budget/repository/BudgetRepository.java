package com.splitopt.backend.budget.repository;

import com.splitopt.backend.budget.domain.Budget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    Optional<Budget> findByGroup_Id(Long groupId);

    boolean existsByGroup_Id(Long groupId);
}
