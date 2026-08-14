package com.amar.expense_tracker.ai;

import java.util.UUID;

public record CategorySuggestion(
        UUID transactionId,
        String category,
        double confidence,
        String reason,
        boolean failed
) {
    public static CategorySuggestion failed(UUID transactionId) {
        return new CategorySuggestion(transactionId, null, 0.0, null, true);
    }
}
