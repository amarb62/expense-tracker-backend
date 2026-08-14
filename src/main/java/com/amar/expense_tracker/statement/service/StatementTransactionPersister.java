package com.amar.expense_tracker.statement.service;

import com.amar.expense_tracker.categorization.service.ExpenseCategorizationService;
import com.amar.expense_tracker.entity.Accounts;
import com.amar.expense_tracker.entity.Statements;
import com.amar.expense_tracker.entity.Transactions;
import com.amar.expense_tracker.entity.Users;
import com.amar.expense_tracker.statement.parser.RawTransaction;
import com.amar.expense_tracker.transaction.repository.TransactionRepository;
import com.amar.expense_tracker.transaction.util.MerchantNormalizer;
import com.amar.expense_tracker.transaction.util.TransactionHasher;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

/**
 * A separate bean (not a method on {@link StatementProcessingService}) so that
 * {@code @Transactional} is actually honored -- an internal call from within the
 * same class bypasses Spring's proxy-based AOP entirely. Persisting all raw
 * transactions for a statement atomically also satisfies design.md's "do not
 * create partial transactions" requirement: either they all land, or none do.
 */
@Component
@RequiredArgsConstructor
public class StatementTransactionPersister {

    private static final Logger log = LoggerFactory.getLogger(StatementTransactionPersister.class);

    private final TransactionRepository transactionRepository;
    private final MerchantNormalizer merchantNormalizer;
    private final TransactionHasher transactionHasher;
    private final ExpenseCategorizationService expenseCategorizationService;

    @Transactional
    public PersistResult persistAll(Statements statement, List<RawTransaction> rawTransactions) {
        Accounts account = statement.getAccounts();
        Users user = statement.getUsers();
        int savedCount = 0;
        int duplicateCount = 0;

        for (RawTransaction raw : rawTransactions) {
            String normalizedMerchant = merchantNormalizer.normalize(raw.description());
            String hash = transactionHasher.hash(account.getId(), raw.transactionDate(), raw.amount(),
                    normalizedMerchant, raw.transactionType());

            if (transactionRepository.existsByTransactionHash(hash)) {
                duplicateCount++;
                continue;
            }

            Date now = Date.from(Instant.now());
            Transactions transaction = new Transactions();
            transaction.setAccounts(account);
            transaction.setStatements(statement);
            transaction.setUsers(user);
            transaction.setTransactionDate(toDate(raw.transactionDate()));
            transaction.setDescription(raw.description());
            transaction.setNormalizedMerchant(normalizedMerchant);
            transaction.setAmount(raw.amount());
            transaction.setTransactionType(raw.transactionType());
            transaction.setSource("PDF");
            transaction.setTransactionHash(hash);
            transaction.setIsManuallyAdded(false);
            transaction.setIsCategoryModified(false);
            transaction.setCreatedAt(now);
            transaction.setUpdatedAt(now);
            transactionRepository.save(transaction);
            savedCount++;

            try {
                expenseCategorizationService.categorize(transaction);
            } catch (Exception e) {
                // Categorization is best-effort: the transaction itself already saved
                // successfully, and a categorization bug must not roll back the batch.
                log.warn("Categorization failed for transaction [{}]: {}", transaction.getId(), e.getMessage());
            }
        }

        return new PersistResult(savedCount, duplicateCount);
    }

    private Date toDate(LocalDate localDate) {
        return Date.from(localDate.atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    public record PersistResult(int savedCount, int duplicateCount) {
    }
}
