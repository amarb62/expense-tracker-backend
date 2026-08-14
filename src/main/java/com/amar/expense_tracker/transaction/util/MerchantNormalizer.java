package com.amar.expense_tracker.transaction.util;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
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

    // Corporate suffixes (SWIGGY PVT LTD -> SWIGGY) and Indian payment-rail
    // prefixes (UPI-SUMAN GURJAR... -> SUMAN GURJAR...) are both "not the
    // merchant/payee identity" tokens, skipped the same way when picking the
    // first meaningful token.
    private static final Set<String> SKIP_TOKENS = Set.of(
            "PVT", "LTD", "LIMITED", "LLC", "INC", "PRIVATE", "CO", "COMPANY",
            "UPI", "NEFT", "IMPS", "RTGS", "ECS", "NACH", "ACH");

    private static final Pattern REFERENCE_MARKER = Pattern.compile("[*#/].*$");
    private static final Pattern DIGIT_RUN = Pattern.compile("\\d{4,}");
    private static final Pattern NON_ALNUM_SPACE = Pattern.compile("[^A-Z0-9 ]");
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s{2,}");

    public String normalize(String rawDescription) {
        if (rawDescription == null || rawDescription.isBlank()) {
            return "";
        }

        String text = rawDescription.toUpperCase(Locale.ROOT).trim();
        text = REFERENCE_MARKER.matcher(text).replaceAll("");
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
