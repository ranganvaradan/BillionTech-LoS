package com.los.core.creditintelligence.policystudio.catalogue;

import com.los.core.creditintelligence.decisionpolicy.DecisionPolicyDomain;
import com.los.core.creditintelligence.decisionpolicy.DecisionPolicyRuleMetadata;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyClause;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.domain.ClauseType;
import com.los.core.creditintelligence.policystudio.domain.ReviewState;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDslSemanticEquivalence;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * POLICY-UX-2D — bind extracted clauses to catalogue capabilities after golden interpretation.
 * Replaces hardcoded golden thresholds when the document states an explicit value.
 * Does not alter production underwriting authority.
 */
@Service
public class CapabilityIngestionBindingService {

    private final CapabilityIngestionMatcher matcher;
    private final CreditCapabilityCatalogueService catalogue;

    public CapabilityIngestionBindingService(
            CapabilityIngestionMatcher matcher,
            CreditCapabilityCatalogueService catalogue) {
        this.matcher = matcher;
        this.catalogue = catalogue;
    }

    public CapabilityIngestionBindingService() {
        CreditCapabilityCatalogueService cat = new CreditCapabilityCatalogueService();
        this.catalogue = cat;
        this.matcher = new CapabilityIngestionMatcher(cat);
    }

    /**
     * Mutates session rule candidates / clause metadata. Call after RuleCandidateFactory.create.
     */
    public Map<String, Object> bind(PolicyStudioSession session) {
        if (session == null || session.getDocument() == null) {
            return Map.of();
        }
        CiPolicyDocument doc = session.getDocument();
        List<CiPolicyClause> clauses = session.getClauses();
        Map<UUID, CiPolicyRuleCandidate> rulesByClause = new HashMap<>();
        for (CiPolicyRuleCandidate r : session.getRuleCandidates()) {
            if (r.getClauseId() != null) {
                rulesByClause.putIfAbsent(r.getClauseId(), r);
            }
        }

        List<CiPolicyRuleCandidate> bound = new ArrayList<>();
        Map<String, List<CiPolicyRuleCandidate>> byCapability = new LinkedHashMap<>();
        Map<String, Integer> classificationCounts = new LinkedHashMap<>();

        for (CiPolicyClause clause : clauses) {
            CapabilityIngestionMatcher.MatchResult match = matcher.match(clause.getSourceText());
            match = overrideByClauseType(clause, match);
            classificationCounts.merge(match.classification().name(), 1, Integer::sum);

            Map<String, Object> clauseMeta = clause.getMetadata() == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(clause.getMetadata());
            clauseMeta.put("ingestionClassification", match.classification().name());
            clauseMeta.put("matchConfidence", match.confidence());
            if (match.businessCapabilityId() != null) {
                clauseMeta.put("businessCapabilityId", match.businessCapabilityId());
            }
            clause.setMetadata(clauseMeta);

            CiPolicyRuleCandidate existing = rulesByClause.get(clause.getId());
            CiPolicyRuleCandidate candidate = materialize(doc, clause, existing, match);
            if (candidate != null) {
                bound.add(candidate);
                String capId = candidate.getMetadata() == null
                        ? null
                        : (candidate.getMetadata().get("businessCapabilityId") == null
                            ? null
                            : String.valueOf(candidate.getMetadata().get("businessCapabilityId")));
                if (capId != null) {
                    byCapability.computeIfAbsent(capId, k -> new ArrayList<>()).add(candidate);
                }
            }
        }

        annotateDuplicatesAndConflicts(byCapability);

        session.getRuleCandidates().clear();
        session.getRuleCandidates().addAll(bound);

        Map<String, Object> summary = buildSummary(clauses.size(), classificationCounts, bound);
        Map<String, Object> preview = session.getPreview() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(session.getPreview());
        preview.put("ingestionBinding", summary);
        session.setPreview(preview);

        Map<String, Object> docMeta = doc.getMetadata() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(doc.getMetadata());
        docMeta.put("ingestionBinding", summary);
        doc.setMetadata(docMeta);

        return summary;
    }

