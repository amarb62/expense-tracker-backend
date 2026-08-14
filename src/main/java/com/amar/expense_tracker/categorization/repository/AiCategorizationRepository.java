package com.amar.expense_tracker.categorization.repository;

import com.amar.expense_tracker.entity.AiCategorization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiCategorizationRepository extends JpaRepository<AiCategorization, UUID> {

    Optional<AiCategorization> findTopByTransactions_IdOrderByCreatedAtDesc(UUID transactionId);

    List<AiCategorization> findByTransactions_Users_IdAndStatusOrderByCreatedAtDesc(UUID userId, String status);
}
