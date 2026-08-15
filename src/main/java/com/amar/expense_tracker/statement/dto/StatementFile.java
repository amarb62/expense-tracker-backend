package com.amar.expense_tracker.statement.dto;

/**
 * The raw, originally-stored PDF bytes for a statement plus its original file name,
 * handed back to the controller so it can set {@code Content-Disposition} correctly.
 */
public record StatementFile(String fileName, byte[] content) {
}