    private CiPolicyRuleCandidate materialize(
            CiPolicyDocument doc,
            CiPolicyClause clause,
            CiPolicyRuleCandidate existing,
            CapabilityIngestionMatcher.MatchResult match) {

        UUID ruleId = existing != null && existing.getId() != null ? existing.getId() : UUID.randomUUID();
        UUID clauseId = clause.getId();

        Map<String, Object> lineage = existing != null && existing.getLineage() != null
                ? new LinkedHashMap<>(existing.getLineage())
                : new LinkedHashMap<>();
        lineage.put("documentId", doc.getId().toString());
        lineage.put("documentName", doc.getName());
        lineage.put("clauseId", clauseId.toString());
        lineage.put("section", clause.getSection());
        lineage.put("sourceText", clause.getSourceText());
        lineage.put("sourceLocation", clause.getSourceLocation());
        lineage.put("provenance", "DOCUMENT_INGESTION_MATCH");
        lineage.put("originalGoldenProvider", existing != null && existing.getMetadata() != null
                ? existing.getMetadata().get("provider") : null);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("classification", match.classification().name());
        meta.put("matchConfidence", match.confidence());
        meta.put("NEEDS_INPUT", match.needsInput());
        meta.put("PARAMETER_DIFFERS", match.parameterDiffers());
        meta.put("extractedParameters", match.extractedParameters());
        meta.put("catalogueDefaultParameters", match.catalogueDefaultParameters());
        meta.put("source", "DOCUMENT_CAPABILITY_MATCH");
        meta.put("provenance", "DOCUMENT_INGESTION_MATCH");
        meta.put("authoringOnly", true);
        meta.put("allowCanonicalAuthority", false);
        meta.put("policySelectsProvider", false);
        meta.put("policyEnqueuesWorkflowStep", false);
        meta.put("disposition", "EXTRACTED");
        meta.put("lastUiAction", "INGESTION_BIND");
        meta.put("lastUiActionAt", Instant.now().toString());
        meta.put("rationale", match.rationale());
        meta.put("originalInterpretation", existing != null ? existing.getExpression() : null);

        // Preserve golden executable rule when matcher is ambiguous but golden already produced DSL
        if (match.classification() == IngestionMatchClassification.AMBIGUOUS
                && existing != null
                && existing.getExpression() != null
                && !existing.getExpression().isEmpty()
                && !"UNKNOWN".equalsIgnoreCase(String.valueOf(existing.getExpression().get("op")))
                && !"CLASSIFICATION".equalsIgnoreCase(String.valueOf(existing.getExpression().get("op")))) {
            Map<String, Object> keepMeta = existing.getMetadata() == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(existing.getMetadata());
            keepMeta.put("classification", IngestionMatchClassification.NEW_AUTOMATABLE_RULE.name());
            keepMeta.put("matchConfidence", "MEDIUM");
            keepMeta.put("source", "GOLDEN_FALLBACK");
            keepMeta.put("provenance", "DETERMINISTIC_GOLDEN_V1");
            keepMeta.put("capabilityBadge", "Policy rule");
            keepMeta.put("authoringOnly", true);
            keepMeta.put("allowCanonicalAuthority", false);
            keepMeta.put("activationIncluded", true);
            keepMeta.put("excludedFromActivation", false);
            keepMeta.put("disposition", "EXTRACTED");
            keepMeta.put("NEEDS_INPUT", false);
            stampGoldenBusinessTitle(existing, keepMeta, clause);
            existing.setMetadata(keepMeta);
            existing.setLineage(lineage);
            return existing;
        }

        if (match.classification().underwritingExecutable() && match.businessCapabilityId() != null) {
            BusinessCapability cap = catalogue.findById(match.businessCapabilityId()).orElse(null);
            if (cap == null) {
                return classificationOnly(doc, clause, ruleId, lineage, meta, match);
            }
            // Prefer extracted params; do not silently replace with golden thresholds
            Map<String, Object> params = new LinkedHashMap<>(match.extractedParameters());
            CatalogueCapabilityExpressionBuilder.BuiltRule built =
                    CatalogueCapabilityExpressionBuilder.build(cap, params, match.failureTreatment());

            if (!ClauseType.HARD_RULE.name().equals(clause.getClauseType())
                    && !ClauseType.UNKNOWN.name().equals(clause.getClauseType())) {
                clause.setClauseType(ClauseType.HARD_RULE.name());
            }

            meta.put("businessCapabilityId", cap.businessCapabilityId());
            meta.put("businessTitle", cap.businessName());
            meta.put("businessSummary", match.businessSummary() != null ? match.businessSummary() : built.businessSummary());
            meta.put("parameters", params);
            Object authoringThr = params.get("minimumScore");
            if (authoringThr == null) authoringThr = params.get("maximumCount");
            if (authoringThr == null) authoringThr = params.get("maximumPercentage");
            if (authoringThr == null) authoringThr = params.get("minimumPercentage");
            if (authoringThr == null) authoringThr = params.get("minimumValue");
            if (authoringThr == null) authoringThr = params.get("maximumAmount");
            if (authoringThr == null) authoringThr = params.get("minimumRatio");
            if (authoringThr != null) {
                meta.put("threshold", authoringThr);
            }
            meta.put("failureTreatment", match.failureTreatment() != null ? match.failureTreatment() : "REJECT");
            meta.put("dataSource", cap.dataSource());
            meta.put("dataRequirement", cap.dataAvailability());
            meta.put("catalogueBacked", true);
            meta.put("existingCapability", true);
            meta.put("capabilityBadge", match.parameterDiffers()
                    ? "Existing capability · Policy parameter differs"
                    : "Existing capability · extracted from policy");
            // excludedFromActivation ≠ Ignored (CM disposition). Needs-input stays reviewable.
            meta.put("activationIncluded", !match.needsInput() && !"LOW".equals(match.confidence()));
            meta.put("excludedFromActivation", match.needsInput() || "LOW".equals(match.confidence()));
            if (match.needsInput()) {
                meta.put("NEEDS_INPUT", true);
                // Authoring threshold missing — not a runtime/application value
                meta.put("blockedReason",
                        com.los.core.creditintelligence.policystudio.parameters
                                .PolicyAuthoringCompleteness.MSG_THRESHOLD_MISSING);
            } else {
                meta.put("NEEDS_INPUT", false);
                meta.remove("blockedReason");
            }
            if (match.parameterDiffers()) {
                meta.put("productionReferenceParameters", match.catalogueDefaultParameters());
                meta.put("uploadedPolicyParameters", params);
            }
            if (Boolean.TRUE.equals(built.needsDerivation())) {
                meta.put("NEEDS_DERIVATION", true);
                meta.put("derivationNote", built.derivationNote());
            }
            meta.put(DecisionPolicyRuleMetadata.KEY_DOMAIN, domainFor(cap).name());

            // Months vintage note
            if ("ELIG.BUSINESS_VINTAGE_MIN".equals(cap.businessCapabilityId())
                    && "MONTHS".equalsIgnoreCase(String.valueOf(params.get("unit")))) {
                meta.put("implementationNote",
                        "Display preserves months; activation binding may require years conversion");
                meta.put("activationReadinessNote", "conversion/binding required");
            }

            Map<String, Object> scope = new LinkedHashMap<>();
            scope.put(DecisionPolicyRuleMetadata.KEY_DOMAIN, domainFor(cap).name());

            // P0-A: catalogue may bind metadata / template only when semantically identical to authored AST.
            if (existing != null
                    && PolicyDslSemanticEquivalence.isExecutableAst(existing.getExpression())
                    && !PolicyDslSemanticEquivalence.equivalent(
                    existing.getExpression(), existing.getOnTrue(), existing.getOnFalse(),
                    built.expression(), built.onTrue(), built.onFalse())) {
                meta.put("catalogueAstSuppressed", true);
                meta.put("catalogueAstSuppressedReason", "SEMANTIC_NOT_EQUIVALENT");
                meta.put("catalogueCandidateSystemRuleId", built.systemRuleId());
                meta.put("catalogueCandidateExpression", built.expression());
                meta.put("source", "AUTHORED_AST_PRESERVED");
                meta.put("capabilityBadge", "Policy rule · catalogue mapped (AST preserved)");
                stampGoldenBusinessTitle(existing, meta, clause);
                Map<String, Object> keepMeta = existing.getMetadata() == null
                        ? new LinkedHashMap<>()
                        : new LinkedHashMap<>(existing.getMetadata());
                keepMeta.putAll(meta);
                // Preserve golden provider provenance when AST retained
                if (existing.getMetadata() != null && existing.getMetadata().get("provider") != null) {
                    keepMeta.putIfAbsent("provider", existing.getMetadata().get("provider"));
                }
                keepMeta.put("activationIncluded", true);
                keepMeta.put("excludedFromActivation", false);
                keepMeta.put("NEEDS_INPUT", false);
                keepMeta.remove("blockedReason");
                return CiPolicyRuleCandidate.builder()
                        .id(ruleId)
                        .clauseId(clauseId)
                        .systemRuleId(existing.getSystemRuleId())
                        .ruleVersion(existing.getRuleVersion() != null ? existing.getRuleVersion() : "DRAFT")
                        .ruleType(existing.getRuleType() != null ? existing.getRuleType() : "HARD")
                        .scope(existing.getScope() != null ? existing.getScope() : scope)
                        .expression(existing.getExpression())
                        .onTrue(existing.getOnTrue())
                        .onFalse(existing.getOnFalse())
                        .onMissing(existing.getOnMissing() != null ? existing.getOnMissing() : built.onMissing())
                        .confidence(existing.getConfidence() != null
                                ? existing.getConfidence() : confidenceScore(match.confidence()))
                        .reviewStatus(ReviewState.AI_DRAFTED.name())
                        .lineage(lineage)
                        .metadata(keepMeta)
                        .build();
            }

            return CiPolicyRuleCandidate.builder()
                    .id(ruleId)
                    .clauseId(clauseId)
                    .systemRuleId(built.systemRuleId())
                    .ruleVersion("DRAFT")
                    .ruleType("HARD")
                    .scope(scope)
                    .expression(built.expression())
                    .onTrue(built.onTrue())
                    .onFalse(built.onFalse())
                    .onMissing(built.onMissing())
                    .confidence(confidenceScore(match.confidence()))
                    .reviewStatus(ReviewState.AI_DRAFTED.name())
                    .lineage(lineage)
                    .metadata(meta)
                    .build();
        }

        return classificationOnly(doc, clause, ruleId, lineage, meta, match);
    }

