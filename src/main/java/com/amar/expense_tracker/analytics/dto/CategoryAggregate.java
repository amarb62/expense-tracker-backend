package com.amar.expense_tracker.analytics.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CategoryAggregate(UUID categoryId, String categoryName, BigDecimal totalAmount) {
}
