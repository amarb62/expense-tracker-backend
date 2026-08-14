package com.amar.expense_tracker.account.dto;

import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String name,
        String institution,
        AccountType accountType,
        String lastFourDigits,
        String currency,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
