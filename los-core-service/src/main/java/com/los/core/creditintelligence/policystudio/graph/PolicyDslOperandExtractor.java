package com.los.core.creditintelligence.policystudio.graph;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * DP-3 — walk PolicyDsl AST and resolve operands to exact GACAT IDs only.
 * No fuzzy / approximate substitution.
 */
public final class PolicyDslOperandExtractor {

    private PolicyDslOperandExtractor() {}

    public record ExtractedOperand(
            String path,
            String originalToken,
            String refKind,
            String canonicalParameterId,
            String resolutionStatus
    ) {}

    public static List<ExtractedOperand> extract(Map<String, Object> expression, CanonicalParameterRegistry registry) {
        List<ExtractedOperand> out = new ArrayList<>();
        walk(expression, "$", registry, out);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void walk(Object node, String path, CanonicalParameterRegistry registry, List<ExtractedOperand> out) {
        if (node == null) return;
        if (node instanceof Map<?, ?> raw) {
            Map<String, Object> m = (Map<String, Object>) raw;
            String token = firstToken(m);
            if (token != null) {
                String kind = refKind(m);
                String status;
                String canonical = null;
                if (registry.findById(token).isPresent()) {
                    canonical = token;
                    status = CiPolicyRuleGraphOperand.RESOLVED;
                } else {
                    status = CiPolicyRuleGraphOperand.UNRESOLVED_CANONICAL_PARAMETER;
                }
                out.add(new ExtractedOperand(path, token, kind, canonical, status));
            }
            for (Map.Entry<String, Object> e : m.entrySet()) {
                String key = e.getKey();
                if (isRefKey(key)) continue;
                Object v = e.getValue();
                if (v instanceof Map || v instanceof List) {
                    walk(v, path + "." + key, registry, out);
                }
            }
        } else if (node instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                walk(list.get(i), path + "[" + i + "]", registry, out);
            }
        }
    }

    private static boolean isRefKey(String key) {
        if (key == null) return false;
        String k = key.toLowerCase(Locale.ROOT);
        return k.equals("metric") || k.equals("fact") || k.equals("policyparameter")
                || k.equals("applicationfield") || k.equals("reconciliation")
                || k.equals("metric_ref") || k.equals("fact_ref")
                || k.equals("policy_parameter_ref") || k.equals("application_field_ref");
    }

    private static String firstToken(Map<String, Object> m) {
        for (String k : List.of(
                "metric", "METRIC", "METRIC_REF",
                "fact", "FACT", "FACT_REF",
                "policyParameter", "POLICY_PARAMETER", "POLICY_PARAMETER_REF",
                "applicationField", "APPLICATION_FIELD", "APPLICATION_FIELD_REF",
                "reconciliation", "RECONCILIATION", "RECON_REF")) {
            Object v = m.get(k);
            if (v != null) {
                String s = String.valueOf(v).trim();
                if (!s.isEmpty()) return s;
            }
        }
        return null;
    }

    private static String refKind(Map<String, Object> m) {
        if (m.containsKey("metric") || m.containsKey("METRIC") || m.containsKey("METRIC_REF")) return "METRIC";
        if (m.containsKey("fact") || m.containsKey("FACT") || m.containsKey("FACT_REF")) return "FACT";
        if (m.containsKey("policyParameter") || m.containsKey("POLICY_PARAMETER") || m.containsKey("POLICY_PARAMETER_REF")) {
            return "POLICY_PARAMETER";
        }
        if (m.containsKey("applicationField") || m.containsKey("APPLICATION_FIELD") || m.containsKey("APPLICATION_FIELD_REF")) {
            return "APPLICATION_FIELD";
        }
        if (m.containsKey("reconciliation") || m.containsKey("RECONCILIATION") || m.containsKey("RECON_REF")) {
            return "RECONCILIATION";
        }
        return "UNKNOWN";
    }

    public static Map<String, Object> deepCopyAst(Map<String, Object> expression) {
        if (expression == null) return Map.of();
        return new LinkedHashMap<>(expression);
    }

    /**
     * Fallback when PolicyDsl AST has no metric/fact tokens — CM authoring stores exact GACAT IDs
     * on rule metadata ({@code parameterId}, {@code rightParameterId}, {@code mappedParameters}).
     * Exact IDs only; no fuzzy mapping.
     */
    public static List<ExtractedOperand> extractFromRuleMetadata(
            Map<String, Object> metadata, CanonicalParameterRegistry registry) {
        List<ExtractedOperand> out = new ArrayList<>();
        if (metadata == null || metadata.isEmpty() || registry == null) return out;
        LinkedHashMap<String, String> tokens = new LinkedHashMap<>();
        addMetaToken(tokens, metadata.get("parameterId"), "METADATA.parameterId");
        addMetaToken(tokens, metadata.get("rightParameterId"), "METADATA.rightParameterId");
        Object mapped = metadata.get("mappedParameters");
        if (mapped instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                addMetaToken(tokens, e.getValue(), "METADATA.mappedParameters." + e.getKey());
            }
        } else if (mapped instanceof List<?> list) {
            int i = 0;
            for (Object o : list) {
                addMetaToken(tokens, o, "METADATA.mappedParameters[" + (i++) + "]");
            }
        }
        for (Map.Entry<String, String> e : tokens.entrySet()) {
            String token = e.getKey();
            String path = e.getValue();
            if (registry.findById(token).isPresent()) {
                out.add(new ExtractedOperand(path, token, "METADATA", token, CiPolicyRuleGraphOperand.RESOLVED));
            } else {
                out.add(new ExtractedOperand(
                        path, token, "METADATA", null, CiPolicyRuleGraphOperand.UNRESOLVED_CANONICAL_PARAMETER));
            }
        }
        return out;
    }

    private static void addMetaToken(LinkedHashMap<String, String> tokens, Object raw, String path) {
        if (raw == null) return;
        String token = String.valueOf(raw).trim();
        if (token.isEmpty() || "null".equalsIgnoreCase(token)) return;
        if (!token.contains(".")) return;
        tokens.putIfAbsent(token, path);
    }
}
