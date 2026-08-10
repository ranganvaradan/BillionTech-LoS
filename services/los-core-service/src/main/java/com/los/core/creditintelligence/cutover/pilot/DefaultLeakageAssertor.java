package com.los.core.creditintelligence.cutover.pilot;

import com.los.core.creditintelligence.cutover.domain.CiCutoverOperationalEvent;
import com.los.core.creditintelligence.cutover.domain.DefaultClassification;
import com.los.core.creditintelligence.cutover.store.CutoverStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * If canonical evaluation consumed an unsafe legacy default → CUTOVER_CERTIFICATION_FAILURE (§19).
 */
@Service
public class DefaultLeakageAssertor {

    public static final String FAILURE_CODE = "CUTOVER_CERTIFICATION_FAILURE";

    public record LeakageResult(
            boolean leaked,
            List<String> leakedKeys,
            String code
    ) {
    }

    private final CutoverStore store;

    public DefaultLeakageAssertor(CutoverStore store) {
        this.store = store;
    }

    /**
     * @param consumedLegacyDefaults keys the canonical path used
     * @param classifications key → classification
     */
    public LeakageResult assertNoUnsafeLeak(
            UUID cohortId,
            List<String> consumedLegacyDefaults,
            Map<String, String> classifications) {
        List<String> consumed = consumedLegacyDefaults == null ? List.of() : consumedLegacyDefaults;
        Map<String, String> cls = classifications == null ? Map.of() : classifications;
        List<String> leaked = consumed.stream()
                .filter(k -> {
                    String c = cls.getOrDefault(k, DefaultClassification.UNSAFE_SILENT_DEFAULT.name());
                    return DefaultClassification.UNSAFE_SILENT_DEFAULT.name().equals(c)
                            || DefaultClassification.DEMO_ONLY.name().equals(c);
                })
                .toList();

        if (!leaked.isEmpty()) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("code", FAILURE_CODE);
            detail.put("leakedKeys", leaked);
            detail.put("sanitized", true);
            store.saveOperationalEvent(CiCutoverOperationalEvent.builder()
                    .cohortId(cohortId)
                    .eventType(FAILURE_CODE)
                    .detail(detail)
                    .createdBy("default-leakage-assertor")
                    .createdAt(Instant.now())
                    .build());
            return new LeakageResult(true, leaked, FAILURE_CODE);
        }
        return new LeakageResult(false, List.of(), null);
    }

    public LeakageResult assertFromTrace(UUID cohortId, Map<String, Object> decisionTrace) {
        if (decisionTrace == null) {
            return new LeakageResult(false, List.of(), null);
        }
        Object used = decisionTrace.get("canonicalConsumedLegacyDefaults");
        List<String> keys = List.of();
        if (used instanceof List<?> list) {
            keys = list.stream().map(String::valueOf).toList();
        }
        @SuppressWarnings("unchecked")
        Map<String, String> classifications = decisionTrace.get("legacyDefaultClassifications") instanceof Map<?, ?> m
                ? (Map<String, String>) m
                : Map.of();
        return assertNoUnsafeLeak(cohortId, keys, classifications);
    }
}
