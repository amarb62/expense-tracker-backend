package com.amar.expense_tracker.categorization.service;

import com.amar.expense_tracker.categorization.config.ExpenseAiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Design.md section 13's confidence thresholds. Read literally, the spec's own
 * three bullets route BOTH the 0.60-0.85 band and the below-0.60 band to
 * NEEDS_REVIEW (only >=0.85 differs, going to AUTO_APPROVED) -- so this only
 * branches on the auto-approve threshold. The lower "review" threshold is kept
 * in config (and here) as a documented no-op today, available if that
 * literal reading ever needs revisiting. FAILED is reserved separately for
 * genuinely unusable AI output (invalid category/confidence, parse failure),
 * not for a low-but-valid confidence score.
 */
@Component
@RequiredArgsConstructor
public class ConfidenceEvaluator {

    private final ExpenseAiProperties properties;

    public boolean isValid(double confidence) {
        return confidence >= 0.0 && confidence <= 1.0;
    }

    public boolean isAutoApproved(double confidence) {
        return confidence >= properties.getConfidence().getAutoApprove();
    }
}
