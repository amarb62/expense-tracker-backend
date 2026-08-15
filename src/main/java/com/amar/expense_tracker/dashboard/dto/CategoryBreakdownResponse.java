package com.amar.expense_tracker.dashboard.dto;

import java.math.BigDecimal;

public record CategoryBreakdownResponse(
        String category,
        BigDecimal amount,
        BigDecimal percentageOfExpenses,
        BigDecimal percentageOfCredit
) {
}
