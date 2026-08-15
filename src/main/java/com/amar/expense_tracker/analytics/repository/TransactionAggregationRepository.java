package com.amar.expense_tracker.analytics.repository;

import com.amar.expense_tracker.analytics.dto.CategoryAggregate;
import com.amar.expense_tracker.entity.Transactions;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Read-only aggregate queries over raw transactions, backing both
 * {@code AnalyticsService} (recalculates the persisted monthly_summary /
 * category_monthly_summary tables, all-accounts only -- there's no account_id
 * column on those tables) and the dashboard's account-filtered live queries
 * (computed on demand, never persisted, since the summary tables can't
 * represent a single-account view).
 */
public interface TransactionAggregationRepository extends Repository<Transactions, UUID> {

    @Query("select coalesce(sum(t.amount), 0) from Transactions t where t.users.id = :userId "
            + "and t.transactionType = :type "
            + "and extract(year from t.transactionDate) = :year and extract(month from t.transactionDate) = :month "
            + "and (:accountId is null or t.accounts.id = :accountId)")
    BigDecimal sumByTypeForMonth(@Param("userId") UUID userId, @Param("type") String type,
                                 @Param("year") int year, @Param("month") int month,
                                 @Param("accountId") UUID accountId);

    // Restricted to EXPENSE-type categories to match design.md section 19's example
    // exactly (its "categories" breakdown is spending-by-category; an income
    // category mixed into the same list would show a nonsensical >100%
    // percentageOfExpenses). percentageOfCredit for an expense category is still
    // meaningful ("what fraction of income did this category consume").
    @Query("select new com.amar.expense_tracker.analytics.dto.CategoryAggregate(t.categories.id, t.categories.name, sum(t.amount)) "
            + "from Transactions t where t.users.id = :userId and t.categories is not null "
            + "and t.categories.categoryType = 'EXPENSE' "
            + "and extract(year from t.transactionDate) = :year and extract(month from t.transactionDate) = :month "
            + "and (:accountId is null or t.accounts.id = :accountId) "
            + "group by t.categories.id, t.categories.name")
    List<CategoryAggregate> categoryBreakdownForMonth(@Param("userId") UUID userId, @Param("year") int year,
                                                       @Param("month") int month, @Param("accountId") UUID accountId);

    @Query("select new com.amar.expense_tracker.analytics.dto.CategoryAggregate(t.categories.id, t.categories.name, sum(t.amount)) "
            + "from Transactions t where t.users.id = :userId and t.categories is not null "
            + "and t.categories.categoryType = 'EXPENSE' "
            + "and extract(year from t.transactionDate) = :year "
            + "and (:accountId is null or t.accounts.id = :accountId) "
            + "group by t.categories.id, t.categories.name")
    List<CategoryAggregate> categoryBreakdownForYear(@Param("userId") UUID userId, @Param("year") int year,
                                                      @Param("accountId") UUID accountId);
}
