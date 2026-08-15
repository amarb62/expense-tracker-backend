package com.amar.expense_tracker.statement.service;

import com.amar.expense_tracker.account.repository.AccountRepository;
import com.amar.expense_tracker.analytics.service.AnalyticsService;
import com.amar.expense_tracker.common.exception.ResourceNotFoundException;
import com.amar.expense_tracker.common.util.JpaDateUtils;
import com.amar.expense_tracker.entity.Accounts;
import com.amar.expense_tracker.entity.Statements;
import com.amar.expense_tracker.entity.Transactions;
import com.amar.expense_tracker.entity.Users;
import com.amar.expense_tracker.statement.dto.StatementFile;
import com.amar.expense_tracker.statement.dto.StatementResponse;
import com.amar.expense_tracker.statement.event.StatementUploadedEvent;
import com.amar.expense_tracker.statement.mapper.StatementMapper;
import com.amar.expense_tracker.statement.repository.StatementRepository;
import com.amar.expense_tracker.statement.storage.FileStorageService;
import com.amar.expense_tracker.transaction.repository.TransactionRepository;
import com.amar.expense_tracker.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StatementService {

    private final StatementRepository statementRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final StatementFileValidator statementFileValidator;
    private final FileStorageService fileStorageService;
    private final StatementEventPublisher statementEventPublisher;
    private final StatementMapper statementMapper;
    private final TransactionRepository transactionRepository;
    private final AnalyticsService analyticsService;

    public StatementResponse upload(UUID userId, UUID accountId, MultipartFile file, String password) {
        byte[] decryptedBytes = statementFileValidator.validateAndDecrypt(file, password);

        Accounts account = accountRepository.findByIdAndUsers_Id(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String storageKey;
        try {
            storageKey = fileStorageService.store(new ByteArrayInputStream(decryptedBytes), userId);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store uploaded statement", e);
        }

        Statements saved;
        try {
            saved = persistStatement(account, user, file.getOriginalFilename(), storageKey);
        } catch (RuntimeException e) {
            deleteQuietly(storageKey);
            throw e;
        }

        // Published only after the row is committed (persistStatement runs in its own
        // repository-managed transaction), so a consumer never sees an event for a
        // statement that doesn't exist yet.
        statementEventPublisher.publishUploaded(
                new StatementUploadedEvent(saved.getId(), account.getId(), userId, storageKey));

        return statementMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<StatementResponse> list(UUID userId) {
        return statementRepository.findByUsers_IdOrderByUploadedAtDesc(userId).stream()
                .map(statementMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public StatementResponse get(UUID userId, UUID statementId) {
        Statements statement = statementRepository.findByIdAndUsers_Id(statementId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Statement not found"));
        return statementMapper.toResponse(statement);
    }

    @Transactional(readOnly = true)
    public StatementFile downloadFile(UUID userId, UUID statementId) {
        Statements statement = statementRepository.findByIdAndUsers_Id(statementId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Statement not found"));
        byte[] content;
        try (InputStream in = fileStorageService.retrieve(statement.getStorageKey())) {
            content = in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read stored statement file", e);
        }
        return new StatementFile(statement.getFileName(), content);
    }

    @Transactional
    public void delete(UUID userId, UUID statementId) {
        Statements statement = statementRepository.findByIdAndUsers_Id(statementId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Statement not found"));

        List<Transactions> transactions = transactionRepository.findByStatements_Id(statementId);
        Set<YearMonth> affectedMonths = new HashSet<>();
        for (Transactions transaction : transactions) {
            LocalDate date = JpaDateUtils.toLocalDate(transaction.getTransactionDate());
            affectedMonths.add(YearMonth.of(date.getYear(), date.getMonthValue()));
        }

        // ai_categorization.transaction_id is ON DELETE CASCADE at the DB level
        // (V1__init_schema.sql), so deleting the transactions is enough -- no explicit
        // ai_categorization cleanup needed here.
        transactionRepository.deleteAll(transactions);

        for (YearMonth yearMonth : affectedMonths) {
            analyticsService.recalculateMonth(userId, yearMonth.getYear(), yearMonth.getMonthValue());
        }

        try {
            fileStorageService.delete(statement.getStorageKey());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete stored statement file", e);
        }

        statementRepository.delete(statement);
    }

    private Statements persistStatement(Accounts account, Users user, String fileName, String storageKey) {
        Statements statement = new Statements();
        statement.setAccounts(account);
        statement.setUsers(user);
        statement.setFileName(fileName);
        statement.setStorageKey(storageKey);
        statement.setStatementType(account.getAccountType());
        statement.setStatus("PROCESSING");
        statement.setUploadedAt(Date.from(Instant.now()));
        return statementRepository.save(statement);
    }

    private void deleteQuietly(String storageKey) {
        try {
            fileStorageService.delete(storageKey);
        } catch (IOException ignored) {
            // best-effort cleanup; the DB save failure is the error being propagated
        }
    }
}
