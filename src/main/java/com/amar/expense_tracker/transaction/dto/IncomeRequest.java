package com.amar.expense_tracker.transaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record IncomeRequest(
        @NotNull @Positive BigDecimal amount,
        @NotNull LocalDate date,
        @NotBlank String description,
        @NotNull UUID categoryId,
        @NotNull UUID accountId
) {
}
