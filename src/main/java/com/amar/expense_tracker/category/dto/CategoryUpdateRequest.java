package com.amar.expense_tracker.category.dto;

import java.util.UUID;

/**
 * PATCH semantics: every field is optional; only non-null fields are applied.
 */
public record CategoryUpdateRequest(
        String name,
        UUID parentId,
        String color
) {
}
