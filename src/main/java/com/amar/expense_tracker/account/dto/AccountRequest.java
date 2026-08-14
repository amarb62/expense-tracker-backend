package com.amar.expense_tracker.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AccountRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 150) String institution,
        @NotNull AccountType accountType,
        @Pattern(regexp = "^\\d{4}$", message = "must be exactly 4 digits") String lastFourDigits,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3-letter ISO currency code") String currency,
        Boolean active
) {
}
