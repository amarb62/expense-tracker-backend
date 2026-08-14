package com.amar.expense_tracker.ai;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The only transaction fields sent to an AI provider (design.md section 12):
 * never password, account number, full statement, balance, or unrelated
 * personal data.
 */
public record TransactionForCategorization(
        UUID transactionId,
        String normalizedMerchant,
        BigDecimal amount,
        String transactionType
) {
}
