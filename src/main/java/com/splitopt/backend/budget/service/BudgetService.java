package com.splitopt.backend.budget.service;

import com.splitopt.backend.budget.domain.Budget;
import com.splitopt.backend.budget.dto.BudgetResponse;
import com.splitopt.backend.budget.repository.BudgetRepository;
import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.group.domain.Group;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 예산 관리 서비스 (API 38·39).
 *
 * <p>예산 현황의 사용액·잔여(39)와 초과 예측(40)은 지출({@code expenses}, 이채빈 파트) 집계가
 * 필요하므로 지출 파트 완성 후 연결한다. 현재는 예산 설정/조회(금액)까지 구현.
 */
@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final EntityManager em;

    /** 예산 설정/수정 (API 38). 모임당 1개 — 있으면 수정, 없으면 생성. */
    @Transactional
    public BudgetResponse upsert(Long groupId, BigDecimal amount) {
        Budget budget = budgetRepository.findByGroup_Id(groupId)
                .map(existing -> {
                    existing.updateAmount(amount);
                    return existing;
                })
                .orElseGet(() -> budgetRepository.save(
                        Budget.builder()
                                .group(em.getReference(Group.class, groupId))
                                .amount(amount)
                                .build()));
        return BudgetResponse.from(budget);
    }

    /** 예산 현황 조회 (API 39, 금액만 — 사용액/잔여는 지출 파트 연결 후 확장). */
    @Transactional(readOnly = true)
    public BudgetResponse getBudget(Long groupId) {
        Budget budget = budgetRepository.findByGroup_Id(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "설정된 예산이 없습니다."));
        return BudgetResponse.from(budget);
    }
}
