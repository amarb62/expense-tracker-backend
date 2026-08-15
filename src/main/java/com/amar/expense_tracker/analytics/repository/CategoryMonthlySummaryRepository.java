package com.amar.expense_tracker.analytics.repository;

import com.amar.expense_tracker.entity.CategoryMonthlySummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryMonthlySummaryRepository extends JpaRepository<CategoryMonthlySummary, UUID> {

    Optional<CategoryMonthlySummary> findByUsers_IdAndYearAndMonthAndCategories_Id(UUID userId, int year, int month,
                                                                                    UUID categoryId);

    List<CategoryMonthlySummary> findByUsers_IdAndYearAndMonth(UUID userId, int year, int month);

    List<CategoryMonthlySummary> findByUsers_IdAndYear(UUID userId, int year);
}
