package com.los.core.creditintelligence.cutover.pilot;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Legacy default invocation frequency from dataset only (§20). Never fabricates.
 */
@Service
public class LegacyDefaultFrequencyMeasurer {

    public Map<String, Object> measure(List<Map<String, Object>> applicationDefaultInvocations) {
        List<Map<String, Object>> apps = applicationDefaultInvocations == null
                ? List.of() : applicationDefaultInvocations;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sampleSize", apps.size());
        out.put("fabricated", false);
        out.put("dataOrigin", "COMPUTED_FROM_PROVIDED_DATASET_ONLY");
        if (apps.isEmpty()) {
            out.put("frequencies", Map.of());
            out.put("note", "Empty dataset — frequencies not computable");
            return out;
        }

        Map<String, Long> invoked = new LinkedHashMap<>();
        for (Map<String, Object> app : apps) {
            Object keys = app.get("legacyDefaultsInvoked");
            if (keys instanceof List<?> list) {
                for (Object k : list) {
                    invoked.merge(String.valueOf(k), 1L, Long::sum);
                }
            }
        }
        Map<String, BigDecimal> frequencies = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : invoked.entrySet()) {
            frequencies.put(e.getKey(),
                    BigDecimal.valueOf(e.getValue() * 100.0 / apps.size())
                            .setScale(2, RoundingMode.HALF_UP));
        }
        out.put("frequencies", frequencies);
        out.put("invocationCounts", invoked);
        return out;
    }
}
