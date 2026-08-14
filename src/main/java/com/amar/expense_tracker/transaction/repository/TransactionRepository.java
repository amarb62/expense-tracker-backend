package com.amar.expense_tracker.transaction.repository;

import com.amar.expense_tracker.entity.Transactions;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transactions, UUID>,
        JpaSpecificationExecutor<Transactions> {

    boolean existsByTransactionHash(String transactionHash);

    Optional<Transactions> findByIdAndUsers_Id(UUID id, UUID userId);
}
