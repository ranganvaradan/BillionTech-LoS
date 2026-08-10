package com.los.core.creditintelligence.policy.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache validated immutable policy content by contentHash only.
 * Never caches mutable runtime values.
 */
@Component
public class CompiledPolicyCache {

    private final ConcurrentHashMap<String, Map<String, Object>> byHash = new ConcurrentHashMap<>();

    public Map<String, Object> getOrCompile(String contentHash, Map<String, Object> content) {
        if (contentHash == null || contentHash.isBlank()) {
            return content == null ? Map.of() : Map.copyOf(content);
        }
        return byHash.computeIfAbsent(contentHash, h -> content == null ? Map.of() : Map.copyOf(content));
    }

    public void invalidate(String contentHash) {
        if (contentHash != null) {
            byHash.remove(contentHash);
        }
    }

    public int size() {
        return byHash.size();
    }
}
