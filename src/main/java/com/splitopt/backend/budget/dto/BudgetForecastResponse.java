package com.splitopt.backend.budget.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 예산 초과 예측 (API 40).
 *
 * <p>"지금 쓰는 속도대로 가면 예산을 넘을 것 같은가"에 답한다. 예산 대비 현재 사용액만 보는
 * 현황(39)과 달리 <b>기간이 얼마나 지났는지</b>가 필요하다: 예산의 80%를 썼어도 일정의 90%가
 * 지났다면 넘지 않을 것이고, 20%가 지났다면 크게 넘을 것이다.
 *
 * <p><b>기준 시간축.</b> {@code budgets}에는 기간 컬럼이 없다. 스키마를 바꾸지 않고 쓸 수 있는
 * 시간축은 모임 일정(첫 일정 시작 ~ 마지막 일정 종료)뿐이라 그것을 기준으로 삼는다.
 *
 * <p>일정이 없거나 기간이 아직 시작되지 않았으면 <b>예측하지 않는다</b>({@link Basis#NONE}).
 * 하루도 지나지 않았으면 하루 평균을 낼 수 없고, 근거 없는 숫자를 그럴듯하게 내보내는 것보다
 * 낫다. 명세는 이 경우를 정의하지 않아 예측 필드를 비우고 근거를 밝히는 쪽으로 뒀다. 화면은
 * 그때 현황(39)으로 물러서면 된다.
 *
 * @param basis             예측 근거. {@code SCHEDULE}이면 아래 예측 필드가 모두 채워지고,
 *                          {@code NONE}이면 모두 {@code null}이다.
 * @param elapsedDays       기간 중 지난 일수 (1 이상)
 * @param totalDays         기간 전체 일수
 * @param dailyAverage      하루 평균 지출 {@code spent / elapsedDays}
 * @param projectedTotal    예상 총 지출 {@code dailyAverage × totalDays}
 * @param projectedOverage  예상 초과액. 넘지 않을 것으로 보이면 0이다(음수로 두지 않는다 —
 *                          "얼마 남는지"는 현황(39)의 {@code remaining}이 답한다).
 * @param willExceed        예상 총 지출이 총예산을 넘는지
 */
public record BudgetForecastResponse(
        Long groupId,
        BigDecimal totalBudget,
        BigDecimal spent,
        Basis basis,
        LocalDateTime periodStart,
        LocalDateTime periodEnd,
        Long elapsedDays,
        Long totalDays,
        BigDecimal dailyAverage,
        BigDecimal projectedTotal,
        BigDecimal projectedOverage,
        Boolean willExceed
) {
    /** 예측 근거. 지금은 일정 기반뿐이고, 기준이 늘어나면 여기에 추가한다. */
    public enum Basis {
        /** 모임 일정 기간의 경과 일수로 예측 */
        SCHEDULE,
        /** 예측 불가 — 일정이 없거나 아직 하루도 지나지 않음 */
        NONE
    }

    /** 예측 불가. 예산·사용액만 담고 예측 필드는 비운다. */
    public static BudgetForecastResponse notForecastable(Long groupId, BigDecimal totalBudget, BigDecimal spent) {
        return new BudgetForecastResponse(groupId, totalBudget, spent, Basis.NONE,
                null, null, null, null, null, null, null, null);
    }

    /**
     * 일정 기간을 기준으로 예측한다.
     *
     * @param elapsedDays 지난 일수 (1 이상). 0이면 하루 평균을 낼 수 없으므로 호출 전에 걸러야 한다.
     * @param totalDays   기간 전체 일수 (1 이상)
     */
    public static BudgetForecastResponse ofSchedule(Long groupId, BigDecimal totalBudget, BigDecimal spent,
                                                    LocalDateTime periodStart, LocalDateTime periodEnd,
                                                    long elapsedDays, long totalDays) {
        BigDecimal dailyAverage = spent.divide(BigDecimal.valueOf(elapsedDays), 2, RoundingMode.HALF_UP);
        // 예상 총액은 하루 평균을 다시 곱하지 않고 한 번에 계산한다. 반올림한 평균을 곱하면
        // 오차가 남아, 기간이 끝나 더 쓸 일이 없는데도 예상 총액이 사용액과 어긋난다.
        BigDecimal projectedTotal = spent.multiply(BigDecimal.valueOf(totalDays))
                .divide(BigDecimal.valueOf(elapsedDays), 2, RoundingMode.HALF_UP);
        BigDecimal overage = projectedTotal.subtract(totalBudget).max(BigDecimal.ZERO);
        return new BudgetForecastResponse(
                groupId, totalBudget, spent, Basis.SCHEDULE,
                periodStart, periodEnd,
                elapsedDays, totalDays, dailyAverage,
                projectedTotal, overage,
                projectedTotal.compareTo(totalBudget) > 0);
    }

    /**
     * 기간 전체 일수. 시작일과 종료일을 모두 포함해 센다(당일치기 여행은 1일).
     */
    public static long totalDays(LocalDateTime start, LocalDateTime end) {
        return ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate()) + 1;
    }

    /**
     * 지금까지 지난 일수. 기간 시작 전이면 0, 기간이 끝났으면 전체 일수와 같다.
     *
     * <p>날짜 단위로 세므로 시작 당일이면 1이다 — 하루 치 지출로 하루 평균을 내는 것이 맞다.
     */
    public static long elapsedDays(LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        LocalDate startDate = start.toLocalDate();
        LocalDate today = now.toLocalDate();
        if (today.isBefore(startDate)) {
            return 0;
        }
        return Math.min(ChronoUnit.DAYS.between(startDate, today) + 1, totalDays(start, end));
    }
}
