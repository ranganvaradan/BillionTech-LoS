package com.los.core.service.integration.providers.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.model.enums.KycStepType;
import com.los.core.service.audit.IntegrationApiAuditService;
import com.los.core.service.integration.providers.IKycProvider.KycVerificationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AuthbridgeMobileOtpMappingTest {

    @Mock
    IntegrationApiAuditService auditService;

    AuthbridgeKycProvider provider;

    @BeforeEach
    void setUp() {
        provider = new AuthbridgeKycProvider(auditService, new ObjectMapper());
        lenient().doNothing().when(auditService).recordJson(
                anyString(), anyString(), any(), any(), anyString(), anyInt(),
                any(), anyString(), any(), any(), any(), anyLong(), any());
    }

    @Test
    void mobileOtpSucceedsWhenOnlyMobileAliasPresent() {
        KycVerificationResult r = provider.verify(
                KycStepType.MOBILE_OTP,
                Map.of("mobile", "9876543210", "applicationId", "00000000-0000-0000-0000-000000000001"));
        assertThat(r.success()).isTrue();
        assertThat(r.parsedData()).containsEntry("mobileVerified", true);
    }

    @Test
    void mobileOtpSucceedsWithPlus91Phone() {
        KycVerificationResult r = provider.verify(
                KycStepType.MOBILE_OTP,
                Map.of("phone", "+91 9123456789"));
        assertThat(r.success()).isTrue();
    }

    @Test
    void mobileOtpFailsWithRequiredMessageWhenMissing() {
        KycVerificationResult r = provider.verify(KycStepType.MOBILE_OTP, Map.of("name", "x"));
        assertThat(r.success()).isFalse();
        assertThat(r.errorMessage()).containsIgnoringCase("required");
    }
}
