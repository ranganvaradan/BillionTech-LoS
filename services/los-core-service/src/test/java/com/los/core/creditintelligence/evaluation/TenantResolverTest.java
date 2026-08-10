package com.los.core.creditintelligence.evaluation;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.core.tenant.TenantResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantResolverTest {

    private CreditIntelligenceProperties properties;
    private TenantResolver resolver;

    @BeforeEach
    void setUp() {
        properties = new CreditIntelligenceProperties();
        resolver = new TenantResolver(properties);
    }

    @Test
    void devModeAllowsDefaultWhenTenantNull() {
        properties.getTenant().setDevMode(true);
        properties.getTenant().setRequireExplicit(false);
        properties.getEvaluationContext().setEnabled(false);

        UUID resolved = resolver.resolve(null);

        assertThat(resolved).isEqualTo(properties.getDefaultTenantId());
    }

    @Test
    void requireExplicitThrowsWhenTenantNull() {
        properties.getTenant().setDevMode(true);
        properties.getTenant().setRequireExplicit(true);

        assertThatThrownBy(() -> resolver.resolve(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tenant id is required");
    }

    @Test
    void evaluationContextEnabledAndNotDevModeThrowsWhenTenantNull() {
        properties.getTenant().setDevMode(false);
        properties.getTenant().setRequireExplicit(false);
        properties.getEvaluationContext().setEnabled(true);

        assertThatThrownBy(() -> resolver.resolve(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tenant id is required");
    }

    @Test
    void explicitTenantAlwaysWins() {
        properties.getTenant().setRequireExplicit(true);
        properties.getEvaluationContext().setEnabled(true);
        properties.getTenant().setDevMode(false);
        UUID explicit = UUID.randomUUID();

        assertThat(resolver.resolve(explicit)).isEqualTo(explicit);
    }
}
