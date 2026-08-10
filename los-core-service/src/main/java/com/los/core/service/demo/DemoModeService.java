package com.los.core.service.demo;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Whether demo / reset features are available: {@code local} profile or {@code los.demo.enabled=true}.
 */
@Service
@RequiredArgsConstructor
public class DemoModeService {

    public static final String DEMO_MODE_DISABLED_MESSAGE =
            "Demo mode is not enabled. Set spring.profiles.active=local or los.demo.enabled=true";

    private final Environment environment;

    public boolean isDemoModeEnabled() {
        if (Boolean.TRUE.equals(environment.getProperty("los.demo.enabled", Boolean.class, false))) {
            return true;
        }
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> StringUtils.hasText(p) && "local".equalsIgnoreCase(p));
    }

    /**
     * Comma-separated active profile names, or {@code (none)} if empty.
     */
    public String getActiveProfilesDisplay() {
        String[] p = environment.getActiveProfiles();
        if (p.length == 0) {
            return "(none)";
        }
        return Arrays.stream(p).collect(Collectors.joining(", "));
    }
}
