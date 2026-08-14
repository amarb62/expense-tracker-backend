package com.amar.expense_tracker.statement.dto;

import com.amar.expense_tracker.account.dto.AccountType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StatementResponse(
        UUID id,
        UUID accountId,
        String fileName,
        AccountType statementType,
        LocalDate statementStartDate,
        LocalDate statementEndDate,
        StatementStatus status,
        Instant uploadedAt,
        Instant processedAt,
        String errorMessage
) {
}
