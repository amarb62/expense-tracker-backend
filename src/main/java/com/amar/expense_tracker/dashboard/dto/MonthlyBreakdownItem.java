package com.amar.expense_tracker.dashboard.dto;

import java.math.BigDecimal;

public record MonthlyBreakdownItem(
        int month,
        BigDecimal totalCredited,
        BigDecimal totalExpenses,
        BigDecimal remaining,
        BigDecimal expensePercentage
) {
}
