package com.los.iam.service;

import com.los.iam.dto.request.ChangePasswordRequest;
import com.los.iam.dto.request.CreateUserRequest;
import com.los.iam.dto.request.LoginRequest;
import com.los.iam.dto.response.AuthResponse;
import com.los.iam.dto.response.TwoFactorSetupResponse;
import com.los.iam.dto.response.UserResponse;
import com.los.iam.entity.LoginAudit;
import com.los.iam.entity.RefreshToken;
import com.los.iam.entity.User;
import com.los.iam.enums.Role;
import com.los.iam.repository.LoginAuditRepository;
import com.los.iam.repository.RefreshTokenRepository;
import com.los.iam.repository.UserRepository;
import com.los.iam.security.JwtTokenProvider;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginAuditRepository loginAuditRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordPolicyService passwordPolicyService;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCK_DURATION_MINUTES = 15;

    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress, String userAgent) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> {
                    auditLogin(request.getUsername(), false, ipAddress, userAgent, "User not found");
                    return new RuntimeException("Invalid credentials");
                });

        if (!user.isEnabled()) {
            auditLogin(request.getUsername(), false, ipAddress, userAgent, "Account disabled");
            throw new RuntimeException("Account is disabled");
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            auditLogin(request.getUsername(), false, ipAddress, userAgent, "Account locked");
            throw new RuntimeException("Account is locked. Try again later.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
            if (user.getFailedLoginAttempts() >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(Instant.now().plusSeconds(LOCK_DURATION_MINUTES * 60));
                log.warn("Account locked for user: {} after {} failed attempts", user.getUsername(), MAX_FAILED_ATTEMPTS);
            }
            userRepository.save(user);
            auditLogin(request.getUsername(), false, ipAddress, userAgent, "Invalid password");
            throw new RuntimeException("Invalid credentials");
        }

        if (user.isTwoFactorEnabled()) {
            if (request.getTotpCode() == null || request.getTotpCode().isBlank()) {
                throw new RuntimeException("TOTP code is required");
            }
            if (!verifyTotpCode(user.getTwoFactorSecret(), request.getTotpCode())) {
                auditLogin(request.getUsername(), false, ipAddress, userAgent, "Invalid TOTP code");
                throw new RuntimeException("Invalid TOTP code");
            }
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        String roles = user.getRoles().stream()
                .map(Role::name)
                .collect(Collectors.joining(","));

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), roles);
        String refreshTokenStr = jwtTokenProvider.generateRefreshToken(user.getId());

        RefreshToken refreshToken = RefreshToken.builder()
                .token(refreshTokenStr)
                .user(user)
                .expiresAt(Instant.now().plusMillis(jwtTokenProvider.getRefreshTokenExpiry()))
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();
        refreshTokenRepository.save(refreshToken);

        auditLogin(request.getUsername(), true, ipAddress, userAgent, null);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenStr)
                .expiresIn(jwtTokenProvider.getAccessTokenExpiry() / 1000)
                .tokenType("Bearer")
                .user(toUserResponse(user))
                .build();
    }

    @Transactional
    public AuthResponse refreshAccessToken(String refreshTokenStr) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenAndRevokedFalse(refreshTokenStr)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
            refreshToken.setRevoked(true);
            refreshTokenRepository.save(refreshToken);
            throw new RuntimeException("Refresh token expired");
        }

        User user = refreshToken.getUser();
        String roles = user.getRoles().stream()
                .map(Role::name)
                .collect(Collectors.joining(","));

        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), roles);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshTokenStr)
                .expiresIn(jwtTokenProvider.getAccessTokenExpiry() / 1000)
                .tokenType("Bearer")
                .user(toUserResponse(user))
                .build();
    }

    @Transactional
    public void logout(UUID userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .mobile(request.getMobile())
                .roles(request.getRoles())
                .build();

        user = userRepository.save(user);
        log.info("User created: {} with roles: {}", user.getUsername(), user.getRoles());
        return toUserResponse(user);
    }

    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return toUserResponse(user);
    }

    public Page<UserResponse> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toUserResponse);
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Current password is incorrect");
        }

        // Enforce password policy
        java.util.List<String> violations = passwordPolicyService.validate(request.getNewPassword(), user.getUsername());
        if (!violations.isEmpty()) {
            throw new RuntimeException("Password policy violations: " + String.join("; ", violations));
        }

        // Check password history (last 5 passwords)
        if (passwordPolicyService.isInHistory(request.getNewPassword(), user.getPasswordHistory(), passwordEncoder)) {
            throw new RuntimeException("Cannot reuse any of the last 5 passwords");
        }

        // Save current password to history (keep last 5)
        user.getPasswordHistory().add(0, user.getPasswordHash());
        if (user.getPasswordHistory().size() > 5) {
            user.setPasswordHistory(user.getPasswordHistory().subList(0, 5));
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedAt(Instant.now());
        userRepository.save(user);
        refreshTokenRepository.revokeAllByUserId(userId);
        log.info("Password changed for user: {}", user.getUsername());
    }

    @Transactional
    public TwoFactorSetupResponse setupTwoFactor(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        SecretGenerator secretGenerator = new DefaultSecretGenerator();
        String secret = secretGenerator.generate();

        user.setTwoFactorSecret(secret);
        userRepository.save(user);

        String qrUri = String.format(
                "otpauth://totp/LOS:%s?secret=%s&issuer=LOS&digits=6",
                user.getUsername(), secret
        );

        return TwoFactorSetupResponse.builder()
                .secret(secret)
                .qrCodeUri(qrUri)
                .build();
    }

    @Transactional
    public void enableTwoFactor(UUID userId, String totpCode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getTwoFactorSecret() == null) {
            throw new RuntimeException("Two-factor not set up. Call setup first.");
        }

        if (!verifyTotpCode(user.getTwoFactorSecret(), totpCode)) {
            throw new RuntimeException("Invalid TOTP code. Two-factor NOT enabled.");
        }

        user.setTwoFactorEnabled(true);
        userRepository.save(user);
        log.info("2FA enabled for user: {}", user.getUsername());
    }

    @Transactional
    public void disableTwoFactor(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setTwoFactorEnabled(false);
        user.setTwoFactorSecret(null);
        userRepository.save(user);
        log.info("2FA disabled for user: {}", user.getUsername());
    }

    private boolean verifyTotpCode(String secret, String code) {
        CodeVerifier verifier = new DefaultCodeVerifier(
                new DefaultCodeGenerator(),
                new SystemTimeProvider()
        );
        return verifier.isValidCode(secret, code);
    }

    private void auditLogin(String username, boolean success, String ipAddress, String userAgent, String failureReason) {
        LoginAudit audit = LoginAudit.builder()
                .username(username)
                .success(success)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .failureReason(failureReason)
                .build();
        loginAuditRepository.save(audit);
    }

    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .mobile(user.getMobile())
                .roles(user.getRoles())
                .enabled(user.isEnabled())
                .twoFactorEnabled(user.isTwoFactorEnabled())
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }
}
