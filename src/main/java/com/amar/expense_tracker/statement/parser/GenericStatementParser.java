package com.amar.expense_tracker.statement.parser;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fallback parser tried when no bank-specific {@link StatementParser} claims the
 * statement. Requires an explicit Dr/Cr marker per transaction line to determine
 * direction -- if a statement's layout omits one (e.g. separate, unmarked debit
 * and credit columns), this parser will not pick up those lines rather than guess
 * and risk corrupting financial totals. Always {@link #supports}; must stay
 * ordered after any bank-specific parser.
 */
@Component
@Order(Integer.MAX_VALUE)
public class GenericStatementParser extends AbstractPdfStatementParser {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Pattern TRANSACTION_LINE = Pattern.compile(
            "^(\\d{2}[/-]\\d{2}[/-]\\d{4})\\s+(.+?)\\s+([\\d,]+\\.\\d{2})\\s*(DR|CR)\\b.*$",
            Pattern.CASE_INSENSITIVE);

    @Override
    public boolean supports(StatementMetadata metadata) {
        return true;
    }

    @Override
    public List<RawTransaction> parse(InputStream pdf) {
        String text = extractText(pdf);

        List<RawTransaction> transactions = new ArrayList<>();
        for (String line : text.split("\\R")) {
            Matcher matcher = TRANSACTION_LINE.matcher(line.trim());
            if (!matcher.matches()) {
                continue;
            }
            LocalDate date = parseDate(matcher.group(1));
            if (date == null) {
                continue;
            }
            String description = matcher.group(2).trim();
            BigDecimal amount = new BigDecimal(matcher.group(3).replace(",", ""));
            String type = "DR".equalsIgnoreCase(matcher.group(4)) ? "DEBIT" : "CREDIT";
            transactions.add(new RawTransaction(date, description, amount, type));
        }
        return transactions;
    }

    private LocalDate parseDate(String raw) {
        try {
            return LocalDate.parse(raw.replace('-', '/'), DATE_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
