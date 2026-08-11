package com.los.core.security;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SingleTenantDeploymentGuardTest {

    private static final String DEPLOY = "00000000-0000-0000-0000-000000000001";
    private static final String FOREIGN = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

    @Test
    void failClosed_rejectsForeignAndBindsBlank() {
        SingleTenantDeploymentGuard g = new SingleTenantDeploymentGuard("SINGLE_TENANT_DEPLOYMENT", DEPLOY);
        assertThat(g.isSingleTenantFailClosed()).isTrue();
        assertThat(g.bindOrReject(null)).isEqualTo(UUID.fromString(DEPLOY));
        assertThat(g.bindOrReject(UUID.fromString(DEPLOY))).isEqualTo(UUID.fromString(DEPLOY));
        assertThatThrownBy(() -> g.bindOrReject(UUID.fromString(FOREIGN)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> g.bindOrRejectHeader(FOREIGN))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> g.assertJwtTenantAllowed(FOREIGN))
                .isInstanceOf(ResponseStatusException.class);
        g.assertJwtTenantAllowed(DEPLOY);
    }

    @Test
    void offMode_allowsForeign() {
        SingleTenantDeploymentGuard g = new SingleTenantDeploymentGuard("OFF", DEPLOY);
        assertThat(g.isSingleTenantFailClosed()).isFalse();
        assertThat(g.bindOrReject(UUID.fromString(FOREIGN))).isEqualTo(UUID.fromString(FOREIGN));
    }
}
