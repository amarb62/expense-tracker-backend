package com.amar.expense_tracker.category.repository;

import com.amar.expense_tracker.entity.UserCategoryRules;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Groundwork repository for Phase 8 (categorization engine). No service/controller
 * yet -- {@code UserCategoryRuleService} and the rule-creation flow from the
 * transaction category PATCH endpoint land there.
 */
public interface UserCategoryRuleRepository extends JpaRepository<UserCategoryRules, UUID> {

    List<UserCategoryRules> findByUsers_IdOrderByPriorityDesc(UUID userId);

    Optional<UserCategoryRules> findByUsers_IdAndMerchantPattern(UUID userId, String merchantPattern);
}
