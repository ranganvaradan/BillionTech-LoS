package com.los.core.config;

import com.los.core.security.AdminApiAccessFilter;
import com.los.core.security.LosJwtAuthFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * LOS-PRODUCTION-HARDENING-1 — JWT Bearer for APIs in production; staging may keep header tests.
 * AdminApiAccessFilter / StaffAccessGuard remain for role gates after identity is established.
 */
@Slf4j
@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

    /**
     * Prevent servlet-container double registration of {@link LosJwtAuthFilter}.
     * As a {@code @Component} {@link org.springframework.web.filter.OncePerRequestFilter},
     * Boot would run it before SecurityContextHolderFilter; the Security-chain pass would then
     * be skipped and JWT auth would be lost → 403 on authenticated routes.
     */
    @Bean
    public FilterRegistrationBean<LosJwtAuthFilter> disableLosJwtAuthFilterServletRegistration(
            LosJwtAuthFilter filter) {
        FilterRegistrationBean<LosJwtAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<AdminApiAccessFilter> disableAdminApiAccessFilterServletRegistration(
            AdminApiAccessFilter filter) {
        FilterRegistrationBean<AdminApiAccessFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @Order(0)
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            LosJwtAuthFilter losJwtAuthFilter,
            AdminApiAccessFilter adminApiAccessFilter,
            @Value("${los.security.local-dev-permit-all:false}") boolean localDevPermitAll,
            @Value("${los.security.jwt.required:false}") boolean jwtRequired
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(losJwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                // After JWT so X-User-Role overrides from Bearer are visible
                .addFilterAfter(adminApiAccessFilter, LosJwtAuthFilter.class);

        if (localDevPermitAll) {
            log.info("Security: local/staging open API (los.security.local-dev-permit-all=true) — JWT filter still overwrites headers when Bearer present");
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        } else if (jwtRequired) {
            log.info("Security: production JWT required for /api/** (except auth/webhooks/actuator/internal-token paths)");
            // PathPattern: ** may only appear at the end — never /api/v1/**/webhook/**
            http.authorizeHttpRequests(auth -> auth
                    .requestMatchers(
                            "/actuator/**",
                            "/api/v1/auth/**",
                            "/api/v1/internal/**",
                            "/api/internal/**",
                            "/internal/**",
                            "/api/v1/webhooks/**",
                            "/api/v1/*/webhook/**",
                            "/api/v1/*/webhooks/**",
                            "/api/v1/integrations/webhook",
                            "/api/v1/payu/**",
                            "/api/v1/esign/**",
                            "/api/v1/lms/callbacks/**"
                    ).permitAll()
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().permitAll()
            );
        } else {
            log.info("Security: jwt.required=false — permitAll with optional Bearer enrichment");
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }
        return http.build();
    }
}