    /**
     * Clause-type overrides from extractor (Fields Required / ADB adjustments)
     * take precedence over free-text AMBIGUOUS matches.
     */
    private CapabilityIngestionMatcher.MatchResult overrideByClauseType(
            CiPolicyClause clause, CapabilityIngestionMatcher.MatchResult match) {
        if (clause == null || match == null) {
            return match;
        }
        String type = clause.getClauseType() == null ? "" : clause.getClauseType();
        String text = clause.getSourceText() == null ? "" : clause.getSourceText();
        if (ClauseType.INFORMATION_REQUIREMENT.name().equals(type)) {
            return new CapabilityIngestionMatcher.MatchResult(
                    IngestionMatchClassification.DATA_REQUIREMENT,
                    null, Map.of(), Map.of(), false, false, "HIGH", null,
                    "Data / report requirement — not an underwriting hard rule",
                    text.length() > 160 ? text.substring(0, 159) + "…" : text,
                    false, null, null);
        }
        if (ClauseType.METRIC_ADJUSTMENT.name().equals(type)) {
            return new CapabilityIngestionMatcher.MatchResult(
                    IngestionMatchClassification.METRIC_ADJUSTMENT,
                    null, Map.of(), Map.of(), false, false, "HIGH", null,
                    "Metric definition adjustment for Average Daily Balance",
                    text.length() > 160 ? text.substring(0, 159) + "…" : text,
                    false, null, null);
        }
        return match;
    }

