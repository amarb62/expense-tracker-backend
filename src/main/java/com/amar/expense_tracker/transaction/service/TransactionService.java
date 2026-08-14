package com.amar.expense_tracker.transaction.service;

import com.amar.expense_tracker.account.repository.AccountRepository;
import com.amar.expense_tracker.categorization.service.UserCategoryRuleService;
import com.amar.expense_tracker.category.repository.CategoryRepository;
import com.amar.expense_tracker.common.dto.PageResponse;
import com.amar.expense_tracker.common.exception.BadRequestException;
import com.amar.expense_tracker.common.exception.ResourceNotFoundException;
import com.amar.expense_tracker.common.util.JpaDateUtils;
import com.amar.expense_tracker.entity.Accounts;
import com.amar.expense_tracker.entity.Categories;
import com.amar.expense_tracker.entity.Transactions;
import com.amar.expense_tracker.entity.Users;
import com.amar.expense_tracker.transaction.dto.CategoryPatchRequest;
import com.amar.expense_tracker.transaction.dto.ExpenseRequest;
import com.amar.expense_tracker.transaction.dto.IncomeRequest;
import com.amar.expense_tracker.transaction.dto.TransactionResponse;
import com.amar.expense_tracker.transaction.dto.TransactionSource;
import com.amar.expense_tracker.transaction.dto.TransactionType;
import com.amar.expense_tracker.transaction.dto.TransactionUpdateRequest;
import com.amar.expense_tracker.transaction.mapper.TransactionMapper;
import com.amar.expense_tracker.transaction.repository.TransactionRepository;
import com.amar.expense_tracker.transaction.repository.TransactionSpecifications;
import com.amar.expense_tracker.transaction.util.MerchantNormalizer;
import com.amar.expense_tracker.transaction.util.TransactionHasher;
import com.amar.expense_tracker.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final String MANUAL_SOURCE = "MANUAL";

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final UserCategoryRuleService userCategoryRuleService;
    private final MerchantNormalizer merchantNormalizer;
    private final TransactionHasher transactionHasher;
    private final TransactionMapper transactionMapper;

    @Transactional
    public TransactionResponse createExpense(UUID userId, ExpenseRequest request) {
        Accounts account = findOwnedAccount(userId, request.accountId());
        Users user = findUser(userId);
        Categories category = findActiveCategory(request.categoryId(), "EXPENSE");

        Transactions transaction = buildManualTransaction(account, user, category,
                request.date(), request.description(), request.amount(), "DEBIT");
        return transactionMapper.toResponse(transactionRepository.save(transaction));
    }

    @Transactional
    public TransactionResponse createIncome(UUID userId, IncomeRequest request) {
        Accounts account = findOwnedAccount(userId, request.accountId());
        Users user = findUser(userId);
        Categories category = findActiveCategory(request.categoryId(), "INCOME");

        Transactions transaction = buildManualTransaction(account, user, category,
                request.date(), request.description(), request.amount(), "CREDIT");
        return transactionMapper.toResponse(transactionRepository.save(transaction));
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> list(UUID userId, LocalDate fromDate, LocalDate toDate,
                                                    UUID accountId, UUID categoryId,
                                                    TransactionType transactionType, TransactionSource source,
                                                    Pageable pageable) {
        var spec = TransactionSpecifications.forFilters(userId, fromDate, toDate, accountId, categoryId,
                transactionType != null ? transactionType.name() : null,
                source != null ? source.name() : null);
        Page<TransactionResponse> page = transactionRepository.findAll(spec, pageable)
                .map(transactionMapper::toResponse);
        return PageResponse.of(page);
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(UUID userId, UUID transactionId) {
        return transactionMapper.toResponse(findOwned(userId, transactionId));
    }

    @Transactional
    public TransactionResponse update(UUID userId, UUID transactionId, TransactionUpdateRequest request) {
        Transactions transaction = findOwned(userId, transactionId);
        Accounts account = findOwnedAccount(userId, request.accountId());
        Categories category = request.categoryId() != null
                ? findActiveCategory(request.categoryId(), null)
                : null;

        boolean categoryChanged = !sameCategory(transaction.getCategories(), category);

        transaction.setAccounts(account);
        transaction.setCategories(category);
        transaction.setTransactionDate(toDbDate(request.date()));
        transaction.setDescription(request.description());
        transaction.setAmount(request.amount());
        transaction.setTransactionType(request.transactionType().name());
        if (categoryChanged) {
            transaction.setIsCategoryModified(true);
        }
        recomputeFingerprint(transaction);
        transaction.setUpdatedAt(Date.from(Instant.now()));

        return transactionMapper.toResponse(transactionRepository.save(transaction));
    }

    @Transactional
    public void delete(UUID userId, UUID transactionId) {
        Transactions transaction = findOwned(userId, transactionId);
        transactionRepository.delete(transaction);
    }

    @Transactional
    public TransactionResponse patchCategory(UUID userId, UUID transactionId, CategoryPatchRequest request) {
        Transactions transaction = findOwned(userId, transactionId);
        Categories category = findActiveCategory(request.categoryId(), null);

        boolean categoryChanged = !sameCategory(transaction.getCategories(), category);
        transaction.setCategories(category);
        if (categoryChanged) {
            transaction.setIsCategoryModified(true);
        }
        transaction.setUpdatedAt(Date.from(Instant.now()));
        Transactions saved = transactionRepository.save(transaction);

        if (Boolean.TRUE.equals(request.createRule())
                && saved.getNormalizedMerchant() != null
                && !saved.getNormalizedMerchant().isBlank()) {
            userCategoryRuleService.upsert(userId, saved.getNormalizedMerchant(), category);
        }

        return transactionMapper.toResponse(saved);
    }

    private Transactions buildManualTransaction(Accounts account, Users user, Categories category, LocalDate date,
                                                 String description, java.math.BigDecimal amount, String type) {
        String normalizedMerchant = merchantNormalizer.normalize(description);
        String salt = UUID.randomUUID().toString();
        String hash = transactionHasher.hash(account.getId(), date, amount, normalizedMerchant, type, salt);

        Date now = Date.from(Instant.now());
        Transactions transaction = new Transactions();
        transaction.setAccounts(account);
        transaction.setUsers(user);
        transaction.setCategories(category);
        transaction.setTransactionDate(toDbDate(date));
        transaction.setDescription(description);
        transaction.setNormalizedMerchant(normalizedMerchant);
        transaction.setAmount(amount);
        transaction.setTransactionType(type);
        transaction.setSource(MANUAL_SOURCE);
        transaction.setTransactionHash(hash);
        transaction.setIsManuallyAdded(true);
        transaction.setIsCategoryModified(false);
        transaction.setCreatedAt(now);
        transaction.setUpdatedAt(now);
        return transaction;
    }

    private void recomputeFingerprint(Transactions transaction) {
        String normalizedMerchant = merchantNormalizer.normalize(transaction.getDescription());
        LocalDate date = JpaDateUtils.toLocalDate(transaction.getTransactionDate());
        String salt = MANUAL_SOURCE.equals(transaction.getSource()) ? UUID.randomUUID().toString() : null;
        String hash = transactionHasher.hash(transaction.getAccounts().getId(), date, transaction.getAmount(),
                normalizedMerchant, transaction.getTransactionType(), salt);
        transaction.setNormalizedMerchant(normalizedMerchant);
        transaction.setTransactionHash(hash);
    }

    private boolean sameCategory(Categories existing, Categories updated) {
        UUID existingId = existing != null ? existing.getId() : null;
        UUID updatedId = updated != null ? updated.getId() : null;
        return java.util.Objects.equals(existingId, updatedId);
    }

    private Accounts findOwnedAccount(UUID userId, UUID accountId) {
        return accountRepository.findByIdAndUsers_Id(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }

    private Categories findActiveCategory(UUID categoryId, String requiredType) {
        Categories category = categoryRepository.findById(categoryId)
                .filter(Categories::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        if (requiredType != null && !requiredType.equals(category.getCategoryType())) {
            throw new BadRequestException("Category must be of type " + requiredType);
        }
        return category;
    }

    private Users findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Transactions findOwned(UUID userId, UUID transactionId) {
        return transactionRepository.findByIdAndUsers_Id(transactionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
    }

    private Date toDbDate(LocalDate localDate) {
        return Date.from(localDate.atStartOfDay(ZoneOffset.UTC).toInstant());
    }
}
