package com.amar.expense_tracker.account.repository;

import com.amar.expense_tracker.entity.Accounts;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Accounts, UUID> {

    List<Accounts> findByUsers_IdOrderByCreatedAtDesc(UUID userId);

    Optional<Accounts> findByIdAndUsers_Id(UUID id, UUID userId);

    @Query("select count(t) > 0 from Transactions t where t.accounts.id = :accountId")
    boolean hasTransactions(@Param("accountId") UUID accountId);

    @Query("select count(s) > 0 from Statements s where s.accounts.id = :accountId")
    boolean hasStatements(@Param("accountId") UUID accountId);
}
