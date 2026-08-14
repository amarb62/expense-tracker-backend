package com.amar.expense_tracker.category.service;

import com.amar.expense_tracker.category.dto.CategoryResponse;
import com.amar.expense_tracker.category.dto.CategoryType;
import com.amar.expense_tracker.category.mapper.CategoryMapper;
import com.amar.expense_tracker.category.repository.CategoryRepository;
import com.amar.expense_tracker.common.exception.ResourceNotFoundException;
import com.amar.expense_tracker.entity.Categories;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

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
}
