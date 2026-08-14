package com.amar.expense_tracker.transaction.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CategoryPatchRequest(
        @NotNull UUID categoryId,
        Boolean createRule
) {
}
