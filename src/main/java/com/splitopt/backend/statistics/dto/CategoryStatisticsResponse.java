package com.splitopt.backend.statistics.dto;

import java.math.BigDecimal;
import java.util.List;

/** 카테고리별 지출 통계 (API 31). */
public record CategoryStatisticsResponse(
        BigDecimal totalAmount,
        List<CategoryItem> categories
) {
    public record CategoryItem(
            String category,
            BigDecimal amount,
            BigDecimal percentage // 전체 대비 비율 (%)
    ) {}
}