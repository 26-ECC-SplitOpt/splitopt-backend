package com.splitopt.backend.statistics.dto;

import java.math.BigDecimal;

/** 모임 전체 지출 통계 (API 30). */
public record GroupStatisticsResponse(
        BigDecimal totalAmount,      // 총 지출액
        int expenseCount,            // 지출 건수
        BigDecimal averagePerPerson  // 1인 평균 (총액 / 활성 참여자 수)
) {}