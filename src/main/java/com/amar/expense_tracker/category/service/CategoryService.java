package com.amar.expense_tracker.category.service;

import com.amar.expense_tracker.analytics.service.AnalyticsService;
import com.amar.expense_tracker.category.dto.CategoryCreateRequest;
import com.amar.expense_tracker.category.dto.CategoryDeactivateRequest;
import com.amar.expense_tracker.category.dto.CategoryResponse;
import com.amar.expense_tracker.category.dto.CategoryType;
import com.amar.expense_tracker.category.dto.CategoryUpdateRequest;
import com.amar.expense_tracker.category.mapper.CategoryMapper;
import com.amar.expense_tracker.category.repository.CategoryRepository;
import com.amar.expense_tracker.common.exception.BadRequestException;
import com.amar.expense_tracker.common.exception.ConflictException;
import com.amar.expense_tracker.common.exception.ResourceNotFoundException;
import com.amar.expense_tracker.common.util.JpaDateUtils;
import com.amar.expense_tracker.entity.Categories;
import com.amar.expense_tracker.entity.Transactions;
import com.amar.expense_tracker.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryMapper categoryMapper;
    private final AnalyticsService analyticsService;

    @Transactional(readOnly = true)
    public List<CategoryResponse> listTree(CategoryType type, boolean includeInactive) {
        List<Categories> filtered = categoryRepository.findAllByOrderByNameAsc().stream()
                .filter(category -> includeInactive || category.isActive())
                .filter(category -> type == null || category.getCategoryType().equals(type.name()))
                .toList();
        return categoryMapper.toTree(filtered);
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(UUID id) {
        Categories category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        return categoryMapper.toResponse(category, List.of());
    }

    @Transactional
    public CategoryResponse create(CategoryCreateRequest request) {
        Categories parent = findParentIfPresent(request.parentId());

        Date now = Date.from(Instant.now());
        Categories category = new Categories();
        category.setCategories(parent);
        category.setName(request.name());
        category.setCategoryType(request.categoryType().name());
        category.setColor(request.color());
        category.setActive(true);
        category.setCreatedAt(now);
        category.setUpdatedAt(now);

        Categories saved = categoryRepository.save(category);
        return categoryMapper.toResponse(saved, List.of());
    }

    @Transactional
    public CategoryResponse update(UUID id, CategoryUpdateRequest request) {
        Categories category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        if (request.name() != null) {
            category.setName(request.name());
        }
        if (request.parentId() != null) {
            category.setCategories(findParentIfPresent(request.parentId()));
        }
        if (request.color() != null) {
            category.setColor(request.color());
        }
        category.setUpdatedAt(Date.from(Instant.now()));

        Categories saved = categoryRepository.save(category);
        return categoryMapper.toResponse(saved, List.of());
    }

    @Transactional
    public void deactivate(UUID id, CategoryDeactivateRequest request) {
        Categories category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        long transactionCount = transactionRepository.countByCategoryId(id);
        if (transactionCount > 0) {
            reassignAndRecalculate(category, request);
        }

        category.setActive(false);
        category.setUpdatedAt(Date.from(Instant.now()));
        categoryRepository.save(category);
    }

    private void reassignAndRecalculate(Categories category, CategoryDeactivateRequest request) {
        UUID replacementId = request != null ? request.replacementCategoryId() : null;
        if (replacementId == null) {
            throw new ConflictException(
                    "Category has existing transactions; a replacementCategoryId is required to deactivate it");
        }
        if (replacementId.equals(category.getId())) {
            throw new BadRequestException(
                    "replacementCategoryId must be different from the category being deactivated");
        }
        categoryRepository.findById(replacementId)
                .filter(Categories::isActive)
                .orElseThrow(() -> new BadRequestException("Replacement category not found or inactive"));

        List<Transactions> affected = transactionRepository.findByCategories_Id(category.getId());
        Set<UserMonth> affectedMonths = new HashSet<>();
        for (Transactions transaction : affected) {
            LocalDate date = JpaDateUtils.toLocalDate(transaction.getTransactionDate());
            affectedMonths.add(new UserMonth(transaction.getUsers().getId(), date.getYear(), date.getMonthValue()));
        }

        transactionRepository.reassignCategory(category.getId(), replacementId);

        for (UserMonth userMonth : affectedMonths) {
            analyticsService.recalculateMonth(userMonth.userId(), userMonth.year(), userMonth.month());
        }
    }

    private Categories findParentIfPresent(UUID parentId) {
        if (parentId == null) {
            return null;
        }
        return categoryRepository.findById(parentId)
                .orElseThrow(() -> new ResourceNotFoundException("Parent category not found"));
    }

    // Categories are global/shared across users, so reassigning a deactivated category's
    // transactions can touch transactions belonging to several different users -- unlike
    // TransactionService's single-user recalculation, each distinct (user, year, month)
    // combination found among the reassigned transactions needs its own recalculation call.
    private record UserMonth(UUID userId, int year, int month) {
    }
}
