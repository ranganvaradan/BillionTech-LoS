package com.los.core.requirement;

import com.los.core.creditintelligence.policystudio.graph.CiPolicyRuleGraphOperand;
import com.los.core.creditintelligence.policystudio.graph.PolicyRuleGraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Builds the Policy Requirement Inventory from the DP-3 persisted Policy Rule Graph.
 * Scorecard may annotate usage but must not introduce parameters outside Policy.
 */
@Component
@RequiredArgsConstructor
public class PolicyRequirementInventoryBuilder {

    private final PolicyRuleGraphService policyRuleGraphService;

    @SuppressWarnings("unchecked")
    public Map<String, Object> buildRaw(UUID policyDocumentId, Map<String, Object> inventoryHints) {
        Map<String, Object> hints = inventoryHints != null ? inventoryHints : Map.of();
        if (hints.get("policyInventory") instanceof Map<?, ?> override) {
            Map<String, Object> out = new LinkedHashMap<>();
            override.forEach((k, v) -> out.put(String.valueOf(k), v));
            out.putIfAbsent("derivedFrom", "INVENTORY_HINT_OVERRIDE");
            out.putIfAbsent("policyDocumentId", policyDocumentId);
            // Ensure parameters list is mutable for scorecard annotation
            if (out.get("parameters") instanceof List<?> params) {
                List<Object> mutable = new ArrayList<>(params);
                out.put("parameters", mutable);
            }
            annotateScorecardUsageWithinPolicy(out, hints);
            return out;
        }
        if (policyDocumentId == null) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("policyDocumentId", null);
            empty.put("derivedFrom", "PERSISTED_POLICY_GRAPH");
            empty.put("graphPresent", false);
            empty.put("parameters", List.of());
            empty.put("unresolvedOperandCount", 0);
            empty.put("inventoryState", "NO_POLICY_DOCUMENT");
            empty.put("reason", "POLICY_DOCUMENT_ID_REQUIRED");
            return empty;
        }
        Map<String, Object> inventory = new LinkedHashMap<>(policyRuleGraphService.parameterInventory(policyDocumentId));
        annotateScorecardUsageWithinPolicy(inventory, hints);
        return inventory;
    }

    public List<PolicyParameterRequirement> build(UUID policyDocumentId, Map<String, Object> inventoryHints) {
        Map<String, Object> raw = buildRaw(policyDocumentId, inventoryHints);
        return toRequirements(raw);
    }

    @SuppressWarnings("unchecked")
    public List<PolicyParameterRequirement> toRequirements(Map<String, Object> inventory) {
        List<PolicyParameterRequirement> out = new ArrayList<>();
        Object paramsObj = inventory.get("parameters");
        if (!(paramsObj instanceof List<?> params)) {
            return out;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (Object p : params) {
            if (!(p instanceof Map<?, ?> rowRaw)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            rowRaw.forEach((k, v) -> row.put(String.valueOf(k), v));

            String canonicalId = row.get("canonicalParameterId") != null
                    ? String.valueOf(row.get("canonicalParameterId")) : null;
            if (canonicalId != null && canonicalId.isBlank()) {
                canonicalId = null;
            }
            String originalToken = row.get("originalToken") != null
                    ? String.valueOf(row.get("originalToken")) : null;
            String status = row.get("resolutionStatus") != null
                    ? String.valueOf(row.get("resolutionStatus"))
                    : (canonicalId != null ? CiPolicyRuleGraphOperand.RESOLVED
                    : CiPolicyRuleGraphOperand.UNRESOLVED_CANONICAL_PARAMETER);
            boolean unresolved = canonicalId == null
                    || CiPolicyRuleGraphOperand.UNRESOLVED_CANONICAL_PARAMETER.equals(status)
                    || status.startsWith("UNRESOLVED");
            String key = unresolved
                    ? ("UNRESOLVED:" + (originalToken != null ? originalToken : row.toString()))
                    : canonicalId;
            if (!seen.add(key)) {
                continue; // inventory already deduped; belt-and-suspenders
            }

            List<String> ruleRefs = new ArrayList<>();
            if (row.get("ruleReferences") instanceof List<?> refs) {
                refs.forEach(r -> {
                    if (r != null) ruleRefs.add(String.valueOf(r));
                });
            }
            List<String> usages = new ArrayList<>();
            if (row.get("usageTypes") instanceof List<?> u) {
                u.forEach(x -> {
                    if (x != null) usages.add(String.valueOf(x));
                });
            } else if (row.get("usageType") != null) {
                usages.add(String.valueOf(row.get("usageType")));
            }

            boolean required = row.get("required") == null || Boolean.TRUE.equals(row.get("required"));
            String readiness = row.get("overallReadiness") != null
                    ? String.valueOf(row.get("overallReadiness")) : null;

            out.add(new PolicyParameterRequirement(
                    canonicalId,
                    originalToken,
                    status,
                    required,
                    unresolved,
                    row.get("businessName") != null ? String.valueOf(row.get("businessName")) : null,
                    ruleRefs,
                    usages,
                    readiness,
                    row.get("productionReady") instanceof Boolean b ? b : null,
                    row.get("runtimeReady") instanceof Boolean b ? b : null,
                    row.get("policyTestReady") instanceof Boolean b ? b : null,
                    row.get("sourceFamily") != null ? String.valueOf(row.get("sourceFamily")) : null,
                    row));
        }
        return out;
    }

    /**
     * Scorecard may mark SCORING_FACTOR on Policy parameters only — never add outside-Policy ids.
     */
    @SuppressWarnings("unchecked")
    private void annotateScorecardUsageWithinPolicy(Map<String, Object> inventory, Map<String, Object> hints) {
        Object scorecardIds = hints.get("scorecardCanonicalParameterIds");
        if (!(scorecardIds instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        Set<String> scorecard = new LinkedHashSet<>();
        for (Object o : list) {
            if (o != null && !String.valueOf(o).isBlank()) {
                scorecard.add(String.valueOf(o).trim());
            }
        }
        if (!(inventory.get("parameters") instanceof List<?> params)) {
            return;
        }
        Set<String> policyIds = new LinkedHashSet<>();
        for (Object p : params) {
            if (p instanceof Map<?, ?> m && m.get("canonicalParameterId") != null) {
                policyIds.add(String.valueOf(m.get("canonicalParameterId")));
            }
        }
        List<String> rejectedOutsidePolicy = new ArrayList<>();
        for (String sid : scorecard) {
            if (!policyIds.contains(sid)) {
                rejectedOutsidePolicy.add(sid);
            }
        }
        inventory.put("scorecardParametersRejectedOutsidePolicy", rejectedOutsidePolicy);

        for (Object p : params) {
            if (!(p instanceof Map<?, ?> raw)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) raw;
            Object cid = row.get("canonicalParameterId");
            if (cid == null || !scorecard.contains(String.valueOf(cid))) {
                continue;
            }
            List<String> usages = new ArrayList<>();
            if (row.get("usageTypes") instanceof List<?> u) {
                u.forEach(x -> usages.add(String.valueOf(x)));
            }
            if (!usages.contains(CiPolicyRuleGraphOperand.USAGE_SCORING_FACTOR)) {
                usages.add(CiPolicyRuleGraphOperand.USAGE_SCORING_FACTOR);
            }
            row.put("usageTypes", usages);
            row.put("scorecardUsage", true);
        }
    }
}
