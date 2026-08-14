package com.amar.expense_tracker.common.util;

import java.sql.Date;
import java.time.LocalDate;

/**
 * Hibernate returns {@code java.sql.Date} for {@code @Temporal(TemporalType.DATE)}
 * columns, and {@code java.sql.Date.toInstant()} throws
 * {@code UnsupportedOperationException} by design (a DATE has no time-of-day
 * component to convert). Re-wrapping as a {@code java.sql.Date} guarantees a safe
 * conversion regardless of the actual {@code java.util.Date} subtype returned.
 */
public final class JpaDateUtils {

    private JpaDateUtils() {
    }

    public static LocalDate toLocalDate(java.util.Date date) {
        return date != null ? new Date(date.getTime()).toLocalDate() : null;
    }
}
