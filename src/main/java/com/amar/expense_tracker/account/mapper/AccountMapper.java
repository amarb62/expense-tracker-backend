package com.amar.expense_tracker.account.mapper;

import com.amar.expense_tracker.account.dto.AccountResponse;
import com.amar.expense_tracker.account.dto.AccountType;
import com.amar.expense_tracker.entity.Accounts;
import org.springframework.stereotype.Component;

@Component
public class AccountMapper {

    public AccountResponse toResponse(Accounts account) {
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getInstitution(),
                AccountType.valueOf(account.getAccountType()),
                account.getLastFourDigits(),
                account.getCurrency(),
                account.isActive(),
                account.getCreatedAt().toInstant(),
                account.getUpdatedAt().toInstant()
        );
    }
}
