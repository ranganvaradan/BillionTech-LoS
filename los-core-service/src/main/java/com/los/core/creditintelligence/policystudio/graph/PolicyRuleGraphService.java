package com.los.core.creditintelligence.policystudio.graph;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;
import com.los.core.creditintelligence.policystudio.parameters.derived.CiGacatDerivedCalculationDefinition;
import com.los.core.creditintelligence.policystudio.parameters.derived.DerivedCalculationDefinitionService;
import com.los.core.creditintelligence.policystudio.truth.CanonicalParameterStateService;
import com.los.core.creditintelligence.policystudio.repository.CiPolicyDocumentRepository;
import com.los.core.creditintelligence.policystudio.repository.CiPolicyRuleGraphNodeRepository;
import com.los.core.creditintelligence.policystudio.repository.CiPolicyRuleGraphOperandRepository;
import com.los.core.creditintelligence.policystudio.repository.CiPolicyRuleGraphRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * DP-3 — queryable Policy rule graph + parameter inventory + Policy Test AST load.
 */
@Service
@RequiredArgsConstructor
public class PolicyRuleGraphService {

    private final CiPolicyRuleGraphRepository graphRepository;
    private final CiPolicyRuleGraphNodeRepository nodeRepository;
    private final CiPolicyRuleGraphOperandRepository operandRepository;
    private final CiPolicyDocumentRepository documentRepository;
    private final PolicyRuleGraphMaterializer materializer;
    private final ObjectProvider<DerivedCalculationDefinitionService> derivedCalculationDefinitionService;

