package com.amar.expense_tracker.categorization.mapper;

import com.amar.expense_tracker.categorization.dto.ReviewItemResponse;
import com.amar.expense_tracker.common.util.JpaDateUtils;
import com.amar.expense_tracker.entity.AiCategorization;
import com.amar.expense_tracker.entity.Transactions;
import org.springframework.stereotype.Component;

@Component
public class ReviewItemMapper {

    public ReviewItemResponse toResponse(AiCategorization aiCategorization) {
        Transactions transaction = aiCategorization.getTransactions();
        return new ReviewItemResponse(
                transaction.getId(),
                transaction.getAccounts().getId(),
                JpaDateUtils.toLocalDate(transaction.getTransactionDate()),
                transaction.getDescription(),
                transaction.getNormalizedMerchant(),
                transaction.getAmount(),
                aiCategorization.getCategories() != null ? aiCategorization.getCategories().getId() : null,
                aiCategorization.getCategories() != null ? aiCategorization.getCategories().getName() : null,
                aiCategorization.getConfidence(),
                aiCategorization.getReasoning()
        );
    }
}
