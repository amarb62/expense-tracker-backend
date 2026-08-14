package com.amar.expense_tracker.categorization.controller;

import com.amar.expense_tracker.auth.security.AuthenticatedUser;
import com.amar.expense_tracker.categorization.dto.CategorizationPatchRequest;
import com.amar.expense_tracker.categorization.dto.ReviewItemResponse;
import com.amar.expense_tracker.categorization.service.CategorizationReviewService;
import com.amar.expense_tracker.transaction.dto.TransactionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categorization")
@RequiredArgsConstructor
public class CategorizationController {

    private final CategorizationReviewService categorizationReviewService;

    @GetMapping("/review")
    public List<ReviewItemResponse> review(@AuthenticationPrincipal AuthenticatedUser user) {
        return categorizationReviewService.listPendingReview(user.userId());
    }

    @PostMapping("/{transactionId}/approve")
    public TransactionResponse approve(@AuthenticationPrincipal AuthenticatedUser user,
                                        @PathVariable UUID transactionId) {
        return categorizationReviewService.approve(user.userId(), transactionId);
    }

    @PatchMapping("/{transactionId}")
    public TransactionResponse patch(@AuthenticationPrincipal AuthenticatedUser user,
                                      @PathVariable UUID transactionId,
                                      @Valid @RequestBody CategorizationPatchRequest request) {
        return categorizationReviewService.patch(user.userId(), transactionId, request);
    }
}
