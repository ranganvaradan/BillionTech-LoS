package com.los.core.creditintelligence.policystudio.service;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyMetricCandidate;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.support.ContentHasher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds metrics → rules → exceptions dependency graph; detects circular/missing refs.
 */
@Component
public class PolicyDependencyGraphBuilder {

    private final ContentHasher hasher = new ContentHasher();

    public Map<String, Object> build(List<CiPolicyMetricCandidate> metrics, List<CiPolicyRuleCandidate> rules) {
        Map<String, Object> graph = new LinkedHashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> circular = new ArrayList<>();

        Set<String> metricCodes = new HashSet<>();
        for (CiPolicyMetricCandidate m : metrics) {
            String code = m.getSystemMetricId() != null ? m.getSystemMetricId() : m.getCandidateCanonicalCode();
            if (code != null) {
                metricCodes.add(code);
                nodes.add(Map.of("id", code, "type", "METRIC"));
            }
            if (m.getDependencies() != null) {
                for (Object dep : m.getDependencies()) {
                    String d = String.valueOf(dep);
                    edges.add(Map.of("from", code, "to", d, "kind", "METRIC_DEP"));
                    if (!metricCodes.contains(d) && metrics.stream().noneMatch(x ->
                            d.equals(x.getSystemMetricId()) || d.equals(x.getCandidateCanonicalCode()))) {
                        missing.add(d);
                    }
                }
            }
        }

        Map<String, List<String>> adj = new HashMap<>();
        for (CiPolicyRuleCandidate r : rules) {
            nodes.add(Map.of("id", r.getSystemRuleId(), "type",
                    r.getSystemRuleId() != null && r.getSystemRuleId().contains("EXCEPTION") ? "EXCEPTION" : "RULE"));
            Set<String> refs = extractRefs(r.getExpression());
            for (String ref : refs) {
                edges.add(Map.of("from", r.getSystemRuleId(), "to", ref, "kind", "RULE_USES"));
                adj.computeIfAbsent(r.getSystemRuleId(), k -> new ArrayList<>()).add(ref);
                if (ref.startsWith("banking.") || ref.startsWith("bureau.") || ref.startsWith("BANK_")) {
                    if (!metricCodes.contains(ref) && metrics.stream().noneMatch(x ->
                            ref.equals(x.getSystemMetricId()) || ref.equals(x.getCandidateCanonicalCode()))) {
                        // referenced metric may be registry-backed — not necessarily missing candidate
                    }
                }
            }
        }

        // simple cycle detection on rule→rule edges via shared expression refs named as rules
        Set<String> ruleIds = new HashSet<>();
        rules.forEach(r -> ruleIds.add(r.getSystemRuleId()));
        for (CiPolicyRuleCandidate r : rules) {
            Set<String> seen = new HashSet<>();
            if (dfsCycle(r.getSystemRuleId(), adj, ruleIds, seen, new HashSet<>())) {
                circular.add(r.getSystemRuleId());
            }
        }

        graph.put("nodes", nodes);
        graph.put("edges", edges);
        graph.put("missing", missing.stream().distinct().toList());
        graph.put("circular", circular.stream().distinct().toList());
        graph.put("hash", hasher.hashMap(Map.of("nodes", nodes, "edges", edges)));
        return graph;
    }

    private boolean dfsCycle(String node, Map<String, List<String>> adj, Set<String> ruleIds,
                             Set<String> path, Set<String> visited) {
        if (!ruleIds.contains(node)) {
            return false;
        }
        if (path.contains(node)) {
            return true;
        }
        if (!visited.add(node)) {
            return false;
        }
        path.add(node);
        for (String next : adj.getOrDefault(node, List.of())) {
            if (ruleIds.contains(next) && dfsCycle(next, adj, ruleIds, path, visited)) {
                return true;
            }
        }
        path.remove(node);
        return false;
    }

    @SuppressWarnings("unchecked")
    private Set<String> extractRefs(Object node) {
        Set<String> refs = new HashSet<>();
        if (!(node instanceof Map<?, ?> raw)) {
            return refs;
        }
        Map<String, Object> m = (Map<String, Object>) raw;
        for (String key : List.of("metric", "METRIC_REF", "fact", "FACT_REF",
                "policyParameter", "POLICY_PARAMETER_REF", "applicationField", "APPLICATION_FIELD_REF")) {
            if (m.get(key) != null) {
                refs.add(String.valueOf(m.get(key)));
            }
        }
        for (Object child : m.values()) {
            if (child instanceof Map<?, ?> || child instanceof List<?>) {
                if (child instanceof List<?> list) {
                    for (Object c : list) {
                        refs.addAll(extractRefs(c));
                    }
                } else {
                    refs.addAll(extractRefs(child));
                }
            }
        }
        return refs;
    }
}