    @Transactional(readOnly = true)
    public Optional<CiPolicyRuleGraph> latestGraph(UUID policyDocumentId) {
        return graphRepository.findFirstByPolicyDocumentIdOrderByDocumentVersionDesc(policyDocumentId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> loadPersistedAstRules(UUID policyDocumentId) {
        CiPolicyRuleGraph graph = latestGraph(policyDocumentId)
                .orElseThrow(() -> new IllegalStateException("No persisted Policy rule graph for " + policyDocumentId));
        List<CiPolicyRuleGraphNode> nodes = nodeRepository.findByGraphIdOrderBySortOrderAsc(graph.getId());
        List<Map<String, Object>> out = new ArrayList<>();
        for (CiPolicyRuleGraphNode n : nodes) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ruleKey", n.getRuleKey());
            row.put("systemRuleId", n.getSystemRuleId());
            row.put("ruleType", n.getRuleType());
            row.put("expression", n.getExpression());
            row.put("onTrue", n.getOnTrue());
            row.put("onFalse", n.getOnFalse());
            row.put("onMissing", n.getOnMissing());
            row.put("humanWording", n.getHumanWording());
            row.put("contentHash", n.getContentHash());
            row.put("metadata", n.getMetadata() == null ? Map.of() : n.getMetadata());
            row.put("participates", PolicyGraphParticipation.participates(n));
            out.add(row);
        }
        return out;
    }

    /**
     * Policy Test must use persisted graph. Unresolved operands → fail closed.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> policyTestGate(UUID policyDocumentId) {
        Optional<CiPolicyRuleGraph> opt = latestGraph(policyDocumentId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("policyDocumentId", policyDocumentId);
        if (opt.isEmpty()) {
            out.put("allowed", false);
            out.put("reason", "POLICY_GRAPH_NOT_MATERIALIZED");
            return out;
        }
        CiPolicyRuleGraph graph = opt.get();
        out.put("graphId", graph.getId());
        out.put("graphHash", graph.getGraphHash());
        out.put("unresolvedOperandCount", graph.getUnresolvedOperandCount());
        if (graph.getUnresolvedOperandCount() > 0) {
            out.put("allowed", false);
            out.put("reason", "UNRESOLVED_CANONICAL_PARAMETER");
            out.put("failClosed", true);
            return out;
        }
        out.put("allowed", true);
        out.put("rules", loadPersistedAstRules(policyDocumentId));
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> parameterInventory(UUID policyDocumentId) {
        Optional<CiPolicyRuleGraph> opt = latestGraph(policyDocumentId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("policyDocumentId", policyDocumentId);
        out.put("derivedFrom", "PERSISTED_POLICY_GRAPH");
        if (opt.isEmpty()) {
            out.put("parameters", List.of());
            out.put("graphPresent", false);
            out.put("unresolvedOperandCount", 0);
            out.put("inventoryState", "NO_POLICY_GRAPH");
            out.put("reason", "NO_MATERIALIZED_POLICY_GRAPH");
            return out;
        }
        CiPolicyRuleGraph graph = opt.get();
        List<CiPolicyRuleGraphOperand> operands = operandRepository.findByGraphId(graph.getId());
        CanonicalParameterRegistry registry = PolicyStudioConvergencePresenter.registry();
        Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
        for (CiPolicyRuleGraphOperand op : operands) {
            String key = op.getCanonicalParameterId() != null
                    ? op.getCanonicalParameterId()
                    : ("UNRESOLVED:" + op.getOriginalToken());
            Map<String, Object> row = byKey.computeIfAbsent(key, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("canonicalParameterId", op.getCanonicalParameterId());
                m.put("originalToken", op.getOriginalToken());
                m.put("resolutionStatus", op.getResolutionStatus());
                m.put("usageTypes", new LinkedHashSet<String>());
                m.put("required", op.isRequired());
                m.put("ruleReferences", new ArrayList<String>());
                m.put("sourceFamily", null);
                m.put("productionReady", false);
                m.put("productionCertified", false);
                m.put("overallReadiness", null);
                m.put("policyTestReady", false);
                m.put("runtimeReady", false);
                m.put("designable", true);
                return m;
            });
            @SuppressWarnings("unchecked")
            Set<String> usages = (Set<String>) row.get("usageTypes");
            usages.add(op.getUsageType());
            @SuppressWarnings("unchecked")
            List<String> refs = (List<String>) row.get("ruleReferences");
            refs.add(op.getNodeId().toString());
            if (op.getCanonicalParameterId() != null) {
                Optional<CanonicalParameterDefinition> def = registry.findById(op.getCanonicalParameterId());
                if (def.isPresent()) {
                    row.put("sourceFamily", def.get().evaluatedFrom());
                    row.put("businessName", def.get().businessName());
                    // FINAL-CANONICAL-PARAMETER-STATE — sole readiness authority
                    CanonicalParameterStateService.stamp(row, op.getCanonicalParameterId());
                    applyDerivedCalculationOverlay(row, def.get(), op.getCanonicalParameterId());
                    // Stamp again so overlay cannot override primary status
                    CanonicalParameterStateService.stamp(row, op.getCanonicalParameterId());
                }
            }
        }
        List<Map<String, Object>> params = new ArrayList<>();
        for (Map<String, Object> row : byKey.values()) {
            @SuppressWarnings("unchecked")
            Set<String> usages = (Set<String>) row.get("usageTypes");
            row.put("usageTypes", new ArrayList<>(usages));
            // primary usage type for UI
            row.put("usageType", usages.isEmpty() ? "OTHER" : usages.iterator().next());
            params.add(row);
        }
        out.put("graphPresent", true);
        out.put("graphId", graph.getId());
        out.put("graphHash", graph.getGraphHash());
        out.put("unresolvedOperandCount", graph.getUnresolvedOperandCount());
        out.put("parameters", params);
        out.put("scoringEligibleParameters", scoringEligible(params));
        if (params.isEmpty()) {
            out.put("inventoryState", "NO_PARAMETERS");
        } else if (graph.getUnresolvedOperandCount() > 0) {
            out.put("inventoryState", "LOADED_WITH_UNRESOLVED");
        } else {
            out.put("inventoryState", "LOADED_WITH_PARAMETERS");
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> scoringEligible(List<Map<String, Object>> params) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> p : params) {
            if (p.get("canonicalParameterId") == null) continue;
            if (!CiPolicyRuleGraphOperand.RESOLVED.equals(p.get("resolutionStatus"))) continue;
            out.add(p);
        }
        return out;
    }

    /** Factors allowed for Scorecard attached to this Policy — Policy parameters only. */
    @Transactional(readOnly = true)
    public Set<String> policyCanonicalParameterIds(UUID policyDocumentId) {
        Set<String> ids = new LinkedHashSet<>();
        latestGraph(policyDocumentId).ifPresent(g -> {
            for (CiPolicyRuleGraphOperand op : operandRepository.findByGraphId(g.getId())) {
                if (op.getCanonicalParameterId() != null
                        && CiPolicyRuleGraphOperand.RESOLVED.equals(op.getResolutionStatus())) {
                    ids.add(op.getCanonicalParameterId());
                }
            }
        });
        return ids;
    }

    @Transactional
    public Map<String, Object> linkScorecard(UUID policyDocumentId, UUID scorecardId) {
        CiPolicyDocument doc = documentRepository.findById(policyDocumentId)
                .orElseThrow(() -> new IllegalArgumentException("policy not found"));
        doc.setScorecardId(scorecardId);
        documentRepository.save(doc);
        return Map.of(
                "policyDocumentId", policyDocumentId,
                "scorecardId", scorecardId,
                "relationship", "POLICY_VERSION_OPTIONAL_SCORECARD_VERSION");
    }

    @Transactional
    public Map<String, Object> markImmutable(UUID policyDocumentId) {
        CiPolicyDocument doc = documentRepository.findById(policyDocumentId)
                .orElseThrow(() -> new IllegalArgumentException("policy not found"));
        doc.setRuleGraphImmutable(true);
        documentRepository.save(doc);
        latestGraph(policyDocumentId).ifPresent(g -> {
            g.setImmutable(true);
            graphRepository.save(g);
        });
        return Map.of("policyDocumentId", policyDocumentId, "ruleGraphImmutable", true);
    }

    @Transactional
    public Map<String, Object> ensureMaterialized(UUID policyDocumentId) {
        Optional<CiPolicyRuleGraph> latest = latestGraph(policyDocumentId);
        if (latest.isEmpty()) {
            return materializer.materializeDocument(policyDocumentId);
        }
        CiPolicyRuleGraph graph = latest.get();
        if (graph.isImmutable()) {
            return Map.of(
                    "action", "ALREADY_PRESENT",
                    "policyDocumentId", policyDocumentId,
                    "immutable", true);
        }
        String currentHash = materializer.currentSnapshotContentHash(policyDocumentId);
        String storedHash = graph.getSourceSnapshotHash();
        boolean hashDrift = currentHash != null && storedHash != null && !currentHash.equals(storedHash);
        int snapshotRules = materializer.currentSnapshotRuleCount(policyDocumentId);
        Integer graphRuleCount = graph.getRuleCount();
        int graphRules = graphRuleCount == null ? 0 : graphRuleCount;
        boolean emptyStale = graphRules == 0 && snapshotRules > 0;
        if (hashDrift || emptyStale || storedHash == null) {
            return materializer.materializeDocument(policyDocumentId);
        }
        return Map.of(
                "action", "ALREADY_PRESENT",
                "policyDocumentId", policyDocumentId,
                "graphId", graph.getId(),
                "ruleCount", graphRules);
    }

    /**
     * Overlay authored derived-calc metadata without inventing execution capability.
     * Readiness remains {@link CanonicalParameterStateService} only.
     */
    private void applyDerivedCalculationOverlay(
            Map<String, Object> row, CanonicalParameterDefinition def, String canonicalId) {
        boolean derivationDefined = def.capability() != null && def.capability().derivationDefined();
        boolean implemented = def.capability() != null && def.capability().implemented();
        boolean calcRequired = derivationDefined && !implemented;
        row.put("calculationRequired", calcRequired);
        row.put("legacyCatalogueImplemented", implemented);

        DerivedCalculationDefinitionService calcSvc = derivedCalculationDefinitionService.getIfAvailable();
        if (calcSvc == null) return;
        Optional<CiGacatDerivedCalculationDefinition> authored = calcSvc.latestFor(canonicalId, null);
        if (authored.isEmpty()) {
            row.put("calculationDefined", false);
            return;
        }
        CiGacatDerivedCalculationDefinition d = authored.get();
        row.put("calculationDefined", true);
        row.put("calculationDefinitionStatus", d.getStatus());
        row.put("calculationDefinitionVersion", d.getVersionNo());
        row.put("calculationRequired", false);
        if (DerivedCalculationDefinitionService.STATUS_PRODUCTION_READY.equals(d.getStatus())) {
            row.put("calculationProductionReady", true);
            // Do not auto-set production certification — promotion remains independent
        }
    }
}
