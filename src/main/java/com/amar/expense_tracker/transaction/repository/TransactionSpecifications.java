package com.amar.expense_tracker.transaction.repository;

import com.amar.expense_tracker.entity.Transactions;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    public static Specification<Transactions> forFilters(UUID userId, LocalDate fromDate, LocalDate toDate,
                                                           UUID accountId, UUID categoryId,
                                                           String transactionType, String source) {
        List<Specification<Transactions>> specs = new ArrayList<>();
        specs.add((root, query, cb) -> cb.equal(root.get("users").get("id"), userId));
        if (fromDate != null) {
            specs.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("transactionDate"), toDbDate(fromDate)));
        }
        if (toDate != null) {
            specs.add((root, query, cb) -> cb.lessThanOrEqualTo(root.get("transactionDate"), toDbDate(toDate)));
        }
        if (accountId != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("accounts").get("id"), accountId));
        }
        if (categoryId != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("categories").get("id"), categoryId));
        }
        if (transactionType != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("transactionType"), transactionType));
        }
        if (source != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("source"), source));
        }
        return specs.stream().reduce(Specification::and).orElseThrow();
    }

    private static Date toDbDate(LocalDate localDate) {
        return Date.from(localDate.atStartOfDay(ZoneOffset.UTC).toInstant());
    }
}
