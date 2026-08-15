package com.amar.expense_tracker.dashboard.dto;

import java.math.BigDecimal;

public record TrendPoint(
        String period,
        BigDecimal totalCredited,
        BigDecimal totalExpenses
) {
}
