package com.los.core.controller;

import com.los.core.model.dto.auth.ChangePasswordRequest;
import com.los.core.model.dto.auth.ForgotPasswordRequest;
import com.los.core.model.dto.auth.ForgotPasswordResponse;
import com.los.core.model.dto.auth.LoginRequest;
import com.los.core.model.dto.auth.LoginResponse;
import com.los.core.model.dto.auth.RegisterRequest;
import com.los.core.model.dto.auth.ResetPasswordRequest;
import com.los.core.service.auth.DemoAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Demo local login and password reset. {@link com.los.core.service.auth.DemoAuthService} stores BCrypt hashes in
 * {@code los_users}. Headless flows (X-User-Id) remain unchanged; this API is for browser UI only until IAM is wired.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth (demo)", description = "Email + password demo login; not for production IAM")
public class AuthController {

    private final DemoAuthService demoAuthService;

    @PostMapping("/register")
    @Operation(summary = "Register a borrower (demo: email + mobile must be unique; verification simulated in UI only)")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(demoAuthService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email and password (demo)")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(demoAuthService.login(request));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset (demo: returns token in body)")
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(demoAuthService.forgotPassword(request));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Set new password using reset token")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        demoAuthService.resetPassword(request);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change password for the signed-in user (used by the forced first-login reset)")
    public ResponseEntity<LoginResponse> changePassword(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Valid @RequestBody ChangePasswordRequest request) {
        UUID actingUser = userId != null && !userId.isBlank() ? UUID.fromString(userId) : null;
        return ResponseEntity.ok(demoAuthService.changePassword(actingUser, request));
    }
}
