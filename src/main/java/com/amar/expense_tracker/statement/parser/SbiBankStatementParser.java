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
 * SBI's netbanking "Statement of Account" export. Like
 * {@link HdfcBankStatementParser}, PDFBox's default (content-stream-order)
 * extraction does not preserve visual row order here -- dates, narrations and
 * amount columns come out grouped separately rather than row by row. Unlike
 * HDFC, requesting position-sorted extraction
 * ({@code PDFTextStripper.setSortByPosition(true)}, see
 * {@link AbstractPdfStatementParser#extractText(InputStream, boolean)})
 * reliably reassembles each row onto one logical text line:
 * "&lt;txn date&gt; &lt;value date&gt; &lt;narration fragment&gt; &lt;cheque/ref no or -&gt; &lt;debit or -&gt; &lt;credit or -&gt; &lt;balance&gt;".
 * Debit/credit are explicit columns in this layout (unlike HDFC's
 * running-balance-only export), so direction and amount are read directly
 * instead of inferred from a balance delta.
 * <p>
 * Each row's narration still wraps across 2-4 physical lines. The line
 * immediately preceding the merged data line (e.g. "WDL TFR", "DEP TFR") is
 * always that row's own leading fragment and is reattached as a prefix.
 * Continuation lines that follow the merged data line (payee VPA/bank detail,
 * the branch reference line) are not reattached -- the same accepted
 * trade-off {@link HdfcBankStatementParser} documents, and for the same reason:
 * in every sample seen, the merchant-identifying token already appears in the
 * fragment captured on the merged data line itself (e.g.
 * "UPI/DR/&lt;ref&gt;/BLINKIT/AIRP/blinkitjkb/Pay v" -- "BLINKIT" is already
 * present before the line wraps).
 */
@Component
@Order(10)
public class SbiBankStatementParser extends AbstractPdfStatementParser {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Pattern TRANSACTION_ROW = Pattern.compile(
            "(\\d{2}/\\d{2}/\\d{4})\\s+\\d{2}/\\d{2}/\\d{4}\\s+(.+?)\\s+"
                    + "(-|[\\d,]+\\.\\d{2})\\s+(-|[\\d,]+\\.\\d{2})\\s+(-|[\\d,]+\\.\\d{2})\\s+([\\d,]+\\.\\d{2})");

    @Override
    public boolean supports(StatementMetadata metadata) {
        String institution = metadata.institution() == null ? "" : metadata.institution().toUpperCase(Locale.ROOT);
        return (institution.contains("SBI") || institution.contains("STATE BANK"))
                && "BANK_ACCOUNT".equals(metadata.accountType());
    }

    @Override
    public List<RawTransaction> parse(InputStream pdf) {
        String text = extractText(pdf, true);

        List<RawTransaction> transactions = new ArrayList<>();
        Matcher rowMatcher = TRANSACTION_ROW.matcher(text);
        int cursor = 0;

        while (rowMatcher.find()) {
            String precedingBlob = text.substring(cursor, rowMatcher.start());
            cursor = rowMatcher.end();

            LocalDate date = LocalDate.parse(rowMatcher.group(1), DATE_FORMAT);
            String header = lastNonBlankLine(precedingBlob);
            String fragment = rowMatcher.group(2).trim();
            String description = header.isEmpty() ? fragment : header + " " + fragment;

            String debit = rowMatcher.group(4);
            String credit = rowMatcher.group(5);

            BigDecimal amount;
            String type;
            if (!"-".equals(debit)) {
                amount = parseAmount(debit);
                type = "DEBIT";
            } else if (!"-".equals(credit)) {
                amount = parseAmount(credit);
                type = "CREDIT";
            } else {
                continue; // neither column populated -- not a real transaction row
            }

            transactions.add(new RawTransaction(date, description, amount, type));
        }
        return transactions;
    }

    private String lastNonBlankLine(String blob) {
        String[] lines = blob.split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            String trimmed = lines[i].trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return "";
    }

    private BigDecimal parseAmount(String raw) {
        return new BigDecimal(raw.replace(",", ""));
    }
}
