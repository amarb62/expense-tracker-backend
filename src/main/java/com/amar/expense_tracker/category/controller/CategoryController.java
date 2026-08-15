package com.amar.expense_tracker.category.controller;

import com.amar.expense_tracker.category.dto.CategoryResponse;
import com.amar.expense_tracker.category.dto.CategoryType;
import com.amar.expense_tracker.category.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Global, hierarchical expense/income/transfer categories (read-only)")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "List categories as a parent/child tree, optionally filtered by type")
    public List<CategoryResponse> list(@RequestParam(required = false) CategoryType type,
                                        @RequestParam(defaultValue = "false") boolean includeInactive) {
        return categoryService.listTree(type, includeInactive);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a category by ID")
    public CategoryResponse get(@PathVariable UUID id) {
        return categoryService.get(id);
    }
}
