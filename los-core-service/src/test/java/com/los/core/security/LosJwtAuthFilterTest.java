package com.los.core.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class LosJwtAuthFilterTest {

    @Test
    void prodMode_rejectsHeaderImpersonationWithoutBearer() throws Exception {
        LosJwtService jwt = new LosJwtService(
                new ObjectMapper(),
                "test-hmac-secret-at-least-32-characters-long",
                3600);
        SingleTenantDeploymentGuard tenancy = new SingleTenantDeploymentGuard(
                "SINGLE_TENANT_DEPLOYMENT", "00000000-0000-0000-0000-000000000001");
        LosJwtAuthFilter filter = new LosJwtAuthFilter(jwt, new ObjectMapper(), tenancy, true, false);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/applications");
        req.addHeader("X-User-Role", "ADMINISTRATOR");
        req.addHeader("X-User-Id", UUID.randomUUID().toString());
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(req, res, chain);
        assertThat(res.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void singleTenantMode_rejectsForeignJwtTenant() throws Exception {
        LosJwtService jwt = new LosJwtService(
                new ObjectMapper(),
                "test-hmac-secret-at-least-32-characters-long",
                3600);
        String token = jwt.issueAccessToken(
                UUID.randomUUID(), "CREDIT_MANAGER", "a@b.com", "A",
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
        SingleTenantDeploymentGuard tenancy = new SingleTenantDeploymentGuard(
                "SINGLE_TENANT_DEPLOYMENT", "00000000-0000-0000-0000-000000000001");
        LosJwtAuthFilter filter = new LosJwtAuthFilter(jwt, new ObjectMapper(), tenancy, true, false);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/applications");
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(req, res, chain);
        assertThat(res.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void stagingMode_allowsHeaderImpersonation() throws Exception {
        LosJwtService jwt = new LosJwtService(
                new ObjectMapper(),
                "test-hmac-secret-at-least-32-characters-long",
                3600);
        SingleTenantDeploymentGuard tenancy = new SingleTenantDeploymentGuard("OFF", "00000000-0000-0000-0000-000000000001");
        LosJwtAuthFilter filter = new LosJwtAuthFilter(jwt, new ObjectMapper(), tenancy, false, true);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/applications");
        req.addHeader("X-User-Role", "CREDIT_MANAGER");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(req, res, chain);
        verify(chain).doFilter(any(), any());
        assertThat(res.getStatus()).isEqualTo(200);
    }
}
