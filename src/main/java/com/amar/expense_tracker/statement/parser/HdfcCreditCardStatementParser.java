package com.amar.expense_tracker.statement.parser;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HDFC credit card ("Billed Statement") export. Unlike the savings account
 * statement, most transaction rows extract as a single line: "dd/mm/yyyy| hh:mm
 * <description>  C<amount> l". "C" is this document's embedded font rendering
 * the Rupee glyph as the literal letter C (confirmed by inspecting the raw
 * extracted bytes) -- specific to this font, worth re-checking against any
 * differently-generated HDFC card statement. A leading "+" before the amount
 * marks a credit (payment or refund); its absence marks a purchase (debit).
 * There's no running balance to cross-check against here (unlike the savings
 * account statement), so direction is read directly from that "+" marker.
 * "PAYMENT" vs "REFUND" is a heuristic: a credit whose description mentions
 * "PAYMENT" (e.g. "CC PAYMENT ... PayZapp") is typed PAYMENT so downstream
 * analytics can exclude credit-card bill payments from totals per design.md
 * section 21; any other credit is typed REFUND.
 */
@Component
@Order(10)
public class HdfcCreditCardStatementParser extends AbstractPdfStatementParser {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Pattern LEADING_DATE_TIME = Pattern.compile("(\\d{2}/\\d{2}/\\d{4})\\|\\s*\\d{1,2}:\\d{2}");
    private static final Pattern AMOUNT_MARKER = Pattern.compile("(\\+)?\\s*C\\s*([\\d,]+\\.\\d{2})\\s*l\\b");

    @Override
    public boolean supports(StatementMetadata metadata) {
        return metadata.institution() != null
                && metadata.institution().toUpperCase(Locale.ROOT).contains("HDFC")
                && "CREDIT_CARD".equals(metadata.accountType());
    }

    @Override
    public List<RawTransaction> parse(InputStream pdf) {
        String text = extractText(pdf);

        List<RawTransaction> transactions = new ArrayList<>();
        Matcher amountMatcher = AMOUNT_MARKER.matcher(text);
        int cursor = 0;

        while (amountMatcher.find()) {
            String blob = text.substring(cursor, amountMatcher.start());
            cursor = amountMatcher.end();

            ParsedNarration narration = parseNarration(blob);
            if (narration == null) {
                continue; // boilerplate/summary text before the first transaction, or between sections
            }

            BigDecimal amount = new BigDecimal(amountMatcher.group(2).replace(",", ""));
            boolean isCredit = amountMatcher.group(1) != null;
            String type = isCredit
                    ? (narration.description().toUpperCase(Locale.ROOT).contains("PAYMENT") ? "PAYMENT" : "REFUND")
                    : "DEBIT";

            transactions.add(new RawTransaction(narration.date(), narration.description(), amount, type));
        }
        return transactions;
    }

    private ParsedNarration parseNarration(String blob) {
        Matcher dateMatcher = LEADING_DATE_TIME.matcher(blob);
        if (!dateMatcher.find()) {
            return null;
        }
        LocalDate date = LocalDate.parse(dateMatcher.group(1), DATE_FORMAT);
        String description = blob.substring(dateMatcher.end())
                .replace('\n', ' ')
                .replaceAll("\\s{2,}", " ")
                .trim();
        return new ParsedNarration(date, description);
    }

    private record ParsedNarration(LocalDate date, String description) {
    }
}
