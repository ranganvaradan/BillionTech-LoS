package com.los.enrollment.service;

import com.los.enrollment.dto.request.OtpVerifyRequest;
import com.los.enrollment.dto.request.RegisterRequest;
import com.los.enrollment.dto.response.CustomerResponse;
import com.los.enrollment.entity.Customer;
import com.los.enrollment.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.los.enrollment.dto.request.AssistedRegisterRequest;
import com.los.enrollment.dto.request.EmailOtpRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final CustomerRepository customerRepository;
    private final StringRedisTemplate redisTemplate;

    private static final String OTP_PREFIX = "otp:mobile:";
    private static final String EMAIL_OTP_PREFIX = "otp:email:";
    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final int OTP_LENGTH = 6;

    @Transactional
    public CustomerResponse register(RegisterRequest request) {
        if (customerRepository.existsByMobile(request.getMobile())) {
            Customer existing = customerRepository.findByMobile(request.getMobile())
                    .orElseThrow();
            return toResponse(existing);
        }

        Customer customer = Customer.builder()
                .fullName(request.getFullName())
                .mobile(request.getMobile())
                .email(request.getEmail())
                .build();

        customer = customerRepository.save(customer);
        log.info("Customer registered: {} (mobile: {})", customer.getId(), request.getMobile());

        sendOtp(request.getMobile());

        return toResponse(customer);
    }

    public void sendOtp(String mobile) {
        String otp = generateOtp();
        redisTemplate.opsForValue().set(OTP_PREFIX + mobile, otp, OTP_TTL);
        log.info("OTP generated for mobile: {} (OTP: {} — would be sent via SMS in production)", mobile, otp);
    }

    @Transactional
    public CustomerResponse verifyOtp(OtpVerifyRequest request) {
        String storedOtp = redisTemplate.opsForValue().get(OTP_PREFIX + request.getMobile());

        if (storedOtp == null) {
            throw new RuntimeException("OTP expired or not found. Request a new OTP.");
        }

        if (!storedOtp.equals(request.getOtp())) {
            throw new RuntimeException("Invalid OTP");
        }

        redisTemplate.delete(OTP_PREFIX + request.getMobile());

        Customer customer = customerRepository.findByMobile(request.getMobile())
                .orElseThrow(() -> new RuntimeException("Customer not found for mobile: " + request.getMobile()));

        customer.setMobileVerified(true);
        customer = customerRepository.save(customer);
        log.info("Mobile verified for customer: {}", customer.getId());

        return toResponse(customer);
    }

    @Transactional
    public CustomerResponse recordConsent(UUID customerId, String ipAddress) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        customer.setConsentGiven(true);
        customer.setConsentTimestamp(Instant.now());
        customer.setConsentIpAddress(ipAddress);
        customer = customerRepository.save(customer);
        log.info("Consent recorded for customer: {} from IP: {}", customerId, ipAddress);

        return toResponse(customer);
    }

    public CustomerResponse getCustomer(UUID id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer not found"));
        return toResponse(customer);
    }

    private String generateOtp() {
        int otp = ThreadLocalRandom.current().nextInt(100000, 1000000);
        return String.valueOf(otp);
    }

    /**
     * BR-1.4: RM-initiated assisted registration.
     */
    @Transactional
    public CustomerResponse assistedRegister(AssistedRegisterRequest request) {
        if (customerRepository.existsByMobile(request.getMobile())) {
            Customer existing = customerRepository.findByMobile(request.getMobile()).orElseThrow();
            return toResponse(existing);
        }

        Customer customer = Customer.builder()
                .fullName(request.getFullName())
                .mobile(request.getMobile())
                .email(request.getEmail())
                .build();

        customer = customerRepository.save(customer);
        log.info("Assisted registration by RM {} for customer: {} (mobile: {}, channel: {})",
                request.getRmUserId(), customer.getId(), request.getMobile(), request.getChannel());

        sendOtp(request.getMobile());
        return toResponse(customer);
    }

    /**
     * BR-1.5: Send email OTP.
     */
    public void sendEmailOtp(String email) {
        String otp = generateOtp();
        redisTemplate.opsForValue().set(EMAIL_OTP_PREFIX + email, otp, OTP_TTL);
        log.info("Email OTP generated for: {} (OTP: {} — would be sent via email in production)", email, otp);
    }

    /**
     * BR-1.5: Verify email OTP.
     */
    @Transactional
    public CustomerResponse verifyEmailOtp(EmailOtpRequest request) {
        String storedOtp = redisTemplate.opsForValue().get(EMAIL_OTP_PREFIX + request.getEmail());
        if (storedOtp == null) {
            throw new RuntimeException("Email OTP expired or not found. Request a new OTP.");
        }
        if (!storedOtp.equals(request.getOtp())) {
            throw new RuntimeException("Invalid email OTP");
        }

        redisTemplate.delete(EMAIL_OTP_PREFIX + request.getEmail());

        Customer customer = customerRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Customer not found for email: " + request.getEmail()));

        customer.setEmailVerified(true);
        customer = customerRepository.save(customer);
        log.info("Email verified for customer: {}", customer.getId());

        return toResponse(customer);
    }

    /**
     * BR-1.7: DigiLocker integration — initiate document fetch.
     */
    public Map<String, Object> initiateDigiLocker(UUID customerId) {
        log.info("Initiating DigiLocker document fetch for customer: {}", customerId);
        // In production, this would redirect to DigiLocker OAuth flow
        return Map.of(
                "customerId", customerId.toString(),
                "status", "INITIATED",
                "redirectUrl", "https://digilocker.gov.in/oauth/authorize?client_id=LOS_APP&redirect_uri=...",
                "message", "Redirect customer to DigiLocker for document consent"
        );
    }

    /**
     * BR-1.7: DigiLocker callback — process fetched documents.
     */
    public Map<String, Object> processDigiLockerCallback(UUID customerId, String authorizationCode) {
        log.info("DigiLocker callback received for customer: {} with code: {}", customerId, authorizationCode);
        // In production, exchange code for access token, fetch documents
        return Map.of(
                "customerId", customerId.toString(),
                "status", "DOCUMENTS_FETCHED",
                "documents", List.of(
                        Map.of("type", "AADHAAR", "issuerId", "UIDAI", "status", "VERIFIED"),
                        Map.of("type", "PAN", "issuerId", "INCOMETAX", "status", "VERIFIED"),
                        Map.of("type", "DRIVING_LICENSE", "issuerId", "PARIVAHAN", "status", "AVAILABLE")
                ),
                "message", "Documents fetched from DigiLocker successfully"
        );
    }

    private CustomerResponse toResponse(Customer customer) {
        return CustomerResponse.builder()
                .id(customer.getId())
                .fullName(customer.getFullName())
                .mobile(customer.getMobile())
                .email(customer.getEmail())
                .mobileVerified(customer.isMobileVerified())
                .emailVerified(customer.isEmailVerified())
                .consentGiven(customer.isConsentGiven())
                .createdAt(customer.getCreatedAt())
                .build();
    }
}
