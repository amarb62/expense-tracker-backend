package com.amar.expense_tracker.auth.controller;

import com.amar.expense_tracker.auth.dto.AuthResponse;
import com.amar.expense_tracker.auth.dto.LoginRequest;
import com.amar.expense_tracker.auth.dto.LogoutRequest;
import com.amar.expense_tracker.auth.dto.RefreshRequest;
import com.amar.expense_tracker.auth.dto.RegisterRequest;
import com.amar.expense_tracker.auth.dto.UserResponse;
import com.amar.expense_tracker.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Registration, login, token refresh, and logout")
@SecurityRequirements
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new user account")
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in with email and password, returning a JWT access token and refresh token")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a valid refresh token for a new access token, rotating the refresh token")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke a refresh token")
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
    }
}
