package com.los.core.creditintelligence.evaluation;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.core.tenant.TenantResolver;
import com.los.core.security.SingleTenantDeploymentGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantResolverTest {

    private CreditIntelligenceProperties properties;
    private TenantResolver resolver;

    @BeforeEach
    void setUp() {
        properties = new CreditIntelligenceProperties();
        resolver = new TenantResolver(
                properties,
                new SingleTenantDeploymentGuard("OFF", "00000000-0000-0000-0000-000000000001"));
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
    void explicitTenantAlwaysWinsWhenNotSingleTenantMode() {
        properties.getTenant().setRequireExplicit(true);
        properties.getEvaluationContext().setEnabled(true);
        properties.getTenant().setDevMode(false);
        UUID explicit = UUID.randomUUID();

        assertThat(resolver.resolve(explicit)).isEqualTo(explicit);
    }

    @Test
    void singleTenantModeRejectsForeignTenant() {
        TenantResolver single = new TenantResolver(
                properties,
                new SingleTenantDeploymentGuard(
                        "SINGLE_TENANT_DEPLOYMENT", "00000000-0000-0000-0000-000000000001"));
        UUID foreign = UUID.fromString("11111111-1111-1111-1111-111111111111");
        assertThatThrownBy(() -> single.resolve(foreign))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("TENANT_FORBIDDEN");
        assertThat(single.resolve(null))
                .isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        assertThatThrownBy(() -> single.resolveFromHeader("11111111-1111-1111-1111-111111111111"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("TENANT_FORBIDDEN");
        assertThat(single.resolveFromHeader(null))
                .isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    }
}
