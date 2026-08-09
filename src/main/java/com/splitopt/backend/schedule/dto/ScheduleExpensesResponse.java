package com.splitopt.backend.schedule.dto;

import com.splitopt.backend.expense.dto.ExpenseResponse;

import java.math.BigDecimal;
import java.util.List;

public record ScheduleExpensesResponse(
        ScheduleResponse schedule,
        List<ExpenseResponse> expenses,
        BigDecimal totalAmount
) {}