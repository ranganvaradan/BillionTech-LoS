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
import java.util.Locale;

/**
 * LOS-LIVE-P0-CLOSURE-1 — fail startup when production hardening invariants are violated.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductionHardeningStartupValidator implements ApplicationRunner {

    private final CreditIntelligenceProperties ciProperties;
    private final CreditIntelligenceInternalTokenService tokenService;
    private final Environment environment;

    @Value("${los.demo.enabled:false}")
    private boolean demoEnabled;

    @Value("${los.underwriting.provider-gap-defaults-enabled:false}")
    private boolean providerGapDefaultsEnabled;

    @Value("${los.security.enforce-production-hardening:false}")
    private boolean enforceProductionHardening;

    @Override
    public void run(ApplicationArguments args) {
        boolean prodProfile = Arrays.stream(environment.getActiveProfiles())
                .map(p -> p.toLowerCase(Locale.ROOT))
                .anyMatch(p -> p.equals("prod") || p.equals("production"));
        if (!prodProfile && !enforceProductionHardening) {
            log.info("Production hardening startup checks skipped (profile not prod; enforce={})",
                    enforceProductionHardening);
            log.info("Effective provider-gap-defaults-enabled={}", providerGapDefaultsEnabled);
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
        if (ciProperties.getCutover() != null && ciProperties.getCutover().isAllowCanonicalAuthority()) {
            throw new IllegalStateException("allow-canonical-authority must remain false");
        }
        if (!ciProperties.isInternalTokenRequired()) {
            throw new IllegalStateException("credit-intelligence.internal-token-required must be true in production");
        }
        log.info("Production hardening startup checks passed (demo=false, staging-demo=false, gap-defaults=false, CI token required)");
    }
}
