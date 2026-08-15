package com.amar.expense_tracker.transaction.repository;

import com.amar.expense_tracker.entity.Transactions;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transactions, UUID>,
        JpaSpecificationExecutor<Transactions> {

    boolean existsByTransactionHash(String transactionHash);

    Optional<Transactions> findByIdAndUsers_Id(UUID id, UUID userId);

    List<Transactions> findByStatements_Id(UUID statementId);

    List<Transactions> findByCategories_Id(UUID categoryId);

    @Query("select count(t) from Transactions t where t.categories.id = :categoryId")
    long countByCategoryId(@Param("categoryId") UUID categoryId);

    @Modifying
    @Query(value = "UPDATE transactions SET category_id = :newCategoryId WHERE category_id = :oldCategoryId",
            nativeQuery = true)
    void reassignCategory(@Param("oldCategoryId") UUID oldCategoryId, @Param("newCategoryId") UUID newCategoryId);
}
