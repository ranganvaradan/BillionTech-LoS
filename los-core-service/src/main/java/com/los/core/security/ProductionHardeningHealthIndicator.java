package com.los.core.security;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;

/**
 * Exposes effective production-hardening flags on actuator health (no secret values).
 * Staging may run with demo/interim flags — only mark DOWN under prod profile.
 */
@Component("productionHardening")
@RequiredArgsConstructor
public class ProductionHardeningHealthIndicator implements HealthIndicator {

    private final ProductionHardeningStartupValidator validator;
    private final Environment environment;

    @Override
    public Health health() {
        var flags = validator.effectiveFlags();
        boolean prodProfile = Arrays.stream(environment.getActiveProfiles())
                .map(p -> p.toLowerCase(Locale.ROOT))
                .anyMatch(p -> p.equals("prod") || p.equals("production"));
        boolean unsafeAuthority = Boolean.TRUE.equals(flags.get("allowCanonicalAuthority"));
        boolean demo = Boolean.TRUE.equals(flags.get("demoEnabled"))
                || Boolean.TRUE.equals(flags.get("stagingDemoEnabled"))
                || Boolean.TRUE.equals(flags.get("providerGapDefaultsEnabled"))
                || Boolean.TRUE.equals(flags.get("allowNonProductionDemoScoring"));
        boolean headerImpersonation = Boolean.TRUE.equals(flags.get("allowHeaderImpersonation"));
        boolean jwtMissing = !Boolean.TRUE.equals(flags.get("jwtRequired"))
                || !Boolean.TRUE.equals(flags.get("jwtConfigured"));
        if (prodProfile && (demo || unsafeAuthority || headerImpersonation || jwtMissing)) {
            return Health.down().withDetails(flags).build();
        }
        return Health.up().withDetails(flags).build();
    }
}
