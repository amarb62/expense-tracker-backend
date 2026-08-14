package com.amar.expense_tracker.categorization.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CategorizationPatchRequest(
        @NotNull UUID categoryId,
        Boolean createRule
) {
}
