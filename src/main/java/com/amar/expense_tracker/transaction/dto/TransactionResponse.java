package com.amar.expense_tracker.transaction.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID accountId,
        UUID statementId,
        UUID categoryId,
        LocalDate transactionDate,
        String description,
        String normalizedMerchant,
        BigDecimal amount,
        TransactionType transactionType,
        TransactionSource source,
        BigDecimal confidenceScore,
        boolean isManuallyAdded,
        boolean isCategoryModified,
        Instant createdAt,
        Instant updatedAt
) {
}
