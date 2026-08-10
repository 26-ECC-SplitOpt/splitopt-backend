package com.splitopt.backend.budget.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 예산 초과 예측 (API 40).
 *
 * <p>"지금 쓰는 속도대로 가면 예산을 넘을 것 같은가"에 답한다. 예산 대비 현재 사용액만 보는
 * 현황(39)과 달리 <b>기간의 경과 비율</b>이 필요하다: 예산의 80%를 썼어도 일정의 90%가
 * 지났다면 넘지 않을 것이고, 20%가 지났다면 크게 넘을 것이다.
 *
 * <p><b>기준 시간축.</b> {@code budgets}에는 기간 컬럼이 없다. 스키마를 바꾸지 않고 쓸 수 있는
 * 시간축은 모임 일정(첫 일정 시작 ~ 마지막 일정 종료)뿐이라 그것을 기준으로 삼는다. 일정이
 * 없거나 기간이 아직 시작되지 않았으면 <b>예측하지 않는다</b>({@link Basis#NONE}) — 경과
 * 비율이 0이면 나눌 수 없고, 근거 없는 숫자를 그럴듯하게 내보내는 것보다 낫다. 이때 화면은
 * 현황(39)으로 물러선다.
 *
 * @param basis             예측 근거. {@code SCHEDULE}이면 아래 예측 필드가 모두 채워지고,
 *                          {@code NONE}이면 모두 {@code null}이다.
 * @param elapsedRate       기간 경과 비율(%, 소수 첫째 자리). 기간이 끝났으면 100.
 * @param projectedTotal    예상 총 지출 {@code spent / 경과비율}. 기간이 끝났으면 현재 사용액과 같다.
 * @param projectedOverspend 예상 초과액. 넘지 않을 것으로 보이면 0이다(음수로 두지 않는다 —
 *                          "얼마 남는지"는 현황(39)의 {@code remaining}이 답한다).
 * @param willExceed        예상 총 지출이 예산을 넘는지
 */
public record BudgetForecastResponse(
        Long groupId,
        BigDecimal amount,
        BigDecimal spent,
        Basis basis,
        LocalDateTime periodStart,
        LocalDateTime periodEnd,
        BigDecimal elapsedRate,
        BigDecimal projectedTotal,
        BigDecimal projectedOverspend,
        Boolean willExceed
) {
    /** 예측 근거. 지금은 일정 기반뿐이고, 기준이 늘어나면 여기에 추가한다. */
    public enum Basis {
        /** 모임 일정 기간의 경과 비율로 예측 */
        SCHEDULE,
        /** 예측 불가 — 일정이 없거나 기간이 아직 시작되지 않음 */
        NONE
    }

    /** 예측 불가. 예산·사용액만 담고 예측 필드는 비운다. */
    public static BudgetForecastResponse notForecastable(Long groupId, BigDecimal amount, BigDecimal spent) {
        return new BudgetForecastResponse(groupId, amount, spent, Basis.NONE,
                null, null, null, null, null, null);
    }

    /**
     * 일정 기간을 기준으로 예측한다.
     *
     * @param elapsedRatio 경과 비율 (0 초과 1 이하). 0이면 나눌 수 없으므로 호출 전에 걸러야 한다.
     */
    public static BudgetForecastResponse ofSchedule(Long groupId, BigDecimal amount, BigDecimal spent,
                                                    LocalDateTime periodStart, LocalDateTime periodEnd,
                                                    BigDecimal elapsedRatio) {
        BigDecimal projectedTotal = spent.divide(elapsedRatio, 2, RoundingMode.HALF_UP);
        BigDecimal overspend = projectedTotal.subtract(amount).max(BigDecimal.ZERO);
        return new BudgetForecastResponse(
                groupId, amount, spent, Basis.SCHEDULE,
                periodStart, periodEnd,
                elapsedRatio.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP),
                projectedTotal,
                overspend,
                projectedTotal.compareTo(amount) > 0);
    }

    /**
     * 기간 중 지금까지의 경과 비율. 기간이 끝났으면 1, 시작 전이면 0이다.
     *
     * <p>시작·종료가 같은 일정(종료 시각 없는 단일 일정)은 길이가 0이라 나눌 수 없다.
     * 그 시점을 지났으면 다 지난 것으로 보고 1, 아니면 0을 돌려준다.
     *
     * @return 0 이상 1 이하. 0이면 예측 불가를 뜻한다.
     */
    public static BigDecimal elapsedRatio(LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        if (!now.isAfter(start)) {
            return BigDecimal.ZERO;
        }
        if (!now.isBefore(end)) {
            return BigDecimal.ONE;
        }
        long total = Duration.between(start, end).toSeconds();
        if (total <= 0) {
            return BigDecimal.ONE;
        }
        return BigDecimal.valueOf(Duration.between(start, now).toSeconds())
                .divide(BigDecimal.valueOf(total), 6, RoundingMode.HALF_UP);
    }
}
