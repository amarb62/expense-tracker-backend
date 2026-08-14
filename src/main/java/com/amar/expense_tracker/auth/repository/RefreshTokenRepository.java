package com.amar.expense_tracker.auth.repository;

import com.amar.expense_tracker.entity.RefreshTokens;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokens, UUID> {

    Optional<RefreshTokens> findByTokenHash(String tokenHash);
}
