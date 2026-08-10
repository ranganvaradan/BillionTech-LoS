package com.los.core.creditintelligence.evaluation;

import com.los.core.creditintelligence.support.ContentHasher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Hashes evaluation outcomes after stripping non-deterministic temporal / run keys.
 */
@Component
public class DeterministicEvaluationHasher {

    private static final Set<String> STRIP_KEYS = Set.of(
            "startedAt",
            "completedAt",
            "durationMs",
            "duration",
            "runId",
            "runIds",
            "createdAt",
            "timestamps");

    private final ContentHasher contentHasher;

    public DeterministicEvaluationHasher() {
        this.contentHasher = new ContentHasher();
    }

    public DeterministicEvaluationHasher(ContentHasher contentHasher) {
        this.contentHasher = contentHasher != null ? contentHasher : new ContentHasher();
    }

    @SuppressWarnings("unchecked")
    public String hashOutcomes(Map<String, Object> outcomes) {
        Object cleaned = stripAndSort(outcomes == null ? Map.of() : outcomes);
        if (cleaned instanceof Map<?, ?> map) {
            return contentHasher.hashMap((Map<?, ?>) map);
        }
        Map<String, Object> wrap = new LinkedHashMap<>();
        wrap.put("value", cleaned);
        return contentHasher.hashMap(wrap);
    }

    private static Object stripAndSort(Object value) {
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String key = e.getKey() == null ? "null" : String.valueOf(e.getKey());
                if (STRIP_KEYS.contains(key)) {
                    continue;
                }
                sorted.put(key, stripAndSort(e.getValue()));
            }
            return sorted;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(stripAndSort(item));
            }
            return out;
        }
        return value;
    }
}
