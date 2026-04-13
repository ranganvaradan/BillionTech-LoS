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

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final CustomerRepository customerRepository;
    private final StringRedisTemplate redisTemplate;

    private static final String OTP_PREFIX = "otp:mobile:";
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
