package com.amar.expense_tracker.analytics.repository;

import com.amar.expense_tracker.entity.MonthlySummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MonthlySummaryRepository extends JpaRepository<MonthlySummary, UUID> {

    Optional<MonthlySummary> findByUsers_IdAndYearAndMonth(UUID userId, int year, int month);

    List<MonthlySummary> findByUsers_IdAndYearOrderByMonthAsc(UUID userId, int year);

    List<MonthlySummary> findByUsers_IdOrderByYearAscMonthAsc(UUID userId);
}
