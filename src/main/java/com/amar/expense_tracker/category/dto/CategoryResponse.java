package com.amar.expense_tracker.category.dto;

import java.util.List;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        CategoryType categoryType,
        UUID parentId,
        boolean active,
        List<CategoryResponse> children
) {
}
