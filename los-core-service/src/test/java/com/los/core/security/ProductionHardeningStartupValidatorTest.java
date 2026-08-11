package com.los.core.security;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionHardeningStartupValidatorTest {

    @Mock CreditIntelligenceInternalTokenService tokenService;
    @Mock LosJwtService jwtService;
    @Mock Environment environment;

    private CreditIntelligenceProperties ci;
    private ProductionHardeningStartupValidator validator;

    @BeforeEach
    void setUp() {
        ci = new CreditIntelligenceProperties();
        ci.setInternalTokenRequired(true);
        ci.getStagingDemo().setEnabled(false);
        ci.getValidation().setEnabled(false);
        ci.getCutover().setAllowCanonicalAuthority(false);
        ci.getTenant().setDevMode(false);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        when(jwtService.isConfigured()).thenReturn(true);
        doNothing().when(tokenService).assertConfiguredWhenRequired();
        validator = new ProductionHardeningStartupValidator(
                ci, tokenService, jwtService,
                new SingleTenantDeploymentGuard("SINGLE_TENANT_DEPLOYMENT", "00000000-0000-0000-0000-000000000001"),
                environment);
        ReflectionTestUtils.setField(validator, "demoEnabled", false);
        ReflectionTestUtils.setField(validator, "providerGapDefaultsEnabled", false);
        ReflectionTestUtils.setField(validator, "allowNonProductionDemoScoring", false);
        ReflectionTestUtils.setField(validator, "blockNonAuthoritativeDefaults", true);
        ReflectionTestUtils.setField(validator, "enforceProductionHardening", true);
        ReflectionTestUtils.setField(validator, "localDevPermitAll", false);
        ReflectionTestUtils.setField(validator, "allowHeaderImpersonation", false);
        ReflectionTestUtils.setField(validator, "jwtRequired", true);
        ReflectionTestUtils.setField(validator, "adminApiRequireRole", true);
    }

    @Test
    void passesWhenHardened() {
        validator.run(new DefaultApplicationArguments());
    }

    @Test
    void failsWhenDemoEnabled() {
        ReflectionTestUtils.setField(validator, "demoEnabled", true);
        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("demo.enabled");
    }

    @Test
    void failsWhenJwtNotConfigured() {
        when(jwtService.isConfigured()).thenReturn(false);
        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hmac-secret");
    }

    @Test
    void failsWhenHeaderImpersonationAllowed() {
        ReflectionTestUtils.setField(validator, "allowHeaderImpersonation", true);
        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("header-impersonation");
    }

    @Test
    void failsWhenTenancyNotSingleTenant() {
        validator = new ProductionHardeningStartupValidator(
                ci, tokenService, jwtService,
                new SingleTenantDeploymentGuard("OFF", "00000000-0000-0000-0000-000000000001"),
                environment);
        ReflectionTestUtils.setField(validator, "demoEnabled", false);
        ReflectionTestUtils.setField(validator, "providerGapDefaultsEnabled", false);
        ReflectionTestUtils.setField(validator, "allowNonProductionDemoScoring", false);
        ReflectionTestUtils.setField(validator, "blockNonAuthoritativeDefaults", true);
        ReflectionTestUtils.setField(validator, "enforceProductionHardening", true);
        ReflectionTestUtils.setField(validator, "localDevPermitAll", false);
        ReflectionTestUtils.setField(validator, "allowHeaderImpersonation", false);
        ReflectionTestUtils.setField(validator, "jwtRequired", true);
        ReflectionTestUtils.setField(validator, "adminApiRequireRole", true);
        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SINGLE_TENANT_DEPLOYMENT");
    }
}
