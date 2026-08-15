package com.amar.expense_tracker.category.dto;

import java.util.UUID;

public record CategoryDeactivateRequest(
        UUID replacementCategoryId
) {
}
