package com.amar.expense_tracker.auth.dto;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String email
) {
}
