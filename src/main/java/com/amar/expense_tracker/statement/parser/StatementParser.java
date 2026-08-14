package com.amar.expense_tracker.statement.parser;

import java.io.InputStream;
import java.util.List;

/**
 * Strategy interface for bank-specific statement parsing (design.md section 8).
 * Implementations are Spring beans; {@link com.amar.expense_tracker.statement.service.StatementProcessingService}
 * picks the first one whose {@link #supports(StatementMetadata)} returns true.
 */
public interface StatementParser {

    boolean supports(StatementMetadata metadata);

    List<RawTransaction> parse(InputStream pdf);
}
