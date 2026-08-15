package com.amar.expense_tracker.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

public record YearlyDashboardResponse(
        int year,
        BigDecimal totalCredited,
        BigDecimal totalExpenses,
        BigDecimal remaining,
        BigDecimal expensePercentage,
        List<CategoryBreakdownResponse> categories,
        List<MonthlyBreakdownItem> months
) {
}
