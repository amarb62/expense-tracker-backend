package com.amar.expense_tracker.categorization.service;

import com.amar.expense_tracker.categorization.dto.CategorizationPatchRequest;
import com.amar.expense_tracker.categorization.dto.ReviewItemResponse;
import com.amar.expense_tracker.categorization.mapper.ReviewItemMapper;
import com.amar.expense_tracker.categorization.repository.AiCategorizationRepository;
import com.amar.expense_tracker.category.repository.CategoryRepository;
import com.amar.expense_tracker.common.exception.BadRequestException;
import com.amar.expense_tracker.common.exception.ResourceNotFoundException;
import com.amar.expense_tracker.entity.AiCategorization;
import com.amar.expense_tracker.entity.Categories;
import com.amar.expense_tracker.entity.Transactions;
import com.amar.expense_tracker.transaction.dto.TransactionResponse;
import com.amar.expense_tracker.transaction.mapper.TransactionMapper;
import com.amar.expense_tracker.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The user-facing side of categorization (design.md section 18): reviewing,
 * approving, or correcting what {@link ExpenseCategorizationService} decided
 * automatically.
 */
@Service
@RequiredArgsConstructor
public class CategorizationReviewService {

    private static final String NEEDS_REVIEW = "NEEDS_REVIEW";

    private final AiCategorizationRepository aiCategorizationRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final UserCategoryRuleService userCategoryRuleService;
    private final ReviewItemMapper reviewItemMapper;
    private final TransactionMapper transactionMapper;

    @Transactional(readOnly = true)
    public List<ReviewItemResponse> listPendingReview(UUID userId) {
        return aiCategorizationRepository.findByTransactions_Users_IdAndStatusOrderByCreatedAtDesc(userId, NEEDS_REVIEW)
                .stream()
                .map(reviewItemMapper::toResponse)
                .toList();
    }

    @Transactional
    public TransactionResponse approve(UUID userId, UUID transactionId) {
        Transactions transaction = findOwned(userId, transactionId);
        AiCategorization aiCategorization = aiCategorizationRepository
                .findTopByTransactions_IdOrderByCreatedAtDesc(transactionId)
                .filter(entry -> NEEDS_REVIEW.equals(entry.getStatus()))
                .orElseThrow(() -> new BadRequestException("This transaction has no pending AI categorization to approve"));

        aiCategorization.setStatus("USER_APPROVED");
        aiCategorizationRepository.save(aiCategorization);

        return transactionMapper.toResponse(transaction);
    }

    @Transactional
    public TransactionResponse patch(UUID userId, UUID transactionId, CategorizationPatchRequest request) {
        Transactions transaction = findOwned(userId, transactionId);
        Categories category = categoryRepository.findById(request.categoryId())
                .filter(Categories::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        boolean categoryChanged = !Objects.equals(
                transaction.getCategories() != null ? transaction.getCategories().getId() : null,
                category.getId());

        transaction.setCategories(category);
        if (categoryChanged) {
            transaction.setIsCategoryModified(true);
        }
        transaction.setUpdatedAt(Date.from(Instant.now()));
        Transactions saved = transactionRepository.save(transaction);

        aiCategorizationRepository.findTopByTransactions_IdOrderByCreatedAtDesc(transactionId)
                .ifPresent(entry -> {
                    entry.setStatus("USER_CORRECTED");
                    aiCategorizationRepository.save(entry);
                });

        if (Boolean.TRUE.equals(request.createRule())
                && saved.getNormalizedMerchant() != null
                && !saved.getNormalizedMerchant().isBlank()) {
            userCategoryRuleService.upsert(userId, saved.getNormalizedMerchant(), category);
        }

        return transactionMapper.toResponse(saved);
    }

    private Transactions findOwned(UUID userId, UUID transactionId) {
        return transactionRepository.findByIdAndUsers_Id(transactionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
    }
}
