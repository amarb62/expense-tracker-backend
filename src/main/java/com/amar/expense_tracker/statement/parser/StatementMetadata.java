package com.amar.expense_tracker.statement.parser;

public record StatementMetadata(
        String institution,
        String accountType,
        String fileName
) {
}
