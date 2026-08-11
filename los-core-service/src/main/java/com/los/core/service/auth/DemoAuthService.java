package com.los.core.service.auth;

import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.UnauthorizedException;
import com.los.core.model.dto.auth.ChangePasswordRequest;
import com.los.core.model.dto.auth.ForgotPasswordRequest;
import com.los.core.model.dto.auth.ForgotPasswordResponse;
import com.los.core.model.dto.auth.LoginRequest;
import com.los.core.model.dto.auth.LoginResponse;
import com.los.core.model.dto.auth.RegisterRequest;
import com.los.core.model.dto.auth.ResetPasswordRequest;
import com.los.core.model.entity.LosUser;
import com.los.core.repository.LosUserRepository;
import com.los.core.security.LosJwtService;
import com.los.core.security.SingleTenantDeploymentGuard;
import com.los.core.service.borrower.BorrowerApplicationOwnershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * <p>Demo authentication only: email + BCrypt password against {@code los_users}.
 * Production must use IAM/OAuth/JWT (or session) and email-based password reset delivery;
 * do not expose reset tokens in API responses.</p>
 */
@Service
@RequiredArgsConstructor
public class DemoAuthService {

    public static final String DEMO_INSTITUTION = "Billionloans Financial Services Private Ltd.";

    public static final String ROLE_BORROWER = "BORROWER";

    private static final Pattern STRONG_PASSWORD = Pattern.compile("^(?=.*[0-9])(?=.*[^A-Za-z0-9\\s]).{8,128}$");

    private static final SecureRandom RANDOM = new SecureRandom();

    private final LosUserRepository losUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final BorrowerApplicationOwnershipService borrowerApplicationOwnershipService;
    private final LosJwtService losJwtService;
    private final SingleTenantDeploymentGuard singleTenantDeploymentGuard;

    @Transactional
    public LoginResponse register(RegisterRequest req) {
        if (!req.getPassword().equals(req.getConfirmPassword())) {
            throw new BusinessRuleException("Passwords do not match", "VALIDATION", "Re-enter the same password", Map.of());
        }
        if (!STRONG_PASSWORD.matcher(req.getPassword()).matches()) {
            throw new BusinessRuleException(
                    "Password must be at least 8 characters and include a number and a special character",
                    "VALIDATION",
                    "Choose a stronger password",
                    Map.of());
        }
        String email = req.getEmail().trim().toLowerCase();
        if (losUserRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessRuleException("This email is already registered", "DUPLICATE_EMAIL", "Use another email or sign in", Map.of());
        }
        String mobileDigits = req.getMobile().replaceAll("\\D", "");
        if (mobileDigits.length() < 10) {
            throw new BusinessRuleException("Enter a valid mobile number (at least 10 digits)", "VALIDATION", "Fix mobile number", Map.of());
        }
        if (losUserRepository.existsByNonNullMobile(mobileDigits)) {
            throw new BusinessRuleException("This mobile number is already registered", "DUPLICATE_MOBILE", "Use another number or sign in", Map.of());
        }
        LosUser u = LosUser.builder()
                .name(req.getName().trim())
                .email(email)
                .mobile(mobileDigits)
                .active(true)
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .primaryLosRole(ROLE_BORROWER)
                .build();
        u = losUserRepository.save(u);
        return toLoginResponse(u, ROLE_BORROWER);
    }

    public LoginResponse login(LoginRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        LosUser u = losUserRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        if (!u.isActive()) {
            throw new UnauthorizedException("Invalid email or password");
        }
        String hash = u.getPasswordHash();
        if (hash == null || hash.isBlank() || !passwordEncoder.matches(req.getPassword(), hash)) {
            throw new UnauthorizedException("Invalid email or password");
        }
        String role = u.getPrimaryLosRole() != null ? u.getPrimaryLosRole() : "OPERATIONS";
        if ("BORROWER".equalsIgnoreCase(role)) {
            borrowerApplicationOwnershipService.reconcileCustomerId(u.getId());
        }
        return toLoginResponse(u, role);
    }

    private LoginResponse toLoginResponse(LosUser u, String role) {
        String token = null;
        if (losJwtService.isConfigured()) {
            token = losJwtService.issueAccessToken(
                    u.getId(), role, u.getEmail(), u.getName(),
                    singleTenantDeploymentGuard.deploymentTenantId());
        }
        return LoginResponse.builder()
                .userId(u.getId())
                .name(u.getName())
                .email(u.getEmail())
                .role(role)
                .institution(DEMO_INSTITUTION)
                .passwordResetRequired(u.isPasswordResetRequired())
                .accessToken(token)
                .tokenType(token == null ? null : "Bearer")
                .build();
    }

    @Transactional
    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        var opt = losUserRepository.findByEmailIgnoreCase(email);
        if (opt.isEmpty()) {
            return ForgotPasswordResponse.builder()
                    .message("If an account exists for this email, you can use the reset flow once email is enabled.")
                    .build();
        }
        LosUser u = opt.get();
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = HexFormat.of().formatHex(raw);
        Instant exp = Instant.now().plus(24, ChronoUnit.HOURS);
        u.setPasswordResetToken(token);
        u.setPasswordResetTokenExpiresAt(exp);
        losUserRepository.save(u);

        String resetPath = "/reset-password?token=" + token;
        return ForgotPasswordResponse.builder()
                .message("Demo: use the link below to set a new password (no email sent yet).")
                .resetToken(token)
                .resetPath(resetPath)
                .resetLink(resetPath)
                .expiresAt(exp)
                .build();
    }

    /**
     * Authenticated password change for a signed-in user. Verifies the current/temporary password,
     * enforces the same strength policy as registration, and clears the forced-reset flag on success.
     */
    @Transactional
    public LoginResponse changePassword(java.util.UUID userId, ChangePasswordRequest req) {
        if (userId == null) {
            throw new UnauthorizedException("Sign-in is required");
        }
        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new BusinessRuleException("Passwords do not match", "VALIDATION", "Re-enter the same password", Map.of());
        }
        if (!STRONG_PASSWORD.matcher(req.getNewPassword()).matches()) {
            throw new BusinessRuleException(
                    "Password must be at least 8 characters and include a number and a special character",
                    "VALIDATION",
                    "Choose a stronger password",
                    Map.of());
        }
        LosUser u = losUserRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Invalid session"));
        String hash = u.getPasswordHash();
        if (hash == null || hash.isBlank() || !passwordEncoder.matches(req.getCurrentPassword(), hash)) {
            throw new UnauthorizedException("Current password is incorrect");
        }
        u.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        u.setPasswordResetRequired(false);
        u.setPasswordResetToken(null);
        u.setPasswordResetTokenExpiresAt(null);
        u = losUserRepository.save(u);
        String role = u.getPrimaryLosRole() != null ? u.getPrimaryLosRole() : "OPERATIONS";
        return LoginResponse.builder()
                .userId(u.getId())
                .name(u.getName())
                .email(u.getEmail())
                .role(role)
                .institution(DEMO_INSTITUTION)
                .passwordResetRequired(false)
                .build();
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        LosUser u = losUserRepository.findByPasswordResetToken(req.getToken().trim())
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired reset token"));
        Instant exp = u.getPasswordResetTokenExpiresAt();
        if (exp == null || exp.isBefore(Instant.now())) {
            throw new UnauthorizedException("Invalid or expired reset token");
        }
        u.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        u.setPasswordResetToken(null);
        u.setPasswordResetTokenExpiresAt(null);
        losUserRepository.save(u);
    }
}
