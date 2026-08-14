package com.amar.expense_tracker.categorization.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ReviewItemResponse(
        UUID transactionId,
        UUID accountId,
        LocalDate transactionDate,
        String description,
        String normalizedMerchant,
        BigDecimal amount,
        UUID suggestedCategoryId,
        String suggestedCategoryName,
        BigDecimal confidence,
        String reason
) {
}
