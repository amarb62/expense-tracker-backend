package com.amar.expense_tracker.statement.repository;

import com.amar.expense_tracker.entity.Statements;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StatementRepository extends JpaRepository<Statements, UUID> {

    List<Statements> findByUsers_IdOrderByUploadedAtDesc(UUID userId);

    Optional<Statements> findByIdAndUsers_Id(UUID id, UUID userId);

    // Join-fetches accounts/users so StatementProcessingService can read them after
    // this call returns without a LazyInitializationException -- the async listener
    // has no surrounding transaction/session by the time it does that slow work.
    @Query("select s from Statements s join fetch s.accounts join fetch s.users where s.id = :id")
    Optional<Statements> findByIdWithAccountAndUser(@Param("id") UUID id);
}
