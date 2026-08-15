package com.amar.expense_tracker.dashboard.service;

import com.amar.expense_tracker.account.repository.AccountRepository;
import com.amar.expense_tracker.analytics.dto.CategoryAggregate;
import com.amar.expense_tracker.analytics.repository.CategoryMonthlySummaryRepository;
import com.amar.expense_tracker.analytics.repository.MonthlySummaryRepository;
import com.amar.expense_tracker.analytics.repository.TransactionAggregationRepository;
import com.amar.expense_tracker.common.exception.ResourceNotFoundException;
import com.amar.expense_tracker.dashboard.dto.CategoryBreakdownResponse;
import com.amar.expense_tracker.dashboard.dto.MonthlyBreakdownItem;
import com.amar.expense_tracker.dashboard.dto.MonthlyDashboardResponse;
import com.amar.expense_tracker.dashboard.dto.TrendPoint;
import com.amar.expense_tracker.dashboard.dto.TrendsResponse;
import com.amar.expense_tracker.dashboard.dto.YearlyDashboardResponse;
import com.amar.expense_tracker.entity.CategoryMonthlySummary;
import com.amar.expense_tracker.entity.MonthlySummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reads, never writes, the dashboard view. When no accountId filter is given,
 * serves straight from the maintained monthly_summary/category_monthly_summary
 * tables (fast; kept fresh by {@code AnalyticsService}). An accountId filter
 * can't be served from those tables at all -- there's no account_id column on
 * them -- so that path computes live from raw transactions instead.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int PERCENTAGE_SCALE = 4;

    private final TransactionAggregationRepository aggregationRepository;
    private final MonthlySummaryRepository monthlySummaryRepository;
    private final CategoryMonthlySummaryRepository categoryMonthlySummaryRepository;
    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public MonthlyDashboardResponse monthly(UUID userId, int year, int month, UUID accountId) {
        if (accountId != null) {
            validateOwnedAccount(userId, accountId);
            return computeLiveMonthly(userId, year, month, accountId);
        }

        return monthlySummaryRepository.findByUsers_IdAndYearAndMonth(userId, year, month)
                .map(summary -> {
                    List<CategoryBreakdownResponse> categories = categoryMonthlySummaryRepository
                            .findByUsers_IdAndYearAndMonth(userId, year, month).stream()
                            .map(this::toCategoryResponse)
                            .toList();
                    return new MonthlyDashboardResponse(period(year, month), summary.getTotalCredited(),
                            summary.getTotalExpenses(), summary.getRemainingAmount(), summary.getExpensePercentage(),
                            categories);
                })
                .orElseGet(() -> emptyMonthly(year, month));
    }

    @Transactional(readOnly = true)
    public YearlyDashboardResponse yearly(UUID userId, int year, UUID accountId) {
        if (accountId != null) {
            validateOwnedAccount(userId, accountId);
            return computeLiveYearly(userId, year, accountId);
        }

        List<MonthlySummary> monthlySummaries = monthlySummaryRepository.findByUsers_IdAndYearOrderByMonthAsc(userId, year);
        BigDecimal totalCredited = sumSummaries(monthlySummaries, MonthlySummary::getTotalCredited);
        BigDecimal totalExpenses = sumSummaries(monthlySummaries, MonthlySummary::getTotalExpenses);
        BigDecimal remaining = totalCredited.subtract(totalExpenses);
        BigDecimal expensePercentage = percentageOf(totalExpenses, totalCredited);

        Map<UUID, CategoryYearAccumulator> byCategory = new LinkedHashMap<>();
        for (CategoryMonthlySummary row : categoryMonthlySummaryRepository.findByUsers_IdAndYear(userId, year)) {
            byCategory.computeIfAbsent(row.getCategories().getId(),
                            key -> new CategoryYearAccumulator(row.getCategories().getName()))
                    .add(row.getTotalAmount());
        }
        List<CategoryBreakdownResponse> categories = byCategory.values().stream()
                .map(acc -> new CategoryBreakdownResponse(acc.name, acc.total,
                        percentageOf(acc.total, totalExpenses), percentageOf(acc.total, totalCredited)))
                .toList();

        List<MonthlyBreakdownItem> months = monthlySummaries.stream()
                .map(m -> new MonthlyBreakdownItem(m.getMonth(), m.getTotalCredited(), m.getTotalExpenses(),
                        m.getRemainingAmount(), m.getExpensePercentage()))
                .toList();

        return new YearlyDashboardResponse(year, totalCredited, totalExpenses, remaining, expensePercentage,
                categories, months);
    }

    @Transactional(readOnly = true)
    public TrendsResponse trends(UUID userId) {
        List<TrendPoint> points = monthlySummaryRepository.findByUsers_IdOrderByYearAscMonthAsc(userId).stream()
                .map(m -> new TrendPoint(period(m.getYear(), m.getMonth()), m.getTotalCredited(), m.getTotalExpenses()))
                .toList();
        return new TrendsResponse(points);
    }

    private MonthlyDashboardResponse computeLiveMonthly(UUID userId, int year, int month, UUID accountId) {
        BigDecimal totalCredited = aggregationRepository.sumByTypeForMonth(userId, "CREDIT", year, month, accountId);
        BigDecimal totalExpenses = aggregationRepository.sumByTypeForMonth(userId, "DEBIT", year, month, accountId);
        BigDecimal remaining = totalCredited.subtract(totalExpenses);
        BigDecimal expensePercentage = percentageOf(totalExpenses, totalCredited);

        List<CategoryBreakdownResponse> categories = aggregationRepository
                .categoryBreakdownForMonth(userId, year, month, accountId).stream()
                .map(agg -> toCategoryResponse(agg, totalExpenses, totalCredited))
                .toList();

        return new MonthlyDashboardResponse(period(year, month), totalCredited, totalExpenses, remaining,
                expensePercentage, categories);
    }

    private YearlyDashboardResponse computeLiveYearly(UUID userId, int year, UUID accountId) {
        List<MonthlyBreakdownItem> months = new ArrayList<>();
        BigDecimal totalCredited = BigDecimal.ZERO;
        BigDecimal totalExpenses = BigDecimal.ZERO;

        for (int month = 1; month <= 12; month++) {
            BigDecimal credited = aggregationRepository.sumByTypeForMonth(userId, "CREDIT", year, month, accountId);
            BigDecimal expenses = aggregationRepository.sumByTypeForMonth(userId, "DEBIT", year, month, accountId);
            totalCredited = totalCredited.add(credited);
            totalExpenses = totalExpenses.add(expenses);
            months.add(new MonthlyBreakdownItem(month, credited, expenses, credited.subtract(expenses),
                    percentageOf(expenses, credited)));
        }

        BigDecimal remaining = totalCredited.subtract(totalExpenses);
        BigDecimal expensePercentage = percentageOf(totalExpenses, totalCredited);

        BigDecimal finalTotalExpenses = totalExpenses;
        BigDecimal finalTotalCredited = totalCredited;
        List<CategoryBreakdownResponse> categories = aggregationRepository
                .categoryBreakdownForYear(userId, year, accountId).stream()
                .map(agg -> toCategoryResponse(agg, finalTotalExpenses, finalTotalCredited))
                .toList();

        return new YearlyDashboardResponse(year, totalCredited, totalExpenses, remaining, expensePercentage,
                categories, months);
    }

    private MonthlyDashboardResponse emptyMonthly(int year, int month) {
        return new MonthlyDashboardResponse(period(year, month), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, List.of());
    }

    private CategoryBreakdownResponse toCategoryResponse(CategoryMonthlySummary row) {
        return new CategoryBreakdownResponse(row.getCategories().getName(), row.getTotalAmount(),
                row.getPercentageOfExpense(), row.getPercentageOfCredit());
    }

    private CategoryBreakdownResponse toCategoryResponse(CategoryAggregate aggregate, BigDecimal totalExpenses,
                                                          BigDecimal totalCredited) {
        return new CategoryBreakdownResponse(aggregate.categoryName(), aggregate.totalAmount(),
                percentageOf(aggregate.totalAmount(), totalExpenses), percentageOf(aggregate.totalAmount(), totalCredited));
    }

    private BigDecimal sumSummaries(List<MonthlySummary> summaries, java.util.function.Function<MonthlySummary, BigDecimal> extractor) {
        BigDecimal total = BigDecimal.ZERO;
        for (MonthlySummary summary : summaries) {
            total = total.add(extractor.apply(summary));
        }
        return total;
    }

    private BigDecimal percentageOf(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return numerator.multiply(ONE_HUNDRED).divide(denominator, PERCENTAGE_SCALE, RoundingMode.HALF_UP);
    }

    private String period(int year, int month) {
        return "%04d-%02d".formatted(year, month);
    }

    private void validateOwnedAccount(UUID userId, UUID accountId) {
        accountRepository.findByIdAndUsers_Id(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }

    private static final class CategoryYearAccumulator {
        private final String name;
        private BigDecimal total = BigDecimal.ZERO;

        private CategoryYearAccumulator(String name) {
            this.name = name;
        }

        private void add(BigDecimal amount) {
            total = total.add(amount);
        }
    }
}
