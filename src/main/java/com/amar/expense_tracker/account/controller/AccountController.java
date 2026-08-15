package com.amar.expense_tracker.account.controller;

import com.amar.expense_tracker.account.dto.AccountRequest;
import com.amar.expense_tracker.account.dto.AccountResponse;
import com.amar.expense_tracker.account.service.AccountService;
import com.amar.expense_tracker.auth.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Bank accounts and credit cards, scoped to the authenticated user")
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a bank account or credit card")
    public AccountResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                   @Valid @RequestBody AccountRequest request) {
        return accountService.create(user.userId(), request);
    }

    @GetMapping
    @Operation(summary = "List the current user's accounts")
    public List<AccountResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return accountService.list(user.userId());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an account by ID")
    public AccountResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return accountService.get(user.userId(), id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an account")
    public AccountResponse update(@AuthenticationPrincipal AuthenticatedUser user,
                                   @PathVariable UUID id,
                                   @Valid @RequestBody AccountRequest request) {
        return accountService.update(user.userId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an account (blocked with 409 if it has transactions or statements; deactivate instead)")
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        accountService.delete(user.userId(), id);
    }
}
