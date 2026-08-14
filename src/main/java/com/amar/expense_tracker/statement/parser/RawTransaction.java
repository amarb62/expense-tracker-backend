package com.amar.expense_tracker.statement.parser;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A transaction line as extracted from a statement PDF, before merchant
 * normalization, fingerprinting, or persistence. {@code amount} is always a
 * positive magnitude; {@code transactionType} (e.g. DEBIT/CREDIT) carries the
 * direction.
 */
public record RawTransaction(
        LocalDate transactionDate,
        String description,
        BigDecimal amount,
        String transactionType
) {
}
