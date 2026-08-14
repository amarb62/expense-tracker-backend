package com.amar.expense_tracker.categorization.service;

import com.amar.expense_tracker.ai.CategorizationResult;
import com.amar.expense_tracker.ai.CategorySuggestion;
import com.amar.expense_tracker.ai.ExpenseCategorizationAI;
import com.amar.expense_tracker.ai.TransactionForCategorization;
import com.amar.expense_tracker.categorization.config.ExpenseAiProperties;
import com.amar.expense_tracker.categorization.repository.AiCategorizationRepository;
import com.amar.expense_tracker.category.repository.CategoryRepository;
import com.amar.expense_tracker.entity.AiCategorization;
import com.amar.expense_tracker.entity.Categories;
import com.amar.expense_tracker.entity.Transactions;
import com.amar.expense_tracker.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates design.md section 11's order: user rule -> global/rule engine ->
 * AI classifier -> confidence evaluation -> persistence. Runs on PDF-derived
 * DEBIT transactions that don't already have a category (wired in from
 * {@code StatementTransactionPersister} right after each new transaction is
 * saved); manual entries always arrive with a user-picked category already, so
 * they never reach this service.
 * <p>
 * {@code source} (PDF/MANUAL/...) is left untouched here -- it's the
 * transaction's origin, not how its category was decided. Categorization
 * provenance lives in {@code ai_categorization.status} + `confidence_score` +
 * `is_category_modified` instead.
 */
@Service
@RequiredArgsConstructor
public class ExpenseCategorizationService {

    private static final Logger log = LoggerFactory.getLogger(ExpenseCategorizationService.class);
    private static final String OTHER_CATEGORY_NAME = "OTHER";
    private static final String CATEGORIZABLE_TYPE = "DEBIT";

    private final UserCategoryRuleService userCategoryRuleService;
    private final RuleEngine ruleEngine;
    private final ExpenseCategorizationAI expenseCategorizationAI;
    private final ConfidenceEvaluator confidenceEvaluator;
    private final ExpenseAiProperties aiProperties;
    private final CategoryRepository categoryRepository;
    private final AiCategorizationRepository aiCategorizationRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public void categorize(Transactions transaction) {
        if (transaction.getCategories() != null || !CATEGORIZABLE_TYPE.equals(transaction.getTransactionType())) {
            return;
        }

        UUID userId = transaction.getUsers().getId();
        String merchant = transaction.getNormalizedMerchant();

        Optional<Categories> userRuleMatch = userCategoryRuleService.match(userId, merchant);
        if (userRuleMatch.isPresent()) {
            applyDeterministicCategory(transaction, userRuleMatch.get());
            return;
        }

        Optional<Categories> globalRuleMatch = ruleEngine.match(merchant);
        if (globalRuleMatch.isPresent()) {
            applyDeterministicCategory(transaction, globalRuleMatch.get());
            return;
        }

        if (!aiProperties.isEnabled()) {
            applyFallback(transaction, null);
            return;
        }

        CategorySuggestion suggestion;
        try {
            TransactionForCategorization input = new TransactionForCategorization(
                    transaction.getId(), merchant, transaction.getAmount(), transaction.getTransactionType());
            CategorizationResult result = expenseCategorizationAI.categorize(List.of(input));
            suggestion = result.suggestions().isEmpty() ? null : result.suggestions().get(0);
        } catch (Exception e) {
            log.warn("AI categorization threw for transaction [{}]: {}", transaction.getId(), e.getMessage());
            suggestion = null;
        }

        applyAiResult(transaction, suggestion);
    }

    private void applyDeterministicCategory(Transactions transaction, Categories category) {
        transaction.setCategories(category);
        transaction.setConfidenceScore(BigDecimal.ONE);
        transactionRepository.save(transaction);
    }

    private void applyAiResult(Transactions transaction, CategorySuggestion suggestion) {
        if (suggestion == null || suggestion.failed() || !confidenceEvaluator.isValid(suggestion.confidence())) {
            recordAiCategorization(transaction, null, null, null, "FAILED");
            applyFallback(transaction, null);
            return;
        }

        Categories predicted = categoryRepository.findByNameIgnoreCaseAndActiveTrue(suggestion.category())
                .orElse(null);
        if (predicted == null) {
            recordAiCategorization(transaction, null, suggestion.confidence(), suggestion.reason(), "FAILED");
            applyFallback(transaction, null);
            return;
        }

        String status = confidenceEvaluator.isAutoApproved(suggestion.confidence()) ? "AUTO_APPROVED" : "NEEDS_REVIEW";
        recordAiCategorization(transaction, predicted, suggestion.confidence(), suggestion.reason(), status);

        transaction.setCategories(predicted);
        transaction.setConfidenceScore(BigDecimal.valueOf(suggestion.confidence()));
        transactionRepository.save(transaction);
    }

    private void applyFallback(Transactions transaction, BigDecimal confidence) {
        Categories other = categoryRepository.findByNameIgnoreCaseAndActiveTrue(OTHER_CATEGORY_NAME)
                .orElseThrow(() -> new IllegalStateException("OTHER category is missing"));
        transaction.setCategories(other);
        transaction.setConfidenceScore(confidence);
        transactionRepository.save(transaction);
    }

    private void recordAiCategorization(Transactions transaction, Categories predicted, Double confidence,
                                         String reasoning, String status) {
        AiCategorization record = new AiCategorization();
        record.setTransactions(transaction);
        record.setCategories(predicted);
        record.setModelName(aiProperties.getLocal().getModel());
        record.setConfidence(confidence != null ? BigDecimal.valueOf(confidence) : null);
        record.setReasoning(reasoning);
        record.setStatus(status);
        record.setCreatedAt(Date.from(Instant.now()));
        aiCategorizationRepository.save(record);
    }
}
