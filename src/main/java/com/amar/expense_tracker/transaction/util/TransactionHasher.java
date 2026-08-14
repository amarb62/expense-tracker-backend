package com.amar.expense_tracker.transaction.util;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Fingerprints a transaction from account ID + date + amount + normalized
 * merchant + type (design.md section 10) so re-uploading the same statement
 * does not create duplicate rows -- the hash is stored in a unique DB column.
 * <p>
 * The unique constraint applies to every transaction regardless of source, but
 * dedup-by-hash is only meaningful for PDF-sourced rows (guarding against
 * re-importing the same statement). Two manually-entered transactions can be
 * legitimately identical (e.g. two separate same-day coffees), so manual
 * entries must be hashed with a random {@code salt} to guarantee they never
 * collide -- with each other or, by coincidence, with a real PDF-derived hash.
 */
@Component
public class TransactionHasher {

    public String hash(UUID accountId, LocalDate transactionDate, BigDecimal amount,
                        String normalizedMerchant, String transactionType) {
        return hash(accountId, transactionDate, amount, normalizedMerchant, transactionType, null);
    }

    public String hash(UUID accountId, LocalDate transactionDate, BigDecimal amount,
                        String normalizedMerchant, String transactionType, String salt) {
        String raw = String.join("|",
                accountId.toString(),
                transactionDate.toString(),
                amount.stripTrailingZeros().toPlainString(),
                normalizedMerchant == null ? "" : normalizedMerchant,
                transactionType,
                salt == null ? "" : salt);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
