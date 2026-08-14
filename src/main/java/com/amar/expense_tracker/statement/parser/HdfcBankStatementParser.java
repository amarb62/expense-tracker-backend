package com.amar.expense_tracker.statement.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
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
 * HDFC's netbanking statement export does not come out of PDFBox's default text
 * stripper as one line per transaction: each row's narration wraps across 2-4
 * physical lines, and the numeric row (ref no / value date / amount / running
 * balance) can land anywhere within that wrapped block depending on internal PDF
 * content-stream ordering, not visual layout. Rather than parse a per-line
 * pattern (which breaks on this layout), this scans the whole text for the
 * numeric-row pattern and treats the running-balance delta between consecutive
 * rows as the source of truth for both the amount and DEBIT/CREDIT direction --
 * more robust than trusting column position, which this layout doesn't reliably
 * preserve.
 * <p>
 * Known limitation: a narration fragment that gets shuffled to <em>after</em> its
 * own numeric row (and before the next transaction's date) is currently dropped
 * rather than reattached -- in samples seen so far this is always a trailing
 * bank-reference-code fragment (e.g. "...-UPI"), not merchant-identifying text,
 * so it doesn't affect {@link com.amar.expense_tracker.transaction.util.MerchantNormalizer}
 * output, only the completeness of the raw description.
 */
@Component
@Order(10)
public class HdfcBankStatementParser implements StatementParser {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yy");

    private static final Pattern NUMERIC_ROW = Pattern.compile(
            "\\d{6,}\\s+(\\d{2}/\\d{2}/\\d{2})\\s+([\\d,]+\\.\\d{2})\\s+([\\d,]+\\.\\d{2})");
    private static final Pattern LEADING_DATE = Pattern.compile("(\\d{2}/\\d{2}/\\d{2})");
    private static final Pattern OPENING_BALANCE = Pattern.compile(
            "Opening Balance.*?Closing Bal\\s*[\\r\\n]+\\s*([\\d,]+\\.\\d{2})", Pattern.DOTALL);

    @Override
    public boolean supports(StatementMetadata metadata) {
        return metadata.institution() != null
                && metadata.institution().toUpperCase(Locale.ROOT).contains("HDFC")
                && "BANK_ACCOUNT".equals(metadata.accountType());
    }

    @Override
    public List<RawTransaction> parse(InputStream pdf) {
        String text = extractText(pdf);

        BigDecimal previousBalance = extractOpeningBalance(text);
        if (previousBalance == null) {
            throw new StatementParsingException("Could not locate the opening balance in the HDFC statement", null);
        }

        List<RawTransaction> transactions = new ArrayList<>();
        Matcher rowMatcher = NUMERIC_ROW.matcher(text);
        int cursor = 0;

        while (rowMatcher.find()) {
            String narrationBlob = text.substring(cursor, rowMatcher.start());
            cursor = rowMatcher.end();

            ParsedNarration narration = parseNarration(narrationBlob);
            if (narration == null) {
                continue; // header/boilerplate before the first transaction, not a transaction block
            }

            BigDecimal balance = parseAmount(rowMatcher.group(3));
            BigDecimal delta = balance.subtract(previousBalance);
            previousBalance = balance;
            if (delta.signum() == 0) {
                continue;
            }

            String type = delta.signum() > 0 ? "CREDIT" : "DEBIT";
            transactions.add(new RawTransaction(narration.date(), narration.description(), delta.abs(), type));
        }
        return transactions;
    }

    private ParsedNarration parseNarration(String blob) {
        Matcher dateMatcher = LEADING_DATE.matcher(blob);
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

    private BigDecimal extractOpeningBalance(String text) {
        Matcher matcher = OPENING_BALANCE.matcher(text);
        return matcher.find() ? parseAmount(matcher.group(1)) : null;
    }

    private BigDecimal parseAmount(String raw) {
        return new BigDecimal(raw.replace(",", ""));
    }

    private String extractText(InputStream pdf) {
        try {
            byte[] bytes = pdf.readAllBytes();
            try (PDDocument document = Loader.loadPDF(bytes)) {
                return new PDFTextStripper().getText(document);
            }
        } catch (IOException e) {
            throw new StatementParsingException("Unable to read PDF content", e);
        }
    }

    private record ParsedNarration(LocalDate date, String description) {
    }
}
