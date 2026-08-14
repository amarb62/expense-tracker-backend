package com.amar.expense_tracker.statement.event;

import java.util.UUID;

public record StatementUploadedEvent(
        UUID statementId,
        UUID accountId,
        UUID userId,
        String storageKey
) {
}
