package com.los.core.creditintelligence.decisionpolicy.kyc;

import com.los.core.model.enums.KycStepType;

import java.util.Optional;

/**
 * Optional overlay for NBFC AggregatorConfig routing.
 * When absent / not live, design readiness uses repository provider evidence only —
 * distinct from temporary runtime provider outages.
 */
public interface KycIntegrationRoutingProbe {

    record Routing(
            boolean primaryConfigured,
            boolean fallbackConfigured,
            String primaryName,
            String fallbackName
    ) {}

    /** False when probe is design-time only (no AggregatorConfig inspection). */
    boolean isLiveConfigurationProbe();

    Optional<Routing> routingFor(KycStepType step);

    /** Default: no live routing inspection. */
    static KycIntegrationRoutingProbe designTimeOnly() {
        return new KycIntegrationRoutingProbe() {
            @Override
            public boolean isLiveConfigurationProbe() {
                return false;
            }

            @Override
            public Optional<Routing> routingFor(KycStepType step) {
                return Optional.empty();
            }
        };
    }
}
