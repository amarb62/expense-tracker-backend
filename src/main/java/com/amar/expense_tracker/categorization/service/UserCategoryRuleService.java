package com.amar.expense_tracker.categorization.service;

import com.amar.expense_tracker.category.repository.UserCategoryRuleRepository;
import com.amar.expense_tracker.common.exception.ResourceNotFoundException;
import com.amar.expense_tracker.entity.Categories;
import com.amar.expense_tracker.entity.UserCategoryRules;
import com.amar.expense_tracker.entity.Users;
import com.amar.expense_tracker.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * User-specific merchant/category preferences (design.md sections 11 and 14):
 * highest priority in the categorization order, and the target of "when the
 * user changes a category, create/update a rule so future transactions from
 * the same merchant skip the AI."
 */
@Service
@RequiredArgsConstructor
public class UserCategoryRuleService {

    private final UserCategoryRuleRepository userCategoryRuleRepository;
    private final UserRepository userRepository;

    public Optional<Categories> match(UUID userId, String normalizedMerchant) {
        if (normalizedMerchant == null || normalizedMerchant.isBlank()) {
            return Optional.empty();
        }
        return userCategoryRuleRepository.findByUsers_IdAndMerchantPattern(userId, normalizedMerchant)
                .map(UserCategoryRules::getCategories);
    }

    @Transactional
    public void upsert(UUID userId, String merchantPattern, Categories category) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        UserCategoryRules rule = userCategoryRuleRepository.findByUsers_IdAndMerchantPattern(userId, merchantPattern)
                .orElseGet(UserCategoryRules::new);
        boolean isNew = rule.getId() == null;
        Date now = Date.from(Instant.now());

        rule.setUsers(user);
        rule.setCategories(category);
        rule.setMerchantPattern(merchantPattern);
        if (isNew) {
            rule.setPriority(0);
            rule.setCreatedAt(now);
        }
        rule.setUpdatedAt(now);
        userCategoryRuleRepository.save(rule);
    }
}