    private CiPolicyRuleCandidate classificationOnly(
            CiPolicyDocument doc,
            CiPolicyClause clause,
            UUID ruleId,
            Map<String, Object> lineage,
            Map<String, Object> meta,
            CapabilityIngestionMatcher.MatchResult match) {

        meta.put("catalogueBacked", false);
        meta.put("existingCapability", false);
        meta.put("businessTitle", classificationTitle(match));
        meta.put("businessSummary", match.businessSummary());
        meta.put("capabilityBadge", classificationBadge(match.classification()));
        meta.put("activationIncluded", false);
        meta.put("excludedFromActivation", true);
        meta.put("classificationOnly", true);
        if (match.classification() == IngestionMatchClassification.DATA_REQUIREMENT
                || match.classification() == IngestionMatchClassification.REPORT_FIELD
                || match.classification() == IngestionMatchClassification.DOCUMENT_REQUIREMENT) {
            meta.put("dataRequirementOnly", true);
            meta.put("disposition", "EXTRACTED");
        }
        if (match.classification() == IngestionMatchClassification.METRIC_ADJUSTMENT) {
            meta.put("metricAdjustment", true);
            meta.put("affectedMetric", "banking.avg_daily_balance_3m");
            meta.put("disposition", "EXTRACTED");
        }
        if (match.classification() == IngestionMatchClassification.MANUAL_INPUT) {
            meta.put("disposition", "MANUAL_INPUT");
            meta.put("verificationMode", "MANUAL");
            meta.put("manualInputLabel", match.manualInputLabel());
            meta.put("manualInputType", match.manualInputType() != null ? match.manualInputType() : "NUMBER");
            meta.put("requiredActor", "Credit Officer");
        }
        String group = match.classification().displayGroup();
        if (group != null) {
            meta.put("businessGroupOverride", group);
        }
        meta.put(DecisionPolicyRuleMetadata.KEY_DOMAIN, DecisionPolicyDomain.CREDIT.name());

        Map<String, Object> expression = new LinkedHashMap<>();
        expression.put("op", "CLASSIFICATION");
        expression.put("classification", match.classification().name());
        expression.put("sourceText", clause.getSourceText());

        return CiPolicyRuleCandidate.builder()
                .id(ruleId)
                .clauseId(clause.getId())
                .systemRuleId("CLASSIFICATION_" + match.classification().name())
                .ruleVersion("DRAFT")
                .ruleType("CLASSIFICATION")
                .scope(Map.of(DecisionPolicyRuleMetadata.KEY_DOMAIN, DecisionPolicyDomain.CREDIT.name()))
                .expression(expression)
                .onTrue("INFO")
                .onFalse("INFO")
                .onMissing("INFO")
                .confidence(confidenceScore(match.confidence()))
                .reviewStatus(ReviewState.AI_DRAFTED.name())
                .lineage(lineage)
                .metadata(meta)
                .build();
    }

