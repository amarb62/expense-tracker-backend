package com.amar.expense_tracker.auth.service;

import com.amar.expense_tracker.auth.dto.AuthResponse;
import com.amar.expense_tracker.auth.dto.LoginRequest;
import com.amar.expense_tracker.auth.dto.LogoutRequest;
import com.amar.expense_tracker.auth.dto.RefreshRequest;
import com.amar.expense_tracker.auth.dto.RegisterRequest;
import com.amar.expense_tracker.auth.dto.UserResponse;
import com.amar.expense_tracker.auth.exception.EmailAlreadyRegisteredException;
import com.amar.expense_tracker.auth.repository.RefreshTokenRepository;
import com.amar.expense_tracker.auth.security.JwtProperties;
import com.amar.expense_tracker.auth.security.JwtService;
import com.amar.expense_tracker.common.exception.ResourceNotFoundException;
import com.amar.expense_tracker.common.exception.UnauthorizedException;
import com.amar.expense_tracker.entity.RefreshTokens;
import com.amar.expense_tracker.entity.Users;
import com.amar.expense_tracker.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyRegisteredException("An account with this email already exists");
        }
        Date now = Date.from(Instant.now());
        Users user = new Users();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        Users saved = userRepository.save(user);
        return new UserResponse(saved.getId(), saved.getName(), saved.getEmail());
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Users user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        RefreshTokens existing = refreshTokenRepository.findByTokenHash(hashToken(request.refreshToken()))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        if (existing.isRevoked() || existing.getExpiresAt().before(new Date())) {
            throw new UnauthorizedException("Refresh token is no longer valid");
        }
        existing.setRevoked(true);
        refreshTokenRepository.save(existing);
        return issueTokens(existing.getUsers());
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(UUID userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return new UserResponse(user.getId(), user.getName(), user.getEmail());
    }

    @Transactional
    public void logout(LogoutRequest request) {
        refreshTokenRepository.findByTokenHash(hashToken(request.refreshToken()))
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    private AuthResponse issueTokens(Users user) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = generateOpaqueToken();

        RefreshTokens refreshToken = new RefreshTokens();
        refreshToken.setUsers(user);
        refreshToken.setTokenHash(hashToken(rawRefreshToken));
        refreshToken.setExpiresAt(Date.from(Instant.now().plus(jwtProperties.getRefreshTokenExpirationDays(), ChronoUnit.DAYS)));
        refreshToken.setRevoked(false);
        refreshToken.setCreatedAt(Date.from(Instant.now()));
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(accessToken, rawRefreshToken, "Bearer", jwtService.getAccessTokenExpirationSeconds());
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
