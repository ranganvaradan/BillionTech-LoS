package com.los.core.creditintelligence.cutover.pilot;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Provider failure categories for Equifax/SurePass/Karza/Setu (§17).
 */
@Service
public class ProviderFailureAnalyzer {

    public enum FailureCategory {
        PROVIDER_MISSING,
        SCHEMA_MISMATCH,
        PARSER_ERROR,
        NORMALIZATION_ERROR,
        STALE_SOURCE,
        SUBJECT_MISMATCH,
        PARTIAL_DATA
    }

    public record ProviderFailure(
            String provider,
            FailureCategory category,
            String detail
    ) {
    }

    public Map<String, Object> analyze(List<ProviderFailure> failures) {
        List<ProviderFailure> list = failures == null ? List.of() : failures;
        Map<String, Long> byProvider = new LinkedHashMap<>();
        Map<String, Long> byCategory = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ProviderFailure f : list) {
            String provider = normalizeProvider(f.provider());
            byProvider.merge(provider, 1L, Long::sum);
            byCategory.merge(f.category().name(), 1L, Long::sum);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("provider", provider);
            row.put("category", f.category().name());
            row.put("detail", f.detail());
            rows.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("byProvider", byProvider);
        out.put("byCategory", byCategory);
        out.put("failures", rows);
        out.put("total", list.size());
        return out;
    }

    static String normalizeProvider(String raw) {
        if (raw == null || raw.isBlank()) return "other";
        String u = raw.toUpperCase(Locale.ROOT);
        if (u.contains("EQUIFAX")) return "Equifax";
        if (u.contains("SUREPASS")) return "SurePass";
        if (u.contains("KARZA")) return "Karza";
        if (u.contains("SETU")) return "Setu";
        return "other";
    }
}
