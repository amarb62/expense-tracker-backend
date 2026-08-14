package com.amar.expense_tracker.transaction.controller;

import com.amar.expense_tracker.auth.security.AuthenticatedUser;
import com.amar.expense_tracker.common.dto.PageResponse;
import com.amar.expense_tracker.transaction.dto.CategoryPatchRequest;
import com.amar.expense_tracker.transaction.dto.ExpenseRequest;
import com.amar.expense_tracker.transaction.dto.IncomeRequest;
import com.amar.expense_tracker.transaction.dto.TransactionResponse;
import com.amar.expense_tracker.transaction.dto.TransactionSource;
import com.amar.expense_tracker.transaction.dto.TransactionType;
import com.amar.expense_tracker.transaction.dto.TransactionUpdateRequest;
import com.amar.expense_tracker.transaction.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse createExpense(@AuthenticationPrincipal AuthenticatedUser user,
                                              @Valid @RequestBody ExpenseRequest request) {
        return transactionService.createExpense(user.userId(), request);
    }

    @PostMapping("/income")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse createIncome(@AuthenticationPrincipal AuthenticatedUser user,
                                             @Valid @RequestBody IncomeRequest request) {
        return transactionService.createIncome(user.userId(), request);
    }

    @GetMapping
    public PageResponse<TransactionResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) TransactionType transactionType,
            @RequestParam(required = false) TransactionSource source,
            @PageableDefault(size = 20, sort = "transactionDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return transactionService.list(user.userId(), fromDate, toDate, accountId, categoryId,
                transactionType, source, pageable);
    }

    @GetMapping("/{id}")
    public TransactionResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return transactionService.get(user.userId(), id);
    }

    @PutMapping("/{id}")
    public TransactionResponse update(@AuthenticationPrincipal AuthenticatedUser user,
                                       @PathVariable UUID id,
                                       @Valid @RequestBody TransactionUpdateRequest request) {
        return transactionService.update(user.userId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        transactionService.delete(user.userId(), id);
    }

    @PatchMapping("/{id}/category")
    public TransactionResponse patchCategory(@AuthenticationPrincipal AuthenticatedUser user,
                                              @PathVariable UUID id,
                                              @Valid @RequestBody CategoryPatchRequest request) {
        return transactionService.patchCategory(user.userId(), id, request);
    }
}
