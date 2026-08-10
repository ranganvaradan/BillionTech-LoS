package com.los.core.creditintelligence.cutover.service;

import com.los.core.creditintelligence.cutover.domain.SourceReadiness;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Gate before any future canonical authority — never manufactures missing facts.
 */
@Service
public class CanonicalSourceReadinessGate {

    private static final Set<String> CRITICAL_SOURCES = Set.of("bureau", "bank", "gst", "itr");

    public record GateResult(SourceReadiness readiness, Map<String, Object> detail) {
    }

    public GateResult evaluate(Map<String, Object> sourcePresence) {
        Map<String, Object> detail = new LinkedHashMap<>();
        if (sourcePresence == null || sourcePresence.isEmpty()) {
            detail.put("reason", "no source presence map");
            return new GateResult(SourceReadiness.ERROR, detail);
        }

        int ready = 0;
        int refer = 0;
        int na = 0;
        int error = 0;
        for (String src : CRITICAL_SOURCES) {
            Object meta = sourcePresence.get(src);
            String status = statusOf(meta);
            detail.put(src, status);
            switch (status) {
                case "READY" -> ready++;
                case "REFER_FOR_DATA" -> refer++;
                case "NOT_APPLICABLE" -> na++;
                default -> error++;
            }
        }

        detail.put("readyCount", ready);
        detail.put("referCount", refer);
        detail.put("naCount", na);
        detail.put("errorCount", error);

        if (error > 0) {
            return new GateResult(SourceReadiness.ERROR, detail);
        }
        if (refer > 0) {
            return new GateResult(SourceReadiness.REFER_FOR_DATA, detail);
        }
        if (ready + na == CRITICAL_SOURCES.size() && ready > 0) {
            return new GateResult(SourceReadiness.READY, detail);
        }
        if (ready == 0 && na == CRITICAL_SOURCES.size()) {
            return new GateResult(SourceReadiness.NOT_APPLICABLE, detail);
        }
        return new GateResult(SourceReadiness.REFER_FOR_DATA, detail);
    }

    @SuppressWarnings("unchecked")
    private static String statusOf(Object meta) {
        if (meta == null) return "REFER_FOR_DATA";
        if (meta instanceof String s) return s;
        if (meta instanceof Boolean b) return b ? "READY" : "REFER_FOR_DATA";
        if (meta instanceof Map<?, ?> m) {
            Object present = m.get("present");
            Object fresh = m.get("fresh");
            Object subjectMatch = m.get("subjectMatch");
            Object parserOk = m.get("parserSuccessful");
            Object metricsOk = m.get("canonicalMetricsAvailable");
            if (Boolean.FALSE.equals(present)) return "REFER_FOR_DATA";
            if (Boolean.TRUE.equals(m.get("notApplicable"))) return "NOT_APPLICABLE";
            if (Boolean.FALSE.equals(parserOk)) return "ERROR";
            if (Boolean.FALSE.equals(fresh) || Boolean.FALSE.equals(subjectMatch)
                    || Boolean.FALSE.equals(metricsOk)) {
                return "REFER_FOR_DATA";
            }
            if (Boolean.TRUE.equals(present)) return "READY";
        }
        return "REFER_FOR_DATA";
    }
}
