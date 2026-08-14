package com.amar.expense_tracker.categorization.service;

import com.amar.expense_tracker.category.repository.CategoryRepository;
import com.amar.expense_tracker.entity.Categories;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Global merchant -> category matching (design.md section 11, steps 2-3).
 * There's no dedicated DB table for global rules in the schema -- only
 * {@code user_category_rules}, which is user-scoped -- so this is a small,
 * deliberately non-exhaustive in-code seed list, easy to extend as more
 * merchants are observed. {@code normalizedMerchant} is already a single
 * uppercase token (MerchantNormalizer), so an exact map lookup is sufficient;
 * no fuzzy/substring matching is needed.
 */
@Component
@RequiredArgsConstructor
public class RuleEngine {

    private static final Map<String, String> GLOBAL_MERCHANT_RULES = Map.ofEntries(
            Map.entry("SWIGGY", "FOOD"),
            Map.entry("ZOMATO", "FOOD"),
            Map.entry("DOMINOS", "FOOD"),
            Map.entry("STARBUCKS", "FOOD"),
            Map.entry("BIGBASKET", "GROCERIES"),
            Map.entry("BLINKIT", "GROCERIES"),
            Map.entry("GROFERS", "GROCERIES"),
            Map.entry("AMAZON", "SHOPPING"),
            Map.entry("FLIPKART", "SHOPPING"),
            Map.entry("MYNTRA", "SHOPPING"),
            Map.entry("UBER", "TRANSPORT"),
            Map.entry("OLA", "TRANSPORT"),
            Map.entry("RAPIDO", "TRANSPORT"),
            Map.entry("NETFLIX", "SUBSCRIPTION"),
            Map.entry("SPOTIFY", "SUBSCRIPTION"),
            Map.entry("HOTSTAR", "SUBSCRIPTION"),
            Map.entry("APOLLO", "HEALTHCARE"),
            Map.entry("PHARMEASY", "HEALTHCARE"),
            Map.entry("IRCTC", "TRAVEL"),
            Map.entry("MAKEMYTRIP", "TRAVEL"),
            Map.entry("ATM", "ATM_CASH")
    );

    private final CategoryRepository categoryRepository;

    public Optional<Categories> match(String normalizedMerchant) {
        if (normalizedMerchant == null || normalizedMerchant.isBlank()) {
            return Optional.empty();
        }
        String categoryName = GLOBAL_MERCHANT_RULES.get(normalizedMerchant.toUpperCase(Locale.ROOT));
        if (categoryName == null) {
            return Optional.empty();
        }
        return categoryRepository.findByNameIgnoreCaseAndActiveTrue(categoryName);
    }
}
