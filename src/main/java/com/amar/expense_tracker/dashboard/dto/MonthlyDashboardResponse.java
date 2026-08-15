package com.amar.expense_tracker.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

public record MonthlyDashboardResponse(
        String period,
        BigDecimal totalCredited,
        BigDecimal totalExpenses,
        BigDecimal remaining,
        BigDecimal expensePercentage,
        List<CategoryBreakdownResponse> categories
) {
}
