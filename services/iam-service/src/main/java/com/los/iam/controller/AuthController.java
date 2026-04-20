package com.los.iam.controller;

import com.los.iam.dto.request.ChangePasswordRequest;
import com.los.iam.dto.request.CreateUserRequest;
import com.los.iam.dto.request.LoginRequest;
import com.los.iam.dto.response.AuthResponse;
import com.los.iam.dto.response.TwoFactorSetupResponse;
import com.los.iam.dto.response.UserResponse;
import com.los.iam.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login, registration, JWT token management, 2FA")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Login with username/password and optional TOTP code")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        String ua = httpRequest.getHeader("User-Agent");
        AuthResponse response = authService.login(request, ip, ua);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using refresh token")
    public ResponseEntity<AuthResponse> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        AuthResponse response = authService.refreshAccessToken(refreshToken);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout — revoke all refresh tokens for user")
    public ResponseEntity<Void> logout(@RequestHeader("X-User-Id") String userId) {
        authService.logout(UUID.fromString(userId));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    @Operation(summary = "Create a new user (admin only in production)")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse user = authService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user profile")
    public ResponseEntity<UserResponse> getCurrentUser(@RequestHeader("X-User-Id") String userId) {
        UserResponse user = authService.getUserById(UUID.fromString(userId));
        return ResponseEntity.ok(user);
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change password for current user")
    public ResponseEntity<Void> changePassword(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(UUID.fromString(userId), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/2fa/setup")
    @Operation(summary = "Generate TOTP secret and QR code URI for 2FA setup")
    public ResponseEntity<TwoFactorSetupResponse> setupTwoFactor(@RequestHeader("X-User-Id") String userId) {
        TwoFactorSetupResponse response = authService.setupTwoFactor(UUID.fromString(userId));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/2fa/enable")
    @Operation(summary = "Enable 2FA after verifying TOTP code")
    public ResponseEntity<Void> enableTwoFactor(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, String> body) {
        String totpCode = body.get("totpCode");
        authService.enableTwoFactor(UUID.fromString(userId), totpCode);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/2fa/disable")
    @Operation(summary = "Disable 2FA for current user")
    public ResponseEntity<Void> disableTwoFactor(@RequestHeader("X-User-Id") String userId) {
        authService.disableTwoFactor(UUID.fromString(userId));
        return ResponseEntity.noContent().build();
    }
}
