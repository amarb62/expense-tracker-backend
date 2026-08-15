package com.amar.expense_tracker.analytics.service;

import com.amar.expense_tracker.analytics.dto.CategoryAggregate;
import com.amar.expense_tracker.analytics.repository.CategoryMonthlySummaryRepository;
import com.amar.expense_tracker.analytics.repository.MonthlySummaryRepository;
import com.amar.expense_tracker.analytics.repository.TransactionAggregationRepository;
import com.amar.expense_tracker.category.repository.CategoryRepository;
import com.amar.expense_tracker.common.exception.ResourceNotFoundException;
import com.amar.expense_tracker.entity.CategoryMonthlySummary;
import com.amar.expense_tracker.entity.Categories;
import com.amar.expense_tracker.entity.MonthlySummary;
import com.amar.expense_tracker.entity.Users;
import com.amar.expense_tracker.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Recalculates the persisted, all-accounts monthly read model (design.md
 * section 20: "monthly summaries must be recalculated whenever transactions
 * are added/deleted/updated/recategorized -- do not allow stale analytics").
 * There's no account_id column on monthly_summary/category_monthly_summary,
 * so this is always the whole-user view; an account-filtered dashboard query
 * is computed live instead (see {@code DashboardService}), never persisted
 * here.
 * <p>
 * Financial rule (design.md section 4): total_credited sums only CREDIT
 * transactions, total_expenses sums only DEBIT transactions. TRANSFER/PAYMENT/
 * REFUND/FEE/INTEREST/CASH_WITHDRAWAL are deliberately excluded from both --
 * this is what keeps a credit-card bill PAYMENT from double-counting an
 * expense that was already recorded as a DEBIT purchase.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int PERCENTAGE_SCALE = 4;

    private final TransactionAggregationRepository aggregationRepository;
    private final MonthlySummaryRepository monthlySummaryRepository;
    private final CategoryMonthlySummaryRepository categoryMonthlySummaryRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;

    @Transactional
    public void recalculateMonth(UUID userId, int year, int month) {
        BigDecimal totalCredited = aggregationRepository.sumByTypeForMonth(userId, "CREDIT", year, month, null);
        BigDecimal totalExpenses = aggregationRepository.sumByTypeForMonth(userId, "DEBIT", year, month, null);
        BigDecimal remaining = totalCredited.subtract(totalExpenses);
        BigDecimal expensePercentage = percentageOf(totalExpenses, totalCredited);

        MonthlySummary summary = monthlySummaryRepository.findByUsers_IdAndYearAndMonth(userId, year, month)
                .orElseGet(MonthlySummary::new);
        boolean isNew = summary.getId() == null;
        Date now = Date.from(Instant.now());
        if (isNew) {
            summary.setUsers(findUser(userId));
            summary.setYear(year);
            summary.setMonth(month);
            summary.setCreatedAt(now);
        }
        summary.setTotalCredited(totalCredited);
        summary.setTotalExpenses(totalExpenses);
        summary.setRemainingAmount(remaining);
        summary.setExpensePercentage(expensePercentage);
        summary.setUpdatedAt(now);
        monthlySummaryRepository.save(summary);

        recalculateCategoryBreakdown(userId, year, month, totalCredited, totalExpenses);
    }

    private void recalculateCategoryBreakdown(UUID userId, int year, int month,
                                               BigDecimal totalCredited, BigDecimal totalExpenses) {
        List<CategoryAggregate> aggregates = aggregationRepository.categoryBreakdownForMonth(userId, year, month, null);
        Date now = Date.from(Instant.now());

        for (CategoryAggregate aggregate : aggregates) {
            BigDecimal percentOfExpense = percentageOf(aggregate.totalAmount(), totalExpenses);
            BigDecimal percentOfCredit = percentageOf(aggregate.totalAmount(), totalCredited);

            CategoryMonthlySummary row = categoryMonthlySummaryRepository
                    .findByUsers_IdAndYearAndMonthAndCategories_Id(userId, year, month, aggregate.categoryId())
                    .orElseGet(CategoryMonthlySummary::new);
            boolean isNew = row.getId() == null;
            if (isNew) {
                row.setUsers(findUser(userId));
                row.setCategories(findCategory(aggregate.categoryId()));
                row.setYear(year);
                row.setMonth(month);
                row.setCreatedAt(now);
            }
            row.setTotalAmount(aggregate.totalAmount());
            row.setPercentageOfExpense(percentOfExpense);
            row.setPercentageOfCredit(percentOfCredit);
            row.setUpdatedAt(now);
            categoryMonthlySummaryRepository.save(row);
        }

        // A category that had a summary row before but has zero matching transactions
        // now (deleted/recategorized away) must not linger with a stale non-zero amount.
        Set<UUID> currentCategoryIds = new HashSet<>();
        for (CategoryAggregate aggregate : aggregates) {
            currentCategoryIds.add(aggregate.categoryId());
        }
        List<CategoryMonthlySummary> existingRows = categoryMonthlySummaryRepository
                .findByUsers_IdAndYearAndMonth(userId, year, month);
        for (CategoryMonthlySummary row : existingRows) {
            if (!currentCategoryIds.contains(row.getCategories().getId())) {
                categoryMonthlySummaryRepository.delete(row);
            }
        }
    }

    private BigDecimal percentageOf(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return numerator.multiply(ONE_HUNDRED).divide(denominator, PERCENTAGE_SCALE, RoundingMode.HALF_UP);
    }

    private Users findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Categories findCategory(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }
}
