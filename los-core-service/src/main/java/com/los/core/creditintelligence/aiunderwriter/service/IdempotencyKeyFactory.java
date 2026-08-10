package com.los.core.creditintelligence.aiunderwriter.service;

import com.los.core.creditintelligence.support.ContentHasher;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class IdempotencyKeyFactory {

    private final ContentHasher hasher;

    public IdempotencyKeyFactory() {
        this(new ContentHasher());
    }

    public IdempotencyKeyFactory(ContentHasher hasher) {
        this.hasher = hasher != null ? hasher : new ContentHasher();
    }

    public String build(
            UUID applicationId,
            UUID evaluationContextId,
            UUID recommendationId,
            Map<String, Object> promptVersions,
            List<String> requestedOutputTypes) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("applicationId", applicationId == null ? "" : applicationId.toString());
        payload.put("evaluationContextId", evaluationContextId == null ? "" : evaluationContextId.toString());
        payload.put("recommendationId", recommendationId == null ? "" : recommendationId.toString());
        payload.put("promptVersions", promptVersions == null ? Map.of() : promptVersions);
        List<String> types = requestedOutputTypes == null ? List.of() : requestedOutputTypes.stream()
                .sorted()
                .collect(Collectors.toList());
        payload.put("requestedOutputTypes", types);
        return hasher.hashMap(payload);
    }
}
