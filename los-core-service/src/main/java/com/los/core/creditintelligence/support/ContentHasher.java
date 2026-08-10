package com.los.core.creditintelligence.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Deterministic SHA-256 hashing of JSON payloads (sorted map keys, compact form).
 */
public final class ContentHasher {

    private final ObjectMapper mapper;

    public ContentHasher() {
        this.mapper = new ObjectMapper()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    public ContentHasher(ObjectMapper objectMapper) {
        this.mapper = objectMapper.copy()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    /**
     * Hash a list of facts by canonical path, value, and classification.
     * Facts are sorted by path before hashing for stability.
     */
    public String hashFacts(List<FactHashInput> facts) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        List<FactHashInput> sorted = new ArrayList<>(facts == null ? List.of() : facts);
        sorted.sort((a, b) -> {
            String pa = a == null || a.path() == null ? "" : a.path();
            String pb = b == null || b.path() == null ? "" : b.path();
            return pa.compareTo(pb);
        });
        for (FactHashInput fact : sorted) {
            if (fact == null) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("canonicalPath", fact.path());
            entry.put("classification", fact.classification());
            entry.put("value", deepSort(fact.value()));
            normalized.add(entry);
        }
        return sha256Hex(toCompactJson(normalized));
    }

    public String hashMap(Map<?, ?> map) {
        Object sorted = deepSort(map == null ? Map.of() : map);
        return sha256Hex(toCompactJson(sorted));
    }

    private String toCompactJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize content for hashing", e);
        }
    }

    private static Object deepSort(Object value) {
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String key = e.getKey() == null ? "null" : String.valueOf(e.getKey());
                sorted.put(key, deepSort(e.getValue()));
            }
            return sorted;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(deepSort(item));
            }
            return out;
        }
        return value;
    }

    private static String sha256Hex(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record FactHashInput(String path, Object value, String classification) {
    }
}
