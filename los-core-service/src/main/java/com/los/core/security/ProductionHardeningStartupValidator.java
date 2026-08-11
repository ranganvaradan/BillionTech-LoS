package com.los.core.security;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * LOS-PRODUCTION-HARDENING-1 — fail startup when production hardening invariants are violated.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductionHardeningStartupValidator implements ApplicationRunner {

    private final CreditIntelligenceProperties ciProperties;
    private final CreditIntelligenceInternalTokenService tokenService;
    private final LosJwtService jwtService;
    private final SingleTenantDeploymentGuard singleTenantDeploymentGuard;
    private final Environment environment;

    @Value("${los.demo.enabled:false}")
    private boolean demoEnabled;

    @Value("${los.underwriting.provider-gap-defaults-enabled:false}")
    private boolean providerGapDefaultsEnabled;

    @Value("${los.underwriting.scorecard.allow-non-production-demo-scoring:false}")
    private boolean allowNonProductionDemoScoring;

    @Value("${los.underwriting.scorecard.block-non-authoritative-defaults:true}")
    private boolean blockNonAuthoritativeDefaults;

    @Value("${los.security.enforce-production-hardening:false}")
    private boolean enforceProductionHardening;

    @Value("${los.security.local-dev-permit-all:false}")
    private boolean localDevPermitAll;

    @Value("${los.security.allow-header-impersonation:true}")
    private boolean allowHeaderImpersonation;

    @Value("${los.security.jwt.required:false}")
    private boolean jwtRequired;

    @Value("${los.security.admin-api-require-role:true}")
    private boolean adminApiRequireRole;

    @Override
    public void run(ApplicationArguments args) {
        Map<String, Object> effective = effectiveFlags();
        log.info("Security/demo effective flags: {}", effective);

        boolean prodProfile = Arrays.stream(environment.getActiveProfiles())
                .map(p -> p.toLowerCase(Locale.ROOT))
                .anyMatch(p -> p.equals("prod") || p.equals("production"));
        if (!prodProfile && !enforceProductionHardening) {
            log.info("Production hardening startup checks skipped (profile not prod; enforce={})",
                    enforceProductionHardening);
            return;
        }

        tokenService.assertConfiguredWhenRequired();

        if (demoEnabled) {
            throw new IllegalStateException("los.demo.enabled must be false in production");
        }
        if (ciProperties.getStagingDemo() != null && ciProperties.getStagingDemo().isEnabled()) {
            throw new IllegalStateException("credit-intelligence.staging-demo.enabled must be false in production");
        }
        if (ciProperties.getValidation() != null && ciProperties.getValidation().isEnabled()) {
            throw new IllegalStateException("credit-intelligence.validation.enabled must be false in production");
        }
        if (providerGapDefaultsEnabled) {
            throw new IllegalStateException("los.underwriting.provider-gap-defaults-enabled must be false in production");
        }
        if (allowNonProductionDemoScoring) {
            throw new IllegalStateException(
                    "los.underwriting.scorecard.allow-non-production-demo-scoring must be false in production");
        }
        if (!blockNonAuthoritativeDefaults) {
            throw new IllegalStateException(
                    "los.underwriting.scorecard.block-non-authoritative-defaults must be true in production");
        }
        if (ciProperties.getCutover() != null && ciProperties.getCutover().isAllowCanonicalAuthority()) {
            throw new IllegalStateException("allow-canonical-authority must remain false");
        }
        if (!ciProperties.isInternalTokenRequired()) {
            throw new IllegalStateException("credit-intelligence.internal-token-required must be true in production");
        }
        if (localDevPermitAll) {
            throw new IllegalStateException("los.security.local-dev-permit-all must be false in production");
        }
        if (allowHeaderImpersonation) {
            throw new IllegalStateException(
                    "los.security.allow-header-impersonation must be false in production (use JWT)");
        }
        if (!jwtRequired) {
            throw new IllegalStateException("los.security.jwt.required must be true in production");
        }
        if (!jwtService.isConfigured()) {
            throw new IllegalStateException(
                    "los.security.jwt.hmac-secret must be configured (>=32 chars) in production");
        }
        if (!adminApiRequireRole) {
            throw new IllegalStateException("los.security.admin-api-require-role must be true in production");
        }
        if (!singleTenantDeploymentGuard.isSingleTenantFailClosed()) {
            throw new IllegalStateException(
                    "los.security.tenancy.mode must be SINGLE_TENANT_DEPLOYMENT in production "
                            + "(LOS core is not true multi-tenant)");
        }
        if (ciProperties.getTenant() != null && ciProperties.getTenant().isDevMode()) {
            throw new IllegalStateException(
                    "credit-intelligence.tenant.dev-mode must be false in production");
        }
        log.info("Production hardening startup checks passed "
                + "(demo=false, gap=false, demo-scoring=false, header-impersonation=false, jwt.required=true, "
                + "CI token required, single-tenant fail-closed)");
    }

    public Map<String, Object> effectiveFlags() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("demoEnabled", demoEnabled);
        m.put("stagingDemoEnabled", ciProperties.getStagingDemo() != null && ciProperties.getStagingDemo().isEnabled());
        m.put("validationEnabled", ciProperties.getValidation() != null && ciProperties.getValidation().isEnabled());
        m.put("providerGapDefaultsEnabled", providerGapDefaultsEnabled);
        m.put("allowNonProductionDemoScoring", allowNonProductionDemoScoring);
        m.put("blockNonAuthoritativeDefaults", blockNonAuthoritativeDefaults);
        m.put("allowCanonicalAuthority",
                ciProperties.getCutover() != null && ciProperties.getCutover().isAllowCanonicalAuthority());
        m.put("internalTokenRequired", ciProperties.isInternalTokenRequired());
        m.put("localDevPermitAll", localDevPermitAll);
        m.put("allowHeaderImpersonation", allowHeaderImpersonation);
        m.put("jwtRequired", jwtRequired);
        m.put("jwtConfigured", jwtService.isConfigured());
        m.put("adminApiRequireRole", adminApiRequireRole);
        m.put("tenancyMode", singleTenantDeploymentGuard.mode());
        m.put("singleTenantFailClosed", singleTenantDeploymentGuard.isSingleTenantFailClosed());
        m.put("deploymentTenantId", singleTenantDeploymentGuard.deploymentTenantId().toString());
        m.put("ciTenantDevMode", ciProperties.getTenant() != null && ciProperties.getTenant().isDevMode());
        return m;
    }
}
