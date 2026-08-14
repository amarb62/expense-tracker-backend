package com.amar.expense_tracker.category.repository;

import com.amar.expense_tracker.entity.Categories;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Categories, UUID> {

    List<Categories> findAllByOrderByNameAsc();

    List<Categories> findByCategoryTypeAndActiveTrue(String categoryType);

    Optional<Categories> findByNameIgnoreCaseAndActiveTrue(String name);
}
