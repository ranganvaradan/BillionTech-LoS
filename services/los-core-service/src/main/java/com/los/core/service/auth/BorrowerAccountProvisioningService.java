package com.los.core.service.auth;

import com.los.core.model.entity.LosUser;
import com.los.core.repository.LosUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Finds an existing borrower {@link LosUser} (by email, then mobile) or provisions a new one so that
 * staff-created applications surface in the borrower portal. New accounts get a temporary,
 * mobile-based password and {@code passwordResetRequired = true} (forced reset on first login).
 *
 * <p>This is additive: it never mutates an existing user's password or role, and it does not change
 * any existing application/auth flow. Self-registration ({@link DemoAuthService#register}) is unaffected.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BorrowerAccountProvisioningService {

    private static final String ROLE_BORROWER = "BORROWER";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final LosUserRepository losUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Optional<BorrowerProvisionResult> findOrCreateBorrowerWithCredential(
            String name, String email, String mobile) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        if (normalizedEmail.isBlank()) {
            return Optional.empty();
        }
        Optional<LosUser> byEmail = losUserRepository.findByEmailIgnoreCase(normalizedEmail);
        if (byEmail.isPresent()) {
            return Optional.of(new BorrowerProvisionResult(byEmail.get(), null));
        }
        String mobileDigits = digitsOnly(mobile);
        String tempPassword = temporaryPassword(mobileDigits);
        String safeName = (name == null || name.isBlank()) ? normalizedEmail.split("@")[0] : name.trim();
        LosUser created = LosUser.builder()
                .name(safeName)
                .email(normalizedEmail)
                .mobile(mobileDigits.isBlank() ? null : mobileDigits)
                .active(true)
                .passwordHash(passwordEncoder.encode(tempPassword))
                .passwordResetRequired(true)
                .primaryLosRole(ROLE_BORROWER)
                .build();
        created = losUserRepository.save(created);
        return Optional.of(new BorrowerProvisionResult(created, tempPassword));
    }

    public record BorrowerProvisionResult(LosUser user, String temporaryPasswordForEmail) {}

    /**
     * Returns the borrower account for the given identity, creating it if absent.
     *
     * @param name   display name (falls back to email local-part when blank)
     * @param email  borrower email (required to provision; used as the login id)
     * @param mobile borrower mobile (used for matching and as the temporary password seed)
     * @return the existing or newly created borrower, or {@code Optional.empty()} when email is blank
     */
    @Transactional
    public Optional<LosUser> findOrCreateBorrower(String name, String email, String mobile) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        if (normalizedEmail.isBlank()) {
            return Optional.empty();
        }

        Optional<LosUser> byEmail = losUserRepository.findByEmailIgnoreCase(normalizedEmail);
        if (byEmail.isPresent()) {
            return byEmail;
        }

        String mobileDigits = digitsOnly(mobile);
        Optional<LosUser> byMobile = mobileDigits.length() >= 10 ? findByMobileDigits(mobileDigits) : Optional.empty();
        if (byMobile.isPresent()) {
            // Reuse mobile match only when it does not belong to a different login email (common when staff
            // reuses a test mobile across invoice-discounting borrower applications).
            String existingEmail = byMobile.get().getEmail() != null ? byMobile.get().getEmail().trim().toLowerCase() : "";
            if (existingEmail.isBlank() || existingEmail.equals(normalizedEmail)) {
                return byMobile;
            }
            log.info(
                    "Skipping mobile match for {} — already used by borrower {}",
                    mobileDigits,
                    existingEmail);
        }

        String tempPassword = temporaryPassword(mobileDigits);
        String safeName = (name == null || name.isBlank()) ? normalizedEmail.split("@")[0] : name.trim();

        LosUser created = LosUser.builder()
                .name(safeName)
                .email(normalizedEmail)
                .mobile(mobileDigits.isBlank() ? null : mobileDigits)
                .active(true)
                .passwordHash(passwordEncoder.encode(tempPassword))
                .passwordResetRequired(true)
                .primaryLosRole(ROLE_BORROWER)
                .build();
        created = losUserRepository.save(created);
        log.info("Provisioned borrower account {} for application submission (forced password reset)", created.getId());
        return Optional.of(created);
    }

    /**
     * Temporary password for an auto-provisioned borrower: the borrower's mobile number when available
     * (so staff can communicate it), otherwise a random hex string. Always paired with a forced reset.
     */
    private static String temporaryPassword(String mobileDigits) {
        if (mobileDigits != null && mobileDigits.length() >= 10) {
            return mobileDigits;
        }
        byte[] raw = new byte[8];
        RANDOM.nextBytes(raw);
        return "Tmp@" + HexFormat.of().formatHex(raw);
    }

    private Optional<LosUser> findByMobileDigits(String tenPlusDigits) {
        List<LosUser> withMobile = losUserRepository.findByMobileIsNotNull();
        for (LosUser u : withMobile) {
            if (tenPlusDigits.equals(digitsOnly(u.getMobile()))) {
                return Optional.of(u);
            }
        }
        return Optional.empty();
    }

    private static String digitsOnly(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }
}
