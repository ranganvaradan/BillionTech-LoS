package com.los.core.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Overwrites spoofable X-User-* headers with JWT-derived identity for downstream guards.
 */
final class LosAuthenticatedRequest extends HttpServletRequestWrapper {

    private final Map<String, String> overrides;

    LosAuthenticatedRequest(HttpServletRequest request, Map<String, String> overrides) {
        super(request);
        this.overrides = overrides;
    }

    @Override
    public String getHeader(String name) {
        if (name == null) return null;
        String key = name.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> e : overrides.entrySet()) {
            if (e.getKey().equalsIgnoreCase(name) || e.getKey().toLowerCase(Locale.ROOT).equals(key)) {
                return e.getValue();
            }
        }
        return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        String v = getHeader(name);
        if (v != null && overrides.keySet().stream().anyMatch(k -> k.equalsIgnoreCase(name))) {
            return Collections.enumeration(Collections.singletonList(v));
        }
        return super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        Set<String> names = new LinkedHashSet<>();
        Enumeration<String> existing = super.getHeaderNames();
        while (existing != null && existing.hasMoreElements()) {
            names.add(existing.nextElement());
        }
        names.addAll(overrides.keySet());
        return Collections.enumeration(names);
    }
}
