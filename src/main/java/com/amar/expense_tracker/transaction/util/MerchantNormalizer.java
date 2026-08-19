package com.amar.expense_tracker.transaction.util;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Collapses raw statement descriptions (e.g. "SWIGGY*ORDER12345",
 * "SWIGGY PVT LTD", "SWIGGY INSTAMART PUNE") to a single merchant token
 * ("SWIGGY") so the same merchant is recognized consistently regardless of
 * per-transaction reference codes or branch/city suffixes. The raw description
 * is never modified in the database -- this only produces normalized_merchant.
 */
@Component
public class MerchantNormalizer {

    // Corporate suffixes (SWIGGY PVT LTD -> SWIGGY), Indian payment-rail
    // prefixes (UPI-SUMAN GURJAR... -> SUMAN GURJAR...), and bank narration
    // jargon carried over verbatim from source statements (SBI's "WDL TFR"/
    // "DEP TFR" transaction-category prefixes) are all "not the merchant/payee
    // identity" tokens, skipped the same way when picking the first
    // meaningful token.
    private static final Set<String> SKIP_TOKENS = Set.of(
            "PVT", "LTD", "LIMITED", "LLC", "INC", "PRIVATE", "CO", "COMPANY",
            "UPI", "NEFT", "IMPS", "RTGS", "ECS", "NACH", "ACH",
            "WDL", "DEP", "TFR", "DR", "CR");

    // SBI narrations encode the payee/merchant AFTER the "UPI/DR|CR/<ref>/"
    // segment (e.g. "UPI/DR/311589015801/BLINKIT/AIRP/blinkitjkb/Pay v"), not
    // before it -- the generic REFERENCE_MARKER truncation below would
    // otherwise discard exactly that part, since it cuts at the very first "/"
    // (right after "UPI"). Matched and stripped before the generic path runs.
    private static final Pattern UPI_TXN_PREFIX = Pattern.compile("UPI/(?:DR|CR)/\\d+/");
    private static final Pattern REFERENCE_MARKER = Pattern.compile("[*#/].*$");
    private static final Pattern DIGIT_RUN = Pattern.compile("\\d{4,}");
    private static final Pattern NON_ALNUM_SPACE = Pattern.compile("[^A-Z0-9 ]");
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s{2,}");

    public String normalize(String rawDescription) {
        if (rawDescription == null || rawDescription.isBlank()) {
            return "";
        }

        String text = rawDescription.toUpperCase(Locale.ROOT).trim();

        Matcher upiPrefixMatcher = UPI_TXN_PREFIX.matcher(text);
        if (upiPrefixMatcher.find()) {
            text = upiPrefixMatcher.replaceAll("").replace('/', ' ');
        } else {
            text = REFERENCE_MARKER.matcher(text).replaceAll("");
        }
        text = DIGIT_RUN.matcher(text).replaceAll("");
        text = NON_ALNUM_SPACE.matcher(text).replaceAll(" ");
        text = MULTI_SPACE.matcher(text).replaceAll(" ").trim();

        for (String token : text.split(" ")) {
            if (!token.isBlank() && !SKIP_TOKENS.contains(token)) {
                return token;
            }
        }
        return text.isBlank() ? "UNKNOWN" : text;
    }
}
