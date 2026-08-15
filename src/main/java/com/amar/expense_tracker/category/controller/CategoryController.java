package com.amar.expense_tracker.category.controller;

import com.amar.expense_tracker.category.dto.CategoryCreateRequest;
import com.amar.expense_tracker.category.dto.CategoryDeactivateRequest;
import com.amar.expense_tracker.category.dto.CategoryResponse;
import com.amar.expense_tracker.category.dto.CategoryType;
import com.amar.expense_tracker.category.dto.CategoryUpdateRequest;
import com.amar.expense_tracker.category.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Global, hierarchical expense/income/transfer categories")
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a category")
    public CategoryResponse create(@Valid @RequestBody CategoryCreateRequest request) {
        return categoryService.create(request);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a category's name, color, and/or parent")
    public CategoryResponse update(@PathVariable UUID id, @RequestBody CategoryUpdateRequest request) {
        return categoryService.update(id, request);
    }

    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deactivate a category, reassigning its transactions to a replacement category if it has any")
    public void deactivate(@PathVariable UUID id,
                            @RequestBody(required = false) CategoryDeactivateRequest request) {
        categoryService.deactivate(id, request);
    }
}
