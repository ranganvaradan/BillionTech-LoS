package com.los.core.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AdminApiAccessFilterTest {

    @Test
    void deniesAdminPathWithoutRole() throws Exception {
        AdminApiAccessFilter filter = new AdminApiAccessFilter(new ObjectMapper(), true);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/admin/live-readiness/data-parameters");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(req, res, chain);
        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(res.getContentAsString()).contains("ADMIN_ROLE_REQUIRED");
        verify(chain, never()).doFilter(req, res);
    }

    @Test
    void allowsAdminPathWithAdministratorRole() throws Exception {
        AdminApiAccessFilter filter = new AdminApiAccessFilter(new ObjectMapper(), true);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/admin/live-readiness/data-parameters");
        req.addHeader("X-User-Role", "ADMINISTRATOR");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(req, res, chain);
        verify(chain).doFilter(req, res);
    }

    @Test
    void skipsWhenRequireRoleDisabled() throws Exception {
        AdminApiAccessFilter filter = new AdminApiAccessFilter(new ObjectMapper(), false);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/admin/live-readiness/data-parameters");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(req, res, chain);
        verify(chain).doFilter(req, res);
    }
}
