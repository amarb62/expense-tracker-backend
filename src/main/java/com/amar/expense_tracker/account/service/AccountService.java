package com.amar.expense_tracker.account.service;

import com.amar.expense_tracker.account.dto.AccountRequest;
import com.amar.expense_tracker.account.dto.AccountResponse;
import com.amar.expense_tracker.account.mapper.AccountMapper;
import com.amar.expense_tracker.account.repository.AccountRepository;
import com.amar.expense_tracker.common.exception.ConflictException;
import com.amar.expense_tracker.common.exception.ResourceNotFoundException;
import com.amar.expense_tracker.entity.Accounts;
import com.amar.expense_tracker.entity.Users;
import com.amar.expense_tracker.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final AccountMapper accountMapper;

    @Transactional
    public AccountResponse create(UUID userId, AccountRequest request) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Date now = Date.from(Instant.now());
        Accounts account = new Accounts();
        account.setUsers(user);
        applyRequest(account, request);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);

        return accountMapper.toResponse(accountRepository.save(account));
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> list(UUID userId) {
        return accountRepository.findByUsers_IdOrderByCreatedAtDesc(userId).stream()
                .map(accountMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse get(UUID userId, UUID accountId) {
        return accountMapper.toResponse(findOwned(userId, accountId));
    }

    @Transactional
    public AccountResponse update(UUID userId, UUID accountId, AccountRequest request) {
        Accounts account = findOwned(userId, accountId);
        applyRequest(account, request);
        account.setUpdatedAt(Date.from(Instant.now()));
        return accountMapper.toResponse(accountRepository.save(account));
    }

    @Transactional
    public void delete(UUID userId, UUID accountId) {
        Accounts account = findOwned(userId, accountId);
        if (accountRepository.hasTransactions(accountId) || accountRepository.hasStatements(accountId)) {
            throw new ConflictException(
                    "Cannot delete an account with existing transactions or statements; deactivate it instead");
        }
        accountRepository.delete(account);
    }

    private void applyRequest(Accounts account, AccountRequest request) {
        account.setName(request.name());
        account.setInstitution(request.institution());
        account.setAccountType(request.accountType().name());
        account.setLastFourDigits(request.lastFourDigits());
        account.setCurrency(request.currency());
        account.setActive(request.active() == null || request.active());
    }

    private Accounts findOwned(UUID userId, UUID accountId) {
        return accountRepository.findByIdAndUsers_Id(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }
}
