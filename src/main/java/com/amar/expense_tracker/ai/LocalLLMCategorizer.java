package com.amar.expense_tracker.ai;

import com.amar.expense_tracker.category.repository.CategoryRepository;
import com.amar.expense_tracker.categorization.config.ExpenseAiProperties;
import com.amar.expense_tracker.entity.Categories;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Calls a local Ollama HTTP endpoint (no API key). Deliberately calls Ollama
 * once per transaction rather than asking for a single JSON array covering the
 * whole batch: a small local model following an exact-array-length instruction
 * reliably is a real risk, and one bad response must not take down the rest of
 * the batch (design.md section 13: "AI failures must not crash statement
 * processing"). Each call is independently try-caught.
 */
@Component
@RequiredArgsConstructor
public class LocalLLMCategorizer implements ExpenseCategorizationAI {

    private static final Logger log = LoggerFactory.getLogger(LocalLLMCategorizer.class);

    // Built locally rather than injected: no Spring-managed bean of this classic
    // Jackson type exists on this Spring Boot version (defaults to Jackson 3.x).
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ExpenseAiProperties properties;
    private final CategoryRepository categoryRepository;
    private final RestClient restClient = RestClient.create();

    @Override
    public CategorizationResult categorize(List<TransactionForCategorization> transactions) {
        List<String> validCategories = categoryRepository.findByCategoryTypeAndActiveTrue("EXPENSE").stream()
                .map(Categories::getName)
                .toList();

        List<CategorySuggestion> suggestions = transactions.stream()
                .map(transaction -> categorizeOne(transaction, validCategories))
                .toList();
        return new CategorizationResult(suggestions);
    }

    private CategorySuggestion categorizeOne(TransactionForCategorization transaction, List<String> validCategories) {
        try {
            String prompt = buildPrompt(transaction, validCategories);
            String rawResponse = callOllama(prompt);
            return parseResponse(transaction.transactionId(), rawResponse, validCategories);
        } catch (Exception e) {
            log.warn("Local LLM categorization failed for transaction [{}]: {}",
                    transaction.transactionId(), e.getMessage());
            return CategorySuggestion.failed(transaction.transactionId());
        }
    }

    private String buildPrompt(TransactionForCategorization transaction, List<String> validCategories) {
        return """
                You are a financial transaction categorizer. Categorize the following transaction \
                into exactly one of these categories: %s.

                Merchant: %s
                Amount: %s
                Type: %s

                Respond with ONLY a JSON object in this exact format, no other text, no markdown:
                {"category": "CATEGORY_NAME", "confidence": 0.0-1.0, "reason": "short explanation"}
                """.formatted(
                String.join(", ", validCategories),
                transaction.normalizedMerchant(),
                transaction.amount(),
                transaction.transactionType());
    }

    private String callOllama(String prompt) {
        Map<String, Object> requestBody = Map.of(
                "model", properties.getLocal().getModel(),
                "prompt", prompt,
                "stream", false,
                "format", "json");

        Map<?, ?> response = restClient.post()
                .uri(properties.getLocal().getBaseUrl() + "/api/generate")
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        Object text = response != null ? response.get("response") : null;
        if (text == null) {
            throw new IllegalStateException("Ollama response missing 'response' field");
        }
        return text.toString();
    }

    private CategorySuggestion parseResponse(UUID transactionId, String rawJson, List<String> validCategories) {
        JsonNode node;
        try {
            node = objectMapper.readTree(rawJson);
        } catch (Exception e) {
            log.warn("AI response was not valid JSON for transaction [{}]", transactionId);
            return CategorySuggestion.failed(transactionId);
        }

        JsonNode categoryNode = node.get("category");
        JsonNode confidenceNode = node.get("confidence");
        if (categoryNode == null || confidenceNode == null || !confidenceNode.isNumber()) {
            return CategorySuggestion.failed(transactionId);
        }

        String category = categoryNode.asText();
        double confidence = confidenceNode.asDouble();
        String reason = node.has("reason") ? node.get("reason").asText() : null;

        boolean categoryValid = validCategories.stream()
                .anyMatch(valid -> valid.equalsIgnoreCase(category));
        boolean confidenceValid = confidence >= 0.0 && confidence <= 1.0;
        if (!categoryValid || !confidenceValid) {
            return CategorySuggestion.failed(transactionId);
        }

        return new CategorySuggestion(transactionId, category.toUpperCase(Locale.ROOT), confidence, reason, false);
    }
}
