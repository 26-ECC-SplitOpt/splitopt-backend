package com.splitopt.backend.budget.service;

import com.splitopt.backend.budget.domain.Budget;
import com.splitopt.backend.budget.dto.BudgetForecastResponse;
import com.splitopt.backend.budget.dto.BudgetResponse;
import com.splitopt.backend.budget.repository.BudgetRepository;
import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 예산 관리 서비스 (API 38·39·40).
 *
 * <p>현황(39)의 사용액·잔여·초과 여부는 지출 합계에서 파생한다. 저장하지 않고 조회 시점에
 * 계산하므로 지출이 바뀌어도 예산 행을 손댈 필요가 없다. 초과 예측(40)도 같은 방식이며,
 * 기준 시간축은 모임 일정이다 — 근거는 {@link BudgetForecastResponse} 참고.
 *
 * <p>설정(38)은 조회 후 저장이 아니라 DB 원자적 upsert로 처리한다 — 근거는
 * {@link #upsert(Long, java.math.BigDecimal)} 참고.
 */
@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;

    /**
     * 예산 설정/수정 (API 38). 모임당 1개 — 있으면 수정, 없으면 생성.
     *
     * <p>삽입·수정을 {@code group_id} UNIQUE 위의 단일 upsert 문장으로 처리해,
     * 같은 모임에 대한 동시 최초 설정 요청에서도 한쪽이 UNIQUE 위반으로 실패하지 않는다.
     * 엔티티 생성을 거치지 않으므로 금액 검증은 {@link Budget#validateAmount}로 먼저 수행한다.
     */
    @Transactional
    public BudgetResponse upsert(Long groupId, BigDecimal amount) {
        Budget.validateAmount(amount);
        budgetRepository.upsertAmount(groupId, amount);
        // upsert 직후에는 반드시 행이 존재한다. 없다면 UNIQUE 제약/스키마 전제가 깨진 상황.
        Budget budget = budgetRepository.findByGroup_Id(groupId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INTERNAL_SERVER_ERROR, "예산 저장에 실패했습니다."));
        // 설정 직후 화면이 곧바로 현황을 보여줄 수 있도록 조회(39)와 같은 형태로 돌려준다.
        return BudgetResponse.from(budget, budgetRepository.sumExpenseAmountByGroupId(groupId));
    }

    /**
     * 예산 현황 조회 (API 39). 설정 금액과 지출 합계에서 사용액·잔여·초과 여부를 파생한다.
     *
     * <p>예산을 설정한 적이 없으면 404다 — 사용액만 따로 보고 싶다면 통계(30)를 쓴다.
     */
    @Transactional(readOnly = true)
    public BudgetResponse getBudget(Long groupId) {
        Budget budget = budgetRepository.findByGroup_Id(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "설정된 예산이 없습니다."));
        return BudgetResponse.from(budget, budgetRepository.sumExpenseAmountByGroupId(groupId));
    }

    /**
     * 예산 초과 예측 (API 40). 모임 일정 기간의 경과 비율로 예상 총 지출을 낸다.
     *
     * <p>일정이 없거나 기간이 아직 시작되지 않았으면 예측하지 않고 {@code basis=NONE}으로
     * 돌려준다 — 경과 비율이 0이면 나눌 수 없다. 근거 없는 숫자 대신 근거 없음을 밝히고,
     * 화면은 현황(39)으로 물러선다.
     */
    @Transactional(readOnly = true)
    public BudgetForecastResponse getForecast(Long groupId) {
        Budget budget = budgetRepository.findByGroup_Id(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "설정된 예산이 없습니다."));
        BigDecimal spent = budgetRepository.sumExpenseAmountByGroupId(groupId);

        BudgetRepository.SchedulePeriod period = budgetRepository.findSchedulePeriodByGroupId(groupId);
        if (period == null || period.getStartAt() == null || period.getEndAt() == null) {
            return BudgetForecastResponse.notForecastable(groupId, budget.getAmount(), spent);
        }

        BigDecimal elapsedRatio = BudgetForecastResponse.elapsedRatio(
                period.getStartAt(), period.getEndAt(), LocalDateTime.now());
        if (elapsedRatio.signum() == 0) {
            return BudgetForecastResponse.notForecastable(groupId, budget.getAmount(), spent);
        }

        return BudgetForecastResponse.ofSchedule(groupId, budget.getAmount(), spent,
                period.getStartAt(), period.getEndAt(), elapsedRatio);
    }
}
