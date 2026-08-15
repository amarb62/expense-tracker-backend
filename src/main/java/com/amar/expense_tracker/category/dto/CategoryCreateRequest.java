package com.amar.expense_tracker.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CategoryCreateRequest(
        @NotBlank String name,
        UUID parentId,
        @NotBlank String color,
        @NotNull CategoryType categoryType
) {
}
