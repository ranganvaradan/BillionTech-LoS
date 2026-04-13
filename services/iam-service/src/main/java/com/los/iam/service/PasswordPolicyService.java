package com.los.iam.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Password policy enforcement — complexity, length, and common password checks.
 * BR-14.6: Password policy enforcement (complexity, expiry, history).
 */
@Component
public class PasswordPolicyService {

    private static final int MIN_LENGTH = 10;
    private static final int MAX_LENGTH = 128;
    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]");

    private static final List<String> COMMON_PASSWORDS = List.of(
            "password", "12345678", "qwerty", "admin123", "letmein",
            "welcome1", "password1", "abc12345", "monkey123", "master1"
    );

    /**
     * Validate password against policy. Returns list of violations (empty = valid).
     */
    public List<String> validate(String password, String username) {
        List<String> violations = new ArrayList<>();

        if (password == null || password.isEmpty()) {
            violations.add("Password is required");
            return violations;
        }

        if (password.length() < MIN_LENGTH) {
            violations.add("Password must be at least " + MIN_LENGTH + " characters");
        }
        if (password.length() > MAX_LENGTH) {
            violations.add("Password must not exceed " + MAX_LENGTH + " characters");
        }
        if (!UPPERCASE.matcher(password).find()) {
            violations.add("Password must contain at least one uppercase letter");
        }
        if (!LOWERCASE.matcher(password).find()) {
            violations.add("Password must contain at least one lowercase letter");
        }
        if (!DIGIT.matcher(password).find()) {
            violations.add("Password must contain at least one digit");
        }
        if (!SPECIAL.matcher(password).find()) {
            violations.add("Password must contain at least one special character");
        }
        if (username != null && password.toLowerCase().contains(username.toLowerCase())) {
            violations.add("Password must not contain the username");
        }
        if (COMMON_PASSWORDS.stream().anyMatch(cp -> password.toLowerCase().contains(cp))) {
            violations.add("Password is too common");
        }

        return violations;
    }

    /**
     * Check if a password matches any in the history list (BCrypt encoded).
     */
    public boolean isInHistory(String rawPassword, List<String> passwordHistory,
                                org.springframework.security.crypto.password.PasswordEncoder encoder) {
        return passwordHistory.stream().anyMatch(old -> encoder.matches(rawPassword, old));
    }
}
