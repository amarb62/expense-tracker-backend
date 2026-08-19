package com.amar.expense_tracker.statement.service;

import com.amar.expense_tracker.entity.Statements;
import com.amar.expense_tracker.statement.event.StatementUploadedEvent;
import com.amar.expense_tracker.statement.parser.RawTransaction;
import com.amar.expense_tracker.statement.parser.StatementMetadata;
import com.amar.expense_tracker.statement.parser.StatementParser;
import com.amar.expense_tracker.statement.repository.StatementRepository;
import com.amar.expense_tracker.statement.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StatementProcessingService {

    private static final Logger log = LoggerFactory.getLogger(StatementProcessingService.class);

    private final StatementRepository statementRepository;
    private final FileStorageService fileStorageService;
    private final List<StatementParser> statementParsers;
    private final StatementTransactionPersister statementTransactionPersister;

    public void process(StatementUploadedEvent event) {
        Optional<Statements> maybeStatement = statementRepository.findByIdWithAccountAndUser(event.statementId());
        if (maybeStatement.isEmpty()) {
            log.warn("Received processing event for unknown statement [{}]", event.statementId());
            return;
        }
        Statements statement = maybeStatement.get();

        try {
            StatementMetadata metadata = new StatementMetadata(
                    statement.getAccounts().getInstitution(),
                    statement.getAccounts().getAccountType(),
                    statement.getFileName());

            StatementParser parser = statementParsers.stream()
                    .filter(candidate -> candidate.supports(metadata))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No statement parser available"));

            List<RawTransaction> rawTransactions;
            try (InputStream pdf = fileStorageService.retrieve(event.storageKey())) {
                rawTransactions = parser.parse(pdf);
            }

            StatementTransactionPersister.PersistResult result =
                    statementTransactionPersister.persistAll(statement, rawTransactions);

            log.info("Statement [{}] processed: {} saved, {} duplicates skipped",
                    statement.getId(), result.savedCount(), result.duplicateCount());

            applyStatementPeriod(statement, rawTransactions);
            markProcessed(statement);
        } catch (Exception e) {
            log.error("Failed to process statement [{}]", statement.getId(), e);
            markFailed(statement, e.getMessage());
        }
    }

    private void applyStatementPeriod(Statements statement, List<RawTransaction> rawTransactions) {
        Optional<LocalDate> minDate = rawTransactions.stream()
                .map(RawTransaction::transactionDate)
                .filter(java.util.Objects::nonNull)
                .min(Comparator.naturalOrder());
        Optional<LocalDate> maxDate = rawTransactions.stream()
                .map(RawTransaction::transactionDate)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder());

        minDate.ifPresent(date -> statement.setStatementStartDate(java.sql.Date.valueOf(date)));
        maxDate.ifPresent(date -> statement.setStatementEndDate(java.sql.Date.valueOf(date)));
    }

    private void markProcessed(Statements statement) {
        statement.setStatus("PROCESSED");
        statement.setProcessedAt(Date.from(Instant.now()));
        statement.setErrorMessage(null);
        statementRepository.save(statement);
    }

    private void markFailed(Statements statement, String message) {
        statement.setStatus("FAILED");
        statement.setProcessedAt(Date.from(Instant.now()));
        statement.setErrorMessage(message != null ? message : "Statement processing failed");
        statementRepository.save(statement);
    }
}
