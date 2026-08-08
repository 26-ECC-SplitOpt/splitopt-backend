package com.splitopt.backend.statistics.service;

import com.splitopt.backend.expense.domain.Expense;
import com.splitopt.backend.expense.domain.ExpenseShare;
import com.splitopt.backend.expense.repository.ExpenseRepository;
import com.splitopt.backend.expense.repository.ExpenseShareRepository;
import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.group.domain.GroupParticipant;
import com.splitopt.backend.group.repository.GroupParticipantRepository;
import com.splitopt.backend.group.repository.GroupRepository;
import com.splitopt.backend.statistics.dto.CategoryStatisticsResponse;
import com.splitopt.backend.statistics.dto.GroupStatisticsResponse;
import com.splitopt.backend.statistics.dto.ParticipantStatisticsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatisticsService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseShareRepository expenseShareRepository;
    private final GroupParticipantRepository groupParticipantRepository;
    private final GroupRepository groupRepository;

    /** 모임 전체 지출 통계(30). */
    public GroupStatisticsResponse getGroupStatistics(Long groupId) {
        validateGroupExists(groupId);

        List<Expense> expenses = expenseRepository.findAllByGroupId(groupId);
        BigDecimal total = sumAmounts(expenses);

        List<GroupParticipant> activeParticipants =
                groupParticipantRepository.findAllByGroupIdAndIsActiveTrue(groupId);

        BigDecimal average = activeParticipants.isEmpty()
                ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(activeParticipants.size()), 0, RoundingMode.HALF_UP);

        return new GroupStatisticsResponse(total, expenses.size(), average);
    }

    /** 카테고리별 지출 통계(31). */
    public CategoryStatisticsResponse getCategoryStatistics(Long groupId) {
        validateGroupExists(groupId);

        List<Expense> expenses = expenseRepository.findAllByGroupId(groupId);
        BigDecimal total = sumAmounts(expenses);

        // 카테고리별로 묶어서(grouping) 합계 계산
        Map<String, BigDecimal> byCategory = expenses.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getCategory().name(),
                        Collectors.reducing(BigDecimal.ZERO, Expense::getAmount, BigDecimal::add)
                ));

        List<CategoryStatisticsResponse.CategoryItem> items = byCategory.entrySet().stream()
                .map(entry -> new CategoryStatisticsResponse.CategoryItem(
                        entry.getKey(),
                        entry.getValue(),
                        calculatePercentage(entry.getValue(), total)
                ))
                .sorted((a, b) -> b.amount().compareTo(a.amount())) // 금액 큰 순서로 정렬
                .toList();

        return new CategoryStatisticsResponse(total, items);
    }

    /** 참여자별 결제·부담 통계(32). */
    public ParticipantStatisticsResponse getParticipantStatistics(Long groupId) {
        validateGroupExists(groupId);

        List<GroupParticipant> activeParticipants =
                groupParticipantRepository.findAllByGroupIdAndIsActiveTrue(groupId);

        // 참여자별 결제액: expenses를 결제자(payer) 기준으로 묶어서 합계
        List<Expense> expenses = expenseRepository.findAllByGroupId(groupId);
        Map<Long, BigDecimal> paidByParticipant = expenses.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getPayer().getId(),
                        Collectors.reducing(BigDecimal.ZERO, Expense::getAmount, BigDecimal::add)
                ));

        // 참여자별 부담액: expense_shares를 참여자 기준으로 묶어서 합계
        List<ExpenseShare> shares = expenseShareRepository.findAllByExpense_GroupId(groupId);
        Map<Long, BigDecimal> owedByParticipant = shares.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getParticipant().getId(),
                        Collectors.reducing(BigDecimal.ZERO, ExpenseShare::getShareAmount, BigDecimal::add)
                ));

        List<ParticipantStatisticsResponse.ParticipantItem> items = activeParticipants.stream()
                .map(p -> {
                    BigDecimal paid = paidByParticipant.getOrDefault(p.getId(), BigDecimal.ZERO);
                    BigDecimal owed = owedByParticipant.getOrDefault(p.getId(), BigDecimal.ZERO);
                    return new ParticipantStatisticsResponse.ParticipantItem(
                            p.getId(), p.getEffectiveDisplayName(), paid, owed, paid.subtract(owed));
                })
                .toList();

        return new ParticipantStatisticsResponse(items);
    }

    private BigDecimal sumAmounts(List<Expense> expenses) {
        return expenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculatePercentage(BigDecimal part, BigDecimal total) {
        if (total.signum() == 0) return BigDecimal.ZERO;
        return part.divide(total, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }

    private void validateGroupExists(Long groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "모임을 찾을 수 없습니다.");
        }
    }
}