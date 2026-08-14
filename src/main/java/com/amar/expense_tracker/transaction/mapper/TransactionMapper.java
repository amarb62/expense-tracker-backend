package com.amar.expense_tracker.transaction.mapper;

import com.amar.expense_tracker.common.util.JpaDateUtils;
import com.amar.expense_tracker.entity.Transactions;
import com.amar.expense_tracker.transaction.dto.TransactionResponse;
import com.amar.expense_tracker.transaction.dto.TransactionSource;
import com.amar.expense_tracker.transaction.dto.TransactionType;
import org.springframework.stereotype.Component;

@Component
public class TransactionMapper {

    public TransactionResponse toResponse(Transactions transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAccounts().getId(),
                transaction.getStatements() != null ? transaction.getStatements().getId() : null,
                transaction.getCategories() != null ? transaction.getCategories().getId() : null,
                JpaDateUtils.toLocalDate(transaction.getTransactionDate()),
                transaction.getDescription(),
                transaction.getNormalizedMerchant(),
                transaction.getAmount(),
                TransactionType.valueOf(transaction.getTransactionType()),
                TransactionSource.valueOf(transaction.getSource()),
                transaction.getConfidenceScore(),
                transaction.isIsManuallyAdded(),
                transaction.isIsCategoryModified(),
                transaction.getCreatedAt().toInstant(),
                transaction.getUpdatedAt().toInstant()
        );
    }
}
