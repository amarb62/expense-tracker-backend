package com.amar.expense_tracker.ai;

import java.util.List;

/**
 * Provider-agnostic AI categorization abstraction (design.md section 12).
 * {@code LocalLLMCategorizer} is the only implementation for now; OpenAI/Gemini
 * implementations can be added later without touching
 * {@code ExpenseCategorizationService} or anything else upstream.
 */
public interface ExpenseCategorizationAI {

    CategorizationResult categorize(List<TransactionForCategorization> transactions);
}
