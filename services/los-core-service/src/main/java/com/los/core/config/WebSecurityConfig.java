package com.los.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

import lombok.extern.slf4j.Slf4j;

/**
 * When {@code los.security.local-dev-permit-all=true} (typically with {@code spring.profiles.active=local}),
 * the API is open without authentication and CSRF is disabled for local frontend development.
 * <p>
 * When {@code false} (default in {@code application.yml}), all requests are still permitted so behavior matches
 * the pre-security state of this service; tighten to {@code authenticated()} for production once IAM is integrated.
 */
@Slf4j
@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

    @Bean
    @Order(0)
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Value("${los.security.local-dev-permit-all:false}") boolean localDevPermitAll
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);

        if (localDevPermitAll) {
            if (log.isInfoEnabled()) {
                log.info("Security: local dev — /api/** permitAll, CSRF and auth disabled (los.security.local-dev-permit-all=true)");
            }
            http.authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/**").permitAll()
                    .anyRequest().permitAll()
            );
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Security: local-dev open API disabled; all requests still permitted until IAM (los.security.local-dev-permit-all=false)");
            }
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }
        return http.build();
    }
}
