package com.los.enrollment.controller;

import com.los.enrollment.dto.request.AssistedRegisterRequest;
import com.los.enrollment.dto.request.EmailOtpRequest;
import com.los.enrollment.dto.request.OtpVerifyRequest;
import com.los.enrollment.dto.request.RegisterRequest;
import com.los.enrollment.dto.response.CustomerResponse;
import com.los.enrollment.service.EnrollmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/enrollment")
@RequiredArgsConstructor
@Tag(name = "Enrollment", description = "Customer self-registration, OTP, consent")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    @PostMapping("/register")
    @Operation(summary = "Register a new customer with mobile number")
    public ResponseEntity<CustomerResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(enrollmentService.register(request));
    }

    @PostMapping("/otp/send")
    @Operation(summary = "Send OTP to mobile number")
    public ResponseEntity<Map<String, String>> sendOtp(@RequestBody Map<String, String> body) {
        enrollmentService.sendOtp(body.get("mobile"));
        return ResponseEntity.ok(Map.of("message", "OTP sent successfully"));
    }

    @PostMapping("/otp/verify")
    @Operation(summary = "Verify OTP for mobile number")
    public ResponseEntity<CustomerResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        return ResponseEntity.ok(enrollmentService.verifyOtp(request));
    }

    @PostMapping("/{customerId}/consent")
    @Operation(summary = "Record customer consent for data processing")
    public ResponseEntity<CustomerResponse> recordConsent(
            @PathVariable UUID customerId,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(enrollmentService.recordConsent(customerId, httpRequest.getRemoteAddr()));
    }

    @GetMapping("/{customerId}")
    @Operation(summary = "Get customer by ID")
    public ResponseEntity<CustomerResponse> getCustomer(@PathVariable UUID customerId) {
        return ResponseEntity.ok(enrollmentService.getCustomer(customerId));
    }

    @PostMapping("/assisted-register")
    @Operation(summary = "BR-1.4: RM-initiated assisted registration")
    public ResponseEntity<CustomerResponse> assistedRegister(
            @Valid @RequestBody AssistedRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(enrollmentService.assistedRegister(request));
    }

    @PostMapping("/email-otp/send")
    @Operation(summary = "BR-1.5: Send email OTP")
    public ResponseEntity<Map<String, String>> sendEmailOtp(@RequestBody Map<String, String> body) {
        enrollmentService.sendEmailOtp(body.get("email"));
        return ResponseEntity.ok(Map.of("message", "Email OTP sent successfully"));
    }

    @PostMapping("/email-otp/verify")
    @Operation(summary = "BR-1.5: Verify email OTP")
    public ResponseEntity<CustomerResponse> verifyEmailOtp(@Valid @RequestBody EmailOtpRequest request) {
        return ResponseEntity.ok(enrollmentService.verifyEmailOtp(request));
    }

    @PostMapping("/{customerId}/digilocker/initiate")
    @Operation(summary = "BR-1.7: Initiate DigiLocker document fetch")
    public ResponseEntity<Map<String, Object>> initiateDigiLocker(@PathVariable UUID customerId) {
        return ResponseEntity.ok(enrollmentService.initiateDigiLocker(customerId));
    }

    @PostMapping("/{customerId}/digilocker/callback")
    @Operation(summary = "BR-1.7: Process DigiLocker callback")
    public ResponseEntity<Map<String, Object>> processDigiLockerCallback(
            @PathVariable UUID customerId,
            @RequestParam String authorizationCode) {
        return ResponseEntity.ok(enrollmentService.processDigiLockerCallback(customerId, authorizationCode));
    }
}
