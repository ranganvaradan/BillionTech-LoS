package com.los.core.creditintelligence.policystudio.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyStudioSessionSnapshot;
import com.los.core.creditintelligence.policystudio.domain.DocumentStatus;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;
import com.los.core.creditintelligence.policystudio.repository.CiPolicyDocumentRepository;
import com.los.core.creditintelligence.policystudio.repository.CiPolicyRuleGraphNodeRepository;
import com.los.core.creditintelligence.policystudio.repository.CiPolicyRuleGraphOperandRepository;
import com.los.core.creditintelligence.policystudio.repository.CiPolicyRuleGraphRepository;
import com.los.core.creditintelligence.policystudio.repository.CiPolicyStudioSessionSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * DP-3 — lossless materialization of snapshot ruleCandidates into durable Policy rule graph.
 */
@Service
@RequiredArgsConstructor
public class PolicyRuleGraphMaterializer {

    private final CiPolicyDocumentRepository documentRepository;
    private final CiPolicyStudioSessionSnapshotRepository snapshotRepository;
    private final CiPolicyRuleGraphRepository graphRepository;
    private final CiPolicyRuleGraphNodeRepository nodeRepository;
    private final CiPolicyRuleGraphOperandRepository operandRepository;
    private final ObjectMapper objectMapper;
    /** Proxy self so {@code materializeAll} → {@code materializeDocument} honors REQUIRES_NEW. */
    private final ObjectProvider<PolicyRuleGraphMaterializer> selfProvider;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Map<String, Object> materializeDocument(UUID policyDocumentId) {
        CiPolicyDocument doc = documentRepository.findById(policyDocumentId)
                .orElseThrow(() -> new IllegalArgumentException("policy document not found: " + policyDocumentId));
        int docVersion = doc.getDocumentVersion() != null ? doc.getDocumentVersion() : 1;
        Optional<CiPolicyRuleGraph> existing = graphRepository
                .findByPolicyDocumentIdAndDocumentVersion(doc.getId(), docVersion);
        if (existing.isPresent() && existing.get().isImmutable()) {
            return summary(existing.get(), "SKIPPED_IMMUTABLE");
        }

        List<CiPolicyRuleCandidate> rules = loadRuleCandidates(doc.getId());
        CanonicalParameterRegistry registry = PolicyStudioConvergencePresenter.registry();

        if (existing.isPresent()) {
            UUID gid = existing.get().getId();
            operandRepository.deleteByGraphId(gid);
            nodeRepository.deleteByGraphId(gid);
            graphRepository.delete(existing.get());
            graphRepository.flush();
        }

        String snapshotHash = snapshotContentHash(doc.getId());
        List<CiPolicyRuleGraphNode> nodes = new ArrayList<>();
        List<CiPolicyRuleGraphOperand> operands = new ArrayList<>();
        int unresolved = 0;
        StringBuilder hashBasis = new StringBuilder();

        CiPolicyRuleGraph graph = CiPolicyRuleGraph.builder()
                .policyDocumentId(doc.getId())
                .policyVersionLabel(String.valueOf(docVersion))
                .documentVersion(docVersion)
                .graphHash("pending")
                .sourceSnapshotHash(snapshotHash)
                .immutable(isGovernedImmutable(doc))
                .status("MATERIALIZED")
                .build();
        graph = graphRepository.save(graph);

        int sort = 0;
        Set<String> usedRuleKeys = new HashSet<>();
        for (CiPolicyRuleCandidate rule : rules) {
            Map<String, Object> expr = rule.getExpression() != null ? rule.getExpression() : Map.of();
            String ruleKey = uniqueRuleKey(rule, usedRuleKeys);
            String contentHash = sha256(stableJson(expr) + "|" + ruleKey);
            hashBasis.append(contentHash).append(';');

            String wording = humanWording(rule);
            CiPolicyRuleGraphNode node = CiPolicyRuleGraphNode.builder()
                    .graphId(graph.getId())
                    .ruleKey(ruleKey)
                    .systemRuleId(rule.getSystemRuleId())
                    .ruleType(rule.getRuleType() != null ? rule.getRuleType() : "HARD")
                    .expression(new LinkedHashMap<>(expr))
                    .onTrue(nz(rule.getOnTrue(), "FAIL"))
                    .onFalse(nz(rule.getOnFalse(), "PASS"))
                    .onMissing(nz(rule.getOnMissing(), "DATA_INSUFFICIENT"))
                    .humanWording(wording)
                    .sourceSnapshotRef("ci_policy_studio_session_snapshot:" + doc.getId())
                    .contentHash(contentHash)
                    .sortOrder(sort++)
                    .metadata(rule.getMetadata() != null ? rule.getMetadata() : Map.of())
                    .build();
            node = nodeRepository.save(node);
            nodes.add(node);

            String usage = usageFromRuleType(node.getRuleType());
            List<PolicyDslOperandExtractor.ExtractedOperand> extracted =
                    PolicyDslOperandExtractor.extract(expr, registry);
            if (extracted.isEmpty()) {
                // CM authoring often stores exact GACAT IDs in metadata when the AST is structural.
                extracted = PolicyDslOperandExtractor.extractFromRuleMetadata(rule.getMetadata(), registry);
            }
            for (PolicyDslOperandExtractor.ExtractedOperand op : extracted) {
                if (CiPolicyRuleGraphOperand.UNRESOLVED_CANONICAL_PARAMETER.equals(op.resolutionStatus())) {
                    unresolved++;
                }
                operands.add(operandRepository.save(CiPolicyRuleGraphOperand.builder()
                        .nodeId(node.getId())
                        .graphId(graph.getId())
                        .operandPath(op.path())
                        .originalToken(op.originalToken())
                        .canonicalParameterId(op.canonicalParameterId())
                        .resolutionStatus(op.resolutionStatus())
                        .usageType(usage)
                        .required(true)
                        .refKind(op.refKind())
                        .build()));
            }
        }

        graph.setRuleCount(nodes.size());
        graph.setUnresolvedOperandCount(unresolved);
        graph.setGraphHash(sha256(hashBasis.toString()));
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("dp3", true);
        meta.put("source", "SESSION_SNAPSHOT_RULE_CANDIDATES");
        meta.put("operandCount", operands.size());
        meta.put("allowCanonicalAuthority", false);
        meta.put("policyReadyImpliesGraphOnly", false);
        graph.setMetadata(meta);
        graph = graphRepository.save(graph);

        return summary(graph, "MATERIALIZED");
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Map<String, Object> materializeAll() {
        PolicyRuleGraphMaterializer self = selfProvider.getObject();
        List<CiPolicyDocument> docs = documentRepository.findAll();
        int ok = 0;
        int skipped = 0;
        int failed = 0;
        List<Map<String, Object>> details = new ArrayList<>();
        for (CiPolicyDocument doc : docs) {
            try {
                Map<String, Object> r = self.materializeDocument(doc.getId());
                details.add(r);
                if ("SKIPPED_IMMUTABLE".equals(r.get("action"))) skipped++;
                else ok++;
            } catch (Exception e) {
                failed++;
                details.add(Map.of("policyDocumentId", doc.getId().toString(), "error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("materialized", ok);
        out.put("skippedImmutable", skipped);
        out.put("failed", failed);
        out.put("details", details);
        out.put("dp3", true);
        return out;
    }

    /**
     * Materialize from an in-memory session (authoring round-trip / tests) without requiring snapshot row.
     */
    @Transactional
    public Map<String, Object> materializeFromCandidates(
            UUID policyDocumentId,
            int documentVersion,
            String versionLabel,
            List<CiPolicyRuleCandidate> rules,
            boolean immutable) {
        CiPolicyDocument doc = documentRepository.findById(policyDocumentId).orElse(null);
        Optional<CiPolicyRuleGraph> existing = graphRepository
                .findByPolicyDocumentIdAndDocumentVersion(policyDocumentId, documentVersion);
        if (existing.isPresent() && existing.get().isImmutable()) {
            return summary(existing.get(), "SKIPPED_IMMUTABLE");
        }
        if (existing.isPresent()) {
            UUID gid = existing.get().getId();
            operandRepository.deleteByGraphId(gid);
            nodeRepository.deleteByGraphId(gid);
            graphRepository.delete(existing.get());
            graphRepository.flush();
        }

        CanonicalParameterRegistry registry = PolicyStudioConvergencePresenter.registry();
        CiPolicyRuleGraph graph = graphRepository.save(CiPolicyRuleGraph.builder()
                .policyDocumentId(policyDocumentId)
                .policyVersionLabel(versionLabel != null ? versionLabel : String.valueOf(documentVersion))
                .documentVersion(documentVersion)
                .graphHash("pending")
                .sourceSnapshotHash(doc != null ? snapshotContentHash(policyDocumentId) : null)
                .immutable(immutable)
                .status("MATERIALIZED")
                .build());

        int unresolved = 0;
        StringBuilder hashBasis = new StringBuilder();
        int sort = 0;
        Set<String> usedRuleKeys = new HashSet<>();
        List<CiPolicyRuleCandidate> safe = rules != null ? rules : List.of();
        for (CiPolicyRuleCandidate rule : safe) {
            Map<String, Object> expr = rule.getExpression() != null ? rule.getExpression() : Map.of();
            String ruleKey = uniqueRuleKey(rule, usedRuleKeys);
            String contentHash = sha256(stableJson(expr) + "|" + ruleKey);
            hashBasis.append(contentHash).append(';');
            CiPolicyRuleGraphNode node = nodeRepository.save(CiPolicyRuleGraphNode.builder()
                    .graphId(graph.getId())
                    .ruleKey(ruleKey)
                    .systemRuleId(rule.getSystemRuleId())
                    .ruleType(rule.getRuleType() != null ? rule.getRuleType() : "HARD")
                    .expression(new LinkedHashMap<>(expr))
                    .onTrue(nz(rule.getOnTrue(), "FAIL"))
                    .onFalse(nz(rule.getOnFalse(), "PASS"))
                    .onMissing(nz(rule.getOnMissing(), "DATA_INSUFFICIENT"))
                    .humanWording(humanWording(rule))
                    .sourceSnapshotRef("session-or-test")
                    .contentHash(contentHash)
                    .sortOrder(sort++)
                    .metadata(rule.getMetadata() != null ? rule.getMetadata() : Map.of())
                    .build());
            String usage = usageFromRuleType(node.getRuleType());
            for (PolicyDslOperandExtractor.ExtractedOperand op : PolicyDslOperandExtractor.extract(expr, registry)) {
                if (CiPolicyRuleGraphOperand.UNRESOLVED_CANONICAL_PARAMETER.equals(op.resolutionStatus())) {
                    unresolved++;
                }
                operandRepository.save(CiPolicyRuleGraphOperand.builder()
                        .nodeId(node.getId())
                        .graphId(graph.getId())
                        .operandPath(op.path())
                        .originalToken(op.originalToken())
                        .canonicalParameterId(op.canonicalParameterId())
                        .resolutionStatus(op.resolutionStatus())
                        .usageType(usage)
                        .required(true)
                        .refKind(op.refKind())
                        .build());
            }
        }
        graph.setRuleCount(safe.size());
        graph.setUnresolvedOperandCount(unresolved);
        graph.setGraphHash(sha256(hashBasis.toString()));
        graph.setMetadata(Map.of("dp3", true, "source", "CANDIDATES"));
        graph = graphRepository.save(graph);
        return summary(graph, "MATERIALIZED");
    }

    @SuppressWarnings("unchecked")
    private List<CiPolicyRuleCandidate> loadRuleCandidates(UUID policyDocumentId) {
        Optional<CiPolicyStudioSessionSnapshot> snap = snapshotRepository.findById(policyDocumentId);
        if (snap.isEmpty() || snap.get().getPayload() == null) {
            return List.of();
        }
        Object raw = snap.get().getPayload().get("ruleCandidates");
        List<CiPolicyRuleCandidate> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof CiPolicyRuleCandidate c) {
                    out.add(c);
                } else if (o instanceof Map<?, ?> m) {
                    out.add(objectMapper.convertValue(m, CiPolicyRuleCandidate.class));
                }
            }
        }
        return out;
    }

    private String snapshotContentHash(UUID policyDocumentId) {
        return snapshotRepository.findById(policyDocumentId)
                .map(s -> sha256(stableJson(s.getPayload())))
                .orElse(null);
    }

    /** Public for ensureMaterialized hash-drift checks. */
    public String currentSnapshotContentHash(UUID policyDocumentId) {
        return snapshotContentHash(policyDocumentId);
    }

    public int currentSnapshotRuleCount(UUID policyDocumentId) {
        return loadRuleCandidates(policyDocumentId).size();
    }

    private static boolean isGovernedImmutable(CiPolicyDocument doc) {
        if (Boolean.TRUE.equals(doc.getRuleGraphImmutable())) return true;
        String status = doc.getStatus() != null ? doc.getStatus() : "";
        return DocumentStatus.APPROVED_FOR_POLICY_BUILD.name().equals(status)
                || "APPROVED".equalsIgnoreCase(status)
                || "ACTIVE".equalsIgnoreCase(status)
                || "PUBLISHED".equalsIgnoreCase(status);
    }

    private static String uniqueRuleKey(CiPolicyRuleCandidate rule, Set<String> usedRuleKeys) {
        String baseKey = rule.getSystemRuleId() != null && !rule.getSystemRuleId().isBlank()
                ? rule.getSystemRuleId()
                : ("rule-" + (rule.getId() != null ? rule.getId() : UUID.randomUUID()));
        String ruleKey = baseKey;
        int dedupe = 2;
        while (!usedRuleKeys.add(ruleKey)) {
            ruleKey = baseKey + "#" + dedupe++;
        }
        return ruleKey;
    }

    private static String usageFromRuleType(String ruleType) {
        if (ruleType == null) return CiPolicyRuleGraphOperand.USAGE_HARD_RULE;
        String t = ruleType.toUpperCase();
        if (t.contains("SCORE")) return CiPolicyRuleGraphOperand.USAGE_SCORING_FACTOR;
        if (t.contains("DECISION") || t.contains("SOFT")) return CiPolicyRuleGraphOperand.USAGE_DECISION_RULE;
        if (t.contains("CALC")) return CiPolicyRuleGraphOperand.USAGE_CALCULATION_INPUT;
        if (t.contains("HARD") || t.contains("ELIG")) return CiPolicyRuleGraphOperand.USAGE_HARD_RULE;
        return CiPolicyRuleGraphOperand.USAGE_OTHER;
    }

    private static String humanWording(CiPolicyRuleCandidate rule) {
        if (rule.getMetadata() != null) {
            Object w = rule.getMetadata().get("humanWording");
            if (w == null) w = rule.getMetadata().get("wording");
            if (w == null) w = rule.getMetadata().get("naturalLanguage");
            if (w != null) return String.valueOf(w);
        }
        return rule.getSystemRuleId();
    }

    private Map<String, Object> summary(CiPolicyRuleGraph graph, String action) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("action", action);
        out.put("graphId", graph.getId());
        out.put("policyDocumentId", graph.getPolicyDocumentId());
        out.put("documentVersion", graph.getDocumentVersion());
        out.put("graphHash", graph.getGraphHash());
        out.put("ruleCount", graph.getRuleCount());
        out.put("unresolvedOperandCount", graph.getUnresolvedOperandCount());
        out.put("immutable", graph.isImmutable());
        out.put("policyReady", false); // materialization alone never implies ready
        out.put("hasUnresolvedOperands", graph.getUnresolvedOperandCount() > 0);
        return out;
    }

    private String stableJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o == null ? Map.of() : o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    private static String nz(String v, String d) {
        return v == null || v.isBlank() ? d : v;
    }
}
