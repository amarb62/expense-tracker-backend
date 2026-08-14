package com.amar.expense_tracker.category.mapper;

import com.amar.expense_tracker.category.dto.CategoryResponse;
import com.amar.expense_tracker.category.dto.CategoryType;
import com.amar.expense_tracker.entity.Categories;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class CategoryMapper {

    public List<CategoryResponse> toTree(List<Categories> categories) {
        Set<UUID> presentIds = categories.stream().map(Categories::getId).collect(Collectors.toSet());
        Map<UUID, List<Categories>> childrenOf = new HashMap<>();
        List<Categories> roots = new ArrayList<>();

        for (Categories category : categories) {
            Categories parent = category.getCategories();
            if (parent != null && presentIds.contains(parent.getId())) {
                childrenOf.computeIfAbsent(parent.getId(), key -> new ArrayList<>()).add(category);
            } else {
                roots.add(category);
            }
        }

        return roots.stream().map(root -> buildNode(root, childrenOf)).toList();
    }

    public CategoryResponse toResponse(Categories category, List<CategoryResponse> children) {
        Categories parent = category.getCategories();
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                CategoryType.valueOf(category.getCategoryType()),
                parent != null ? parent.getId() : null,
                category.isActive(),
                children
        );
    }

    private CategoryResponse buildNode(Categories category, Map<UUID, List<Categories>> childrenOf) {
        List<CategoryResponse> children = childrenOf.getOrDefault(category.getId(), List.of()).stream()
                .map(child -> buildNode(child, childrenOf))
                .toList();
        return toResponse(category, children);
    }
}
