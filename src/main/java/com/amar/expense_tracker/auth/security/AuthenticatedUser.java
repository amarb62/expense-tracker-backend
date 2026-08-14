package com.amar.expense_tracker.auth.security;

import java.util.UUID;

public record AuthenticatedUser(UUID userId, String email) {
}
