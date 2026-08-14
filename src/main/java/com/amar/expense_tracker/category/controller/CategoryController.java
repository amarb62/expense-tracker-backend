package com.amar.expense_tracker.category.controller;

import com.amar.expense_tracker.category.dto.CategoryResponse;
import com.amar.expense_tracker.category.dto.CategoryType;
import com.amar.expense_tracker.category.service.CategoryService;
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
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public List<CategoryResponse> list(@RequestParam(required = false) CategoryType type,
                                        @RequestParam(defaultValue = "false") boolean includeInactive) {
        return categoryService.listTree(type, includeInactive);
    }

    @GetMapping("/{id}")
    public CategoryResponse get(@PathVariable UUID id) {
        return categoryService.get(id);
    }
}