    private void annotateDuplicatesAndConflicts(Map<String, List<CiPolicyRuleCandidate>> byCapability) {
        for (Map.Entry<String, List<CiPolicyRuleCandidate>> e : byCapability.entrySet()) {
            List<CiPolicyRuleCandidate> list = e.getValue();
            if (list.size() < 2) continue;
            // Compare parameters
            boolean conflict = false;
            Map<String, Object> firstParams = paramsOf(list.get(0));
            for (int i = 1; i < list.size(); i++) {
                if (!Objects.equals(firstParams, paramsOf(list.get(i)))) {
                    conflict = true;
                    break;
                }
            }
            for (CiPolicyRuleCandidate r : list) {
                Map<String, Object> meta = r.getMetadata() == null
                        ? new LinkedHashMap<>()
                        : new LinkedHashMap<>(r.getMetadata());
                if (conflict) {
                    meta.put("capabilityConflict", true);
                    meta.put("blockedReason", "Conflict requiring review — same capability with different parameters");
                    meta.put("capabilityBadge", "Conflict · same capability, different values");
                    meta.put("activationIncluded", false);
                    meta.put("excludedFromActivation", true);
                    meta.put("matchConfidence", "LOW");
                } else {
                    meta.put("potentialDuplicate", true);
                    meta.put("capabilityBadge", "Potential duplicate");
                    meta.put("blockedReason", "Potential duplicate — keep one or delete extras");
                    meta.put("matchConfidence", "MEDIUM");
                }
                r.setMetadata(meta);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> paramsOf(CiPolicyRuleCandidate r) {
        if (r.getMetadata() == null) return Map.of();
        Object p = r.getMetadata().get("parameters");
        if (p instanceof Map<?, ?> m) {
            return new LinkedHashMap<>((Map<String, Object>) m);
        }
        return Map.of();
    }

    private Map<String, Object> buildSummary(
            int clauseCount,
            Map<String, Integer> classificationCounts,
            List<CiPolicyRuleCandidate> bound) {
        long existing = bound.stream().filter(r -> Boolean.TRUE.equals(
                r.getMetadata() != null ? r.getMetadata().get("catalogueBacked") : null)).count();
        long manualInput = countClass(classificationCounts, "MANUAL_INPUT");
        long manualReview = countClass(classificationCounts, "MANUAL_REVIEW");
        long product = countClass(classificationCounts, "PRODUCT_CONFIG");
        long docs = countClass(classificationCounts, "DOCUMENT_REQUIREMENT");
        long portfolio = countClass(classificationCounts, "PORTFOLIO_CONTROL");
        long servicing = countClass(classificationCounts, "SERVICING_RULE");
        long narrative = countClass(classificationCounts, "NARRATIVE")
                + countClass(classificationCounts, "AMBIGUOUS");
        long highReady = bound.stream().filter(this::acceptAllEligible).count();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("title", "Ingestion binding summary");
        summary.put("totalClauses", clauseCount);
        summary.put("boundRules", bound.size());
        summary.put("existingAutomatedCapabilities", existing);
        summary.put("manualInputs", manualInput);
        summary.put("manualReviews", manualReview);
        summary.put("productConfiguration", product);
        summary.put("documentRequirements", docs);
        summary.put("portfolioControls", portfolio);
        summary.put("servicingOrNarrative", servicing + narrative);
        summary.put("classificationCounts", classificationCounts);
        summary.put("acceptAllReadyEligible", highReady);
        summary.put("allowCanonicalAuthority", false);
        summary.put("primaryCta", "Save Draft");
        return summary;
    }

    private boolean acceptAllEligible(CiPolicyRuleCandidate r) {
        Map<String, Object> meta = r.getMetadata();
        if (meta == null) return false;
        if (Boolean.TRUE.equals(meta.get("NEEDS_INPUT"))) return false;
        if (Boolean.TRUE.equals(meta.get("capabilityConflict"))) return false;
        if (Boolean.TRUE.equals(meta.get("potentialDuplicate"))) return false;
        if (Boolean.TRUE.equals(meta.get("dataRequirementOnly"))) return false;
        if (Boolean.TRUE.equals(meta.get("metricAdjustment"))) return false;
        if (Boolean.TRUE.equals(meta.get("classificationOnly"))
                && !IngestionMatchClassification.NEW_AUTOMATABLE_RULE.name()
                .equals(String.valueOf(meta.get("classification")))) {
            return false;
        }
        String avail = String.valueOf(meta.getOrDefault("dataAvailability", ""));
        if ("UNAVAILABLE".equals(avail) || "NEEDS_CONFIGURATION".equals(avail)) return false;
        String cls = String.valueOf(meta.get("classification"));
        boolean executable = "EXACT_EXISTING_CAPABILITY".equals(cls)
                || "EXISTING_CAPABILITY_PARAMETER_CHANGE".equals(cls)
                || "EXISTING_CAPABILITY_MANUAL_DATA".equals(cls)
                || "NEW_AUTOMATABLE_RULE".equals(cls)
                || Boolean.TRUE.equals(meta.get("catalogueBacked"))
                || "GOLDEN_FALLBACK".equals(String.valueOf(meta.get("source")));
        return executable && !Boolean.TRUE.equals(meta.get("excludedFromActivation"));
    }

    private static void stampGoldenBusinessTitle(
            CiPolicyRuleCandidate existing, Map<String, Object> meta, CiPolicyClause clause) {
        String sys = existing.getSystemRuleId() == null ? "" : existing.getSystemRuleId().toUpperCase(Locale.ROOT);
        String src = clause.getSourceText() == null ? "" : clause.getSourceText();
        if (sys.contains("SETTLEMENT_COUNT") || src.toLowerCase(Locale.ROOT).contains("number of settlements")) {
            meta.put("businessTitle", "Average monthly settlements");
            meta.put("businessSummary", "At least 20 per month");
            meta.put("dataAvailability", "DERIVABLE_FROM_AVAILABLE_DATA");
            meta.put("failureTreatment", "REJECT");
        } else if (sys.contains("SETTLEMENT_DIV") || src.toLowerCase(Locale.ROOT).contains("daily settlements")) {
            meta.put("businessTitle", "Settlement capacity vs EDI");
            meta.put("businessSummary", "One tenth of average daily settlements ≥ proposed EDI");
            meta.put("dataAvailability", "DERIVABLE_FROM_AVAILABLE_DATA");
            meta.put("failureTreatment", "REJECT");
        } else if (sys.contains("INWARD_RETURN")) {
            meta.put("businessTitle", "Inward cheque / ECS / ENACH returns");
            meta.put("businessSummary",
                    "If transactions > 100 in last 3 months: return ratio ≤ 5%; if < 100: return count ≤ 5");
            meta.put("businessCapabilityId", "BANK.INWARD_RETURN_MAX");
            meta.put("dataAvailability", "AVAILABLE_AUTOMATICALLY");
            meta.put("failureTreatment", "REJECT");
        } else if (sys.contains("ADB") || sys.contains("STARTER") || sys.contains("DIGILEAP")
                || sys.contains("REBOOST")) {
            if (meta.get("businessTitle") == null) {
                meta.put("businessTitle", friendlyGoldenTitle(sys));
            }
            meta.put("dataAvailability", "AVAILABLE_AUTOMATICALLY");
            meta.put("failureTreatment", meta.getOrDefault("failureTreatment", "REJECT"));
        }
    }

    private static String friendlyGoldenTitle(String sys) {
        if (sys.contains("STARTER")) return "Starter — banking capacity (ADB ≥ EDI)";
        if (sys.contains("DIGILEAP") && sys.contains("TXN")) return "DigiLeap — monthly transactions";
        if (sys.contains("DIGILEAP")) return "DigiLeap — banking capacity";
        if (sys.contains("REBOOST") && sys.contains("TXN")) return "Reboost — monthly transactions";
        if (sys.contains("REBOOST")) return "Reboost — banking capacity";
        return "Banking policy rule";
    }

    private static long countClass(Map<String, Integer> counts, String key) {
        return counts.getOrDefault(key, 0);
    }

    private static DecisionPolicyDomain domainFor(BusinessCapability cap) {
        return switch (cap.domain()) {
            case KYC -> DecisionPolicyDomain.KYC;
            case ELIGIBILITY -> DecisionPolicyDomain.ELIGIBILITY;
            default -> DecisionPolicyDomain.CREDIT;
        };
    }

    private static BigDecimal confidenceScore(String band) {
        return switch (band == null ? "" : band.toUpperCase(Locale.ROOT)) {
            case "HIGH" -> new BigDecimal("0.9200");
            case "MEDIUM" -> new BigDecimal("0.7000");
            default -> new BigDecimal("0.4500");
        };
    }

    private static String classificationTitle(CapabilityIngestionMatcher.MatchResult match) {
        if (match.classification() == IngestionMatchClassification.MANUAL_INPUT
                && match.manualInputLabel() != null) {
            return match.manualInputLabel();
        }
        return switch (match.classification()) {
            case MANUAL_REVIEW -> "Manual review required";
            case MANUAL_INPUT -> "Manual input required";
            case PRODUCT_CONFIG -> "Product / configuration";
            case DOCUMENT_REQUIREMENT -> "Document requirement";
            case DATA_REQUIREMENT, REPORT_FIELD -> "Data requirement";
            case METRIC_ADJUSTMENT -> "Metric adjustment";
            case PORTFOLIO_CONTROL -> "Portfolio control";
            case SERVICING_RULE -> "Servicing rule";
            case NARRATIVE -> "Narrative";
            case AMBIGUOUS -> "Needs clarification";
            default -> match.businessSummary() != null ? match.businessSummary() : match.classification().name();
        };
    }

    private static String classificationBadge(IngestionMatchClassification c) {
        return switch (c) {
            case MANUAL_REVIEW -> "Manual review";
            case MANUAL_INPUT -> "Manual input";
            case PRODUCT_CONFIG -> "Product / configuration";
            case DOCUMENT_REQUIREMENT -> "Document requirement";
            case DATA_REQUIREMENT, REPORT_FIELD -> "Data requirement";
            case METRIC_ADJUSTMENT -> "Metric adjustment";
            case PORTFOLIO_CONTROL -> "Portfolio control";
            case SERVICING_RULE -> "Servicing";
            case NARRATIVE -> "Narrative";
            case AMBIGUOUS -> "Ambiguous";
            case NEW_AUTOMATABLE_RULE -> "Policy rule";
            default -> "Classified";
        };
    }
}
