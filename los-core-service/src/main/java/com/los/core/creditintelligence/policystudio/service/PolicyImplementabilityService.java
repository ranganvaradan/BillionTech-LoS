package com.los.core.creditintelligence.policystudio.service;

import com.los.core.creditintelligence.decisionpolicy.DecisionPolicyDomain;
import com.los.core.creditintelligence.decisionpolicy.DecisionPolicyRuleMetadata;
import com.los.core.creditintelligence.decisionpolicy.DecisionPolicySectionReadiness;
import com.los.core.creditintelligence.decisionpolicy.kyc.DecisionPolicyDesignReadiness;
import com.los.core.creditintelligence.decisionpolicy.kyc.KycIntegrationRoutingProbe;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyAmbiguity;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyClause;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyInterpretation;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyMetricCandidate;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.validation.service.PolicyAuthoringRegistry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Deterministic Policy Implementability / Data Readiness analysis.
 * Derives availability only from {@link PolicyAuthoringRegistry}, session metric candidates,
 * and open ambiguities — never invents provider capabilities.
 */
@Service
public class PolicyImplementabilityService {

    public static final String READY = "READY";
    public static final String READY_WITH_FALLBACK = "READY_WITH_FALLBACK";
    public static final String MANUAL_INPUT_REQUIRED = "MANUAL_INPUT_REQUIRED";
    public static final String MANUAL_VERIFICATION = "MANUAL_VERIFICATION";
    public static final String DEFINITION_REQUIRED = "DEFINITION_REQUIRED";
    public static final String MAPPING_REQUIRED = "MAPPING_REQUIRED";
    public static final String DATA_SOURCE_REQUIRED = "DATA_SOURCE_REQUIRED";
    public static final String METRIC_REQUIRED = "METRIC_REQUIRED";
    public static final String RECONCILIATION_REQUIRED = "RECONCILIATION_REQUIRED";
    public static final String NOT_IMPLEMENTABLE = "NOT_IMPLEMENTABLE";
    // KYC-4 aliases / extensions (mapped into existing rollup)
    public static final String APPLICATION_INPUT_REQUIRED = DecisionPolicyDesignReadiness.APPLICATION_INPUT_REQUIRED;
    public static final String MATCH_CAPABILITY_REQUIRED = DecisionPolicyDesignReadiness.MATCH_CAPABILITY_REQUIRED;
    public static final String WORKFLOW_CONFIGURATION_REQUIRED = DecisionPolicyDesignReadiness.WORKFLOW_CONFIGURATION_REQUIRED;
    public static final String INTEGRATION_CONFIGURATION_REQUIRED = DecisionPolicyDesignReadiness.INTEGRATION_CONFIGURATION_REQUIRED;
    public static final String ENGINEERING_REQUIRED = DecisionPolicyDesignReadiness.ENGINEERING_REQUIRED;

    private final PolicyAuthoringRegistry registry;
    private final KycIntegrationRoutingProbe kycIntegrationRoutingProbe;

    public PolicyImplementabilityService(PolicyAuthoringRegistry registry) {
        this(registry, null);
    }

    public PolicyImplementabilityService(
            PolicyAuthoringRegistry registry,
            KycIntegrationRoutingProbe kycIntegrationRoutingProbe
    ) {
        this.registry = registry == null ? new PolicyAuthoringRegistry() : registry;
        this.kycIntegrationRoutingProbe = kycIntegrationRoutingProbe == null
                ? KycIntegrationRoutingProbe.designTimeOnly()
                : kycIntegrationRoutingProbe;
    }

    public PolicyImplementabilityService() {
        this(new PolicyAuthoringRegistry(), null);
    }

    public Map<String, Object> assess(PolicyStudioSession session) {
        Map<String, Object> reg = registry.registry();
        Map<String, Map<String, Object>> byCode = indexRegistry(reg);

        List<Map<String, Object>> ruleRows = new ArrayList<>();
        Set<String> allElements = new LinkedHashSet<>();
        Map<String, Set<String>> elementToRules = new LinkedHashMap<>();

        Map<UUID, CiPolicyInterpretation> interps = session.getInterpretations().stream()
                .filter(i -> i.getClauseId() != null)
                .collect(Collectors.toMap(CiPolicyInterpretation::getClauseId, i -> i, (a, b) -> a, LinkedHashMap::new));
        Map<UUID, CiPolicyClause> clauses = session.getClauses().stream()
                .filter(c -> c.getId() != null)
                .collect(Collectors.toMap(CiPolicyClause::getId, c -> c, (a, b) -> a, LinkedHashMap::new));

        Set<String> openPhrases = session.getAmbiguities().stream()
                .filter(a -> "OPEN".equals(a.getResolutionStatus())
                        || "CLARIFICATION_REQUESTED".equals(a.getResolutionStatus()))
                .map(a -> a.getPhrase() == null ? "" : a.getPhrase().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> openMissingMetricCodes = session.getAmbiguities().stream()
                .filter(a -> "OPEN".equals(a.getResolutionStatus()))
                .filter(a -> "MISSING_METRIC".equals(a.getAmbiguityType())
                        || "UNSUPPORTED_DATA".equals(a.getAmbiguityType())
                        || "UNKNOWN_BUSINESS_TERM".equals(a.getAmbiguityType())
                        || "BOUNDARY_AMBIGUITY".equals(a.getAmbiguityType()))
                .map(a -> a.getPhrase() == null ? "" : a.getPhrase().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, String> metricCandidateAvailability = new LinkedHashMap<>();
        for (CiPolicyMetricCandidate m : session.getMetricCandidates()) {
            String code = m.getCandidateCanonicalCode() == null ? m.getSystemMetricId() : m.getCandidateCanonicalCode();
            if (code == null) {
                continue;
            }
            Map<String, Object> meta = m.getMetadata() == null ? Map.of() : m.getMetadata();
            // metricCreated without executable calculation must NOT count as available
            if (Boolean.TRUE.equals(meta.get("metricCreated"))
                    && !BusinessMeasureDesignerService.isExecutableExpression(m.getExpression())) {
                metricCandidateAvailability.put(code, "UNAVAILABLE");
                continue;
            }
            if (BusinessMeasureDesignerService.isApprovedExecutableMeasure(m)) {
                metricCandidateAvailability.put(code, "AVAILABLE");
                continue;
            }
            if (meta.get("verificationMode") != null
                    && "MANUAL".equalsIgnoreCase(String.valueOf(meta.get("verificationMode")))) {
                metricCandidateAvailability.put(code, "CANDIDATE");
                continue;
            }
            Object av = meta.get("availability");
            if (av != null) {
                metricCandidateAvailability.put(code, String.valueOf(av));
            } else if (Boolean.TRUE.equals(meta.get("NEW_METRIC_CANDIDATE"))) {
                metricCandidateAvailability.put(code, "UNAVAILABLE");
            } else if (!BusinessMeasureDesignerService.isExecutableExpression(m.getExpression())) {
                metricCandidateAvailability.put(code, "UNAVAILABLE");
            }
        }

        int criticalTotal = 0;
        int criticalReady = 0;
        int criticalBlocked = 0;

        for (CiPolicyRuleCandidate rule : session.getRuleCandidates()) {
            // Ignored / deleted rules stay in the draft but never block activation or draft build.
            if (isExcludedFromActivation(rule)) {
                Map<String, Object> skipped = new LinkedHashMap<>();
                skipped.put("ruleId", rule.getId() == null ? null : rule.getId().toString());
                skipped.put("systemRuleId", rule.getSystemRuleId());
                skipped.put("ruleName", friendlyRuleName(rule.getSystemRuleId()));
                skipped.put("status", "EXCLUDED");
                skipped.put("disposition", dispositionOf(rule));
                skipped.put("excludedFromActivation", true);
                skipped.put("critical", false);
                skipped.put("requirements", List.of());
                ruleRows.add(skipped);
                continue;
            }
            CiPolicyInterpretation interp = interps.get(rule.getClauseId());
            CiPolicyClause clause = clauses.get(rule.getClauseId());
            List<String> paths = extractPaths(rule, interp);
            boolean critical = isCritical(rule);
            if (critical) {
                criticalTotal++;
            }

            List<Map<String, Object>> requirements = new ArrayList<>();
            List<String> statuses = new ArrayList<>();
            if (paths.isEmpty()) {
                Map<String, Object> req = elementView("__UNMAPPED__", byCode, metricCandidateAvailability,
                        openPhrases, openMissingMetricCodes, rule, true);
                req.put("status", MAPPING_REQUIRED);
                req.put("recommendedAction", "Complete executable mapping for this clause before implementation.");
                requirements.add(req);
                statuses.add(MAPPING_REQUIRED);
            } else {
                for (String path : paths) {
                    allElements.add(path);
                    elementToRules.computeIfAbsent(path, k -> new LinkedHashSet<>())
                            .add(rule.getSystemRuleId() == null ? rule.getId().toString() : rule.getSystemRuleId());
                    Map<String, Object> req = elementView(path, byCode, metricCandidateAvailability,
                            openPhrases, openMissingMetricCodes, rule, false);
                    requirements.add(req);
                    statuses.add(String.valueOf(req.get("status")));
                }
            }

            // Cross-source turnover variance detection
            boolean hasGst = paths.stream().anyMatch(p -> p.startsWith("gst."));
            boolean hasBankTurnover = paths.stream().anyMatch(p -> p.contains("turnover") && p.startsWith("bank"));
            if (hasGst && hasBankTurnover) {
                Map<String, Object> recon = new LinkedHashMap<>();
                recon.put("dataElementCode", "XSRC_GST_BANK_TURNOVER");
                recon.put("businessName", "GST vs Banking Turnover Cross-check");
                recon.put("category", "Financial");
                recon.put("status", registryHasRecon(reg, "XSRC_GST_BANK_TURNOVER") ? READY : RECONCILIATION_REQUIRED);
                recon.put("primarySource", "Cross-source reconciliation");
                recon.put("fallbackSources", List.of());
                recon.put("calculation", "TURNOVER_VARIANCE");
                requirements.add(recon);
                statuses.add(String.valueOf(recon.get("status")));
            }

            String ruleStatus = rollupStatus(statuses);
            if (isManualVerificationDesignated(rule)) {
                ruleStatus = MANUAL_VERIFICATION;
                for (Map<String, Object> req : requirements) {
                    String st = String.valueOf(req.get("status"));
                    if (isBlockedStatus(st) || MANUAL_INPUT_REQUIRED.equals(st)) {
                        req.put("status", MANUAL_VERIFICATION);
                        req.put("verificationMode", "MANUAL");
                        req.put("requiredEvidence", "Credit appraisal note / supporting document");
                        req.put("requiredActor", "Credit Manager");
                        req.put("outcome", "PASS / FAIL / REFER");
                        req.put("recommendedAction",
                                "Manual Credit Manager verification designated — remains visible in the policy package.");
                    }
                }
            }
            if (critical) {
                if (isBlockedStatus(ruleStatus)) {
                    criticalBlocked++;
                } else if (READY.equals(ruleStatus) || READY_WITH_FALLBACK.equals(ruleStatus)
                        || MANUAL_VERIFICATION.equals(ruleStatus)) {
                    criticalReady++;
                } else if (MANUAL_INPUT_REQUIRED.equals(ruleStatus)) {
                    // still implementable with manual capture
                    criticalReady++;
                } else {
                    criticalBlocked++;
                }
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ruleId", rule.getId() == null ? null : rule.getId().toString());
            row.put("systemRuleId", rule.getSystemRuleId());
            row.put("ruleName", friendlyRuleName(rule.getSystemRuleId()));
            row.put("businessRule", businessRuleText(rule, interp));
            row.put("ruleType", rule.getRuleType());
            row.put("critical", critical);
            row.put("criticalClass", criticalClass(rule));
            row.put("productScope", productScope(rule, clause, interp));
            row.put("onMissing", rule.getOnMissing());
            row.put("requirements", requirements);
            row.put("implementability", ruleStatus);
            row.put("automationClass", automationClass(ruleStatus));
            row.put("recommendedAction", recommendedActionForRule(ruleStatus, requirements));
            ruleRows.add(row);
        }

        Map<String, Long> byStatus = ruleRows.stream()
                .collect(Collectors.groupingBy(r -> String.valueOf(r.get("implementability")),
                        LinkedHashMap::new, Collectors.counting()));

        int total = ruleRows.size();
        long ready = byStatus.getOrDefault(READY, 0L) + byStatus.getOrDefault(READY_WITH_FALLBACK, 0L);
        long manual = byStatus.getOrDefault(MANUAL_INPUT_REQUIRED, 0L)
                + byStatus.getOrDefault(MANUAL_VERIFICATION, 0L);
        long blocked = total - ready - manual;
        if (blocked < 0) {
            blocked = 0;
        }

        // Weighted readiness: critical rules count 2x
        double weightTotal = 0;
        double weightReady = 0;
        for (Map<String, Object> r : ruleRows) {
            double w = Boolean.TRUE.equals(r.get("critical")) ? 2.0 : 1.0;
            weightTotal += w;
            String st = String.valueOf(r.get("implementability"));
            if (READY.equals(st) || READY_WITH_FALLBACK.equals(st)
                    || MANUAL_INPUT_REQUIRED.equals(st) || MANUAL_VERIFICATION.equals(st)) {
                weightReady += w;
            }
        }
        int percent = weightTotal <= 0 ? 0 : (int) Math.round(100.0 * weightReady / weightTotal);

        List<Map<String, Object>> gaps = buildGaps(ruleRows, elementToRules, byCode);
        List<Map<String, Object>> nextActions = buildNextActions(gaps, ruleRows);
        List<Map<String, Object>> matrix = buildMatrix(ruleRows);
        List<Map<String, Object>> sources = enrichSourceFamilies(byCode, allElements, metricCandidateAvailability);

        String band = percent >= 90 ? "READY"
                : percent >= 70 ? "MOSTLY READY"
                : percent >= 40 ? "NEEDS WORK"
                : "BLOCKED";

        boolean criticalBlockedFlag = criticalBlocked > 0;
        boolean draftBlockedByImplementability = criticalBlockedFlag;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("implementationReadinessPercent", percent);
        summary.put("readinessBand", band);
        summary.put("headline", headline(percent, total, ready, manual, blocked));
        summary.put("rulesIdentified", total);
        summary.put("ready", byStatus.getOrDefault(READY, 0L));
        summary.put("readyWithFallback", byStatus.getOrDefault(READY_WITH_FALLBACK, 0L));
        summary.put("needsClarification",
                byStatus.getOrDefault(DEFINITION_REQUIRED, 0L)
                        + byStatus.getOrDefault(MAPPING_REQUIRED, 0L));
        summary.put("manualVerification",
                byStatus.getOrDefault(MANUAL_INPUT_REQUIRED, 0L)
                        + byStatus.getOrDefault(MANUAL_VERIFICATION, 0L));
        summary.put("missingData",
                byStatus.getOrDefault(DATA_SOURCE_REQUIRED, 0L)
                        + byStatus.getOrDefault(METRIC_REQUIRED, 0L)
                        + byStatus.getOrDefault(RECONCILIATION_REQUIRED, 0L));
        summary.put("blocked", byStatus.getOrDefault(NOT_IMPLEMENTABLE, 0L)
                + (criticalBlockedFlag && byStatus.getOrDefault(NOT_IMPLEMENTABLE, 0L) == 0
                ? Math.max(0, blocked - byStatus.getOrDefault(NOT_IMPLEMENTABLE, 0L)) : 0));
        summary.put("statusCounts", byStatus);
        summary.put("automationCoveragePercent", total == 0 ? 0 : (int) Math.round(100.0 * ready / total));
        summary.put("manualReviewPercent", total == 0 ? 0 : (int) Math.round(100.0 * manual / total));
        summary.put("currentlyBlockedPercent", total == 0 ? 0 : (int) Math.round(100.0 * blocked / total));
        summary.put("distinctDataElements", allElements.size());
        summary.put("criticalRules", criticalTotal);
        summary.put("criticalFullyImplementable", criticalReady);
        summary.put("criticalBlocked", criticalBlocked);
        summary.put("draftBlockedByCriticalDataGap", draftBlockedByImplementability);
        summary.put("allowCanonicalAuthority", false);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", summary);
        out.put("rules", ruleRows);
        out.put("gaps", gaps);
        out.put("nextActions", nextActions);
        out.put("requirementMatrix", matrix);
        out.put("dataSources", sources);
        out.put("analystMessage", analystMessage(summary));
        out.put("applicationTimeContract", applicationTimeContractNote());
        out.put("policyImpactLabNote",
                "Alternate-policy comparison remains analytical (Portfolio Intelligence / Policy Impact Lab) — "
                        + "not part of normal application underwriting runtime.");
        out.put("packageStatusMapping", packageStatusMapping());
        // KYC-2: Decision Policy section readiness (honest registry signals; not production authority)
        out.put("decisionPolicySections", DecisionPolicySectionReadiness.sectionRollup(registry));
        out.put("kycDataReadiness", DecisionPolicySectionReadiness.kycElementReadiness(registry));
        out.put("allowCanonicalAuthority", false);
        new BusinessMeasureDesignerService(registry, null, null).enrichImplementability(out, session);
        // KYC-4: unify Decision Policy design readiness (workflow + integration + KYC domains)
        DecisionPolicyDesignReadiness.enrich(out, session, kycIntegrationRoutingProbe);
        return out;
    }

    private Map<String, Map<String, Object>> indexRegistry(Map<String, Object> reg) {
        Map<String, Map<String, Object>> by = new LinkedHashMap<>();
        for (String key : List.of("metrics", "facts")) {
            Object list = reg.get(key);
            if (list instanceof List<?> l) {
                for (Object o : l) {
                    if (o instanceof Map<?, ?> m) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> row = (Map<String, Object>) m;
                        by.put(String.valueOf(row.get("code")), row);
                    }
                }
            }
        }
        return by;
    }

    private boolean registryHasRecon(Map<String, Object> reg, String code) {
        Object list = reg.get("reconciliations");
        if (list instanceof List<?> l) {
            return l.stream().anyMatch(x -> code.equals(String.valueOf(x)));
        }
        return false;
    }

    private Map<String, Object> elementView(
            String path,
            Map<String, Map<String, Object>> byCode,
            Map<String, String> metricCandidateAvailability,
            Set<String> openPhrases,
            Set<String> openMissingMetricPhrases,
            CiPolicyRuleCandidate rule,
            boolean unmapped) {

        Map<String, Object> catalog = BusinessDataSourceCatalog.describe(path);
        Map<String, Object> reg = byCode.get(path);

        String registryAvailability = reg == null ? null : String.valueOf(reg.get("availability"));
        if (metricCandidateAvailability.containsKey(path)) {
            registryAvailability = metricCandidateAvailability.get(path);
        }

        String status;
        String recommendedAction;
        boolean fallbackCompatible = false;

        if (unmapped) {
            status = MAPPING_REQUIRED;
            recommendedAction = "Map this clause to executable metrics/facts before implementation.";
        } else if (definitionBlocked(path, openPhrases, openMissingMetricPhrases, rule)
                && !"AVAILABLE".equalsIgnoreCase(registryAvailability)) {
            status = DEFINITION_REQUIRED;
            recommendedAction = "Credit Head must resolve the related ambiguous business term before this rule can be implemented.";
        } else if (reg == null && !path.startsWith("application.")
                && !"AVAILABLE".equalsIgnoreCase(registryAvailability)) {
            status = MAPPING_REQUIRED;
            recommendedAction = "No registry definition found for this path — complete metric/fact mapping.";
        } else if ("UNAVAILABLE".equalsIgnoreCase(registryAvailability)) {
            // Settlement/QR is bound to PolicyBankingMetricService helpers — treat as derivable when coded settlement.*
            if (path != null && path.contains("settlement")) {
                status = "READY";
                recommendedAction = "Derived from QR settlement credits (trailing 3m) via banking helper — not unavailable.";
                fallbackCompatible = true;
            } else {
                status = path.contains("overdue.age")
                        || path.contains("credit_after_overdue") || path.contains("clean_history")
                        ? METRIC_REQUIRED
                        : DATA_SOURCE_REQUIRED;
                recommendedAction = recommendedForMissing(path, status);
                // Fallback listed but unavailable primary — still not READY_WITH_FALLBACK unless fallback proven available
                fallbackCompatible = false;
            }
        } else if ("CANDIDATE".equalsIgnoreCase(registryAvailability)
                || path.startsWith("application.")
                || Boolean.TRUE.equals(catalog.get("manualCaptureAllowed"))) {
            if (path.contains("proposed_edi") || "CANDIDATE".equalsIgnoreCase(registryAvailability)) {
                status = MANUAL_INPUT_REQUIRED;
                recommendedAction = "Capture " + catalog.get("businessName") + " on the application form.";
            } else if (looksLikeManualAppraisal(path, rule)) {
                status = MANUAL_VERIFICATION;
                recommendedAction = "Designate Credit Manager manual verification with supporting evidence.";
            } else {
                status = MANUAL_INPUT_REQUIRED;
                recommendedAction = "Capture this value in the application journey.";
            }
        } else if ("AVAILABLE".equalsIgnoreCase(registryAvailability)) {
            // Primary available — fallback is for outage only, not dual-fetch
            List<?> fallbacks = (List<?>) catalog.get("fallbackSources");
            if (fallbacks != null && !fallbacks.isEmpty()) {
                status = READY; // primary available; fallback exists for failure — not "with fallback" unless primary down
                // Spec wants READY_WITH_FALLBACK when primary unavailable + fallback available.
                // Here primary IS available → READY.
            } else {
                status = READY;
            }
            recommendedAction = "No action — primary source can supply this element.";
            fallbackCompatible = fallbacks != null && !fallbacks.isEmpty();
        } else {
            status = DATA_SOURCE_REQUIRED;
            recommendedAction = "Configure a primary source for " + catalog.get("businessName") + ".";
        }

        // Manual verification heuristic for industry experience style rules without paths
        if (looksLikeManualAppraisal(path, rule) && READY.equals(status)) {
            // keep READY if data exists
        }

        Map<String, Object> out = new LinkedHashMap<>(catalog);
        out.put("registryAvailability", registryAvailability == null ? "UNKNOWN" : registryAvailability);
        out.put("status", status);
        out.put("fallbackCompatible", fallbackCompatible);
        out.put("primarySourceAvailable", "AVAILABLE".equalsIgnoreCase(registryAvailability));
        out.put("fallbackAvailable", false); // do not invent fallback availability
        out.put("noSourceAvailable", "UNAVAILABLE".equalsIgnoreCase(registryAvailability)
                || registryAvailability == null && !path.startsWith("application."));
        out.put("manualCapturePossible", Boolean.TRUE.equals(catalog.get("manualCaptureAllowed"))
                || MANUAL_INPUT_REQUIRED.equals(status) || MANUAL_VERIFICATION.equals(status));
        out.put("calculation", calculationHint(path, rule));
        out.put("recommendedAction", recommendedAction);
        if (reg != null) {
            out.put("dataType", reg.get("type"));
            out.put("unit", reg.get("unit"));
            out.put("periodRequirement", reg.get("periodSemantics"));
            out.put("canonicalPath", reg.get("canonicalPath"));
        }
        return out;
    }

    private boolean definitionBlocked(
            String path,
            Set<String> openPhrases,
            Set<String> openMissing,
            CiPolicyRuleCandidate rule) {
        String sys = rule.getSystemRuleId() == null ? "" : rule.getSystemRuleId().toUpperCase(Locale.ROOT);
        String p = path.toLowerCase(Locale.ROOT);
        if (openPhrases.stream().anyMatch(x -> x.contains("edi"))
                && (p.contains("proposed_edi") || sys.contains("EDI"))) {
            return true;
        }
        if (openPhrases.stream().anyMatch(x -> x.contains("clean"))
                && (p.contains("clean") || sys.contains("CLEAN") || sys.contains("OVERDUE"))) {
            return true;
        }
        if (openPhrases.stream().anyMatch(x -> x.contains("ntc")) && (p.contains("ntc") || sys.contains("NTC"))) {
            return true;
        }
        if (openPhrases.stream().anyMatch(x -> x.contains("exactly 100") || x.contains("100 transaction"))
                && sys.contains("INWARD")) {
            return true;
        }
        // Settlement/QR is derivable — do not treat open settlement phrases as a missing metric.
        if (openPhrases.stream().anyMatch(x -> x.contains("deposition") || x.contains("deposit"))
                && (p.contains("deposit") || sys.contains("BULK"))) {
            return true;
        }
        if (openMissing.stream().anyMatch(x ->
                (x.contains("overdue") && p.contains("overdue"))
                        || (x.contains("credit after") && p.contains("credit_after"))
                        || (x.contains("clean history") && p.contains("clean_history"))
                        || (x.contains("settlement") && p.contains("settlement")))) {
            return true;
        }
        return false;
    }

    private boolean looksLikeManualAppraisal(String path, CiPolicyRuleCandidate rule) {
        String sys = rule.getSystemRuleId() == null ? "" : rule.getSystemRuleId().toLowerCase(Locale.ROOT);
        String p = path == null ? "" : path.toLowerCase(Locale.ROOT);
        return p.contains("experience") || p.contains("promoter") || sys.contains("EXPERIENCE") || sys.contains("PROMOTER");
    }

    /** Explicit Credit Manager designation — manual must remain visible, never silent ignore. */
    private boolean isManualVerificationDesignated(CiPolicyRuleCandidate rule) {
        Map<String, Object> meta = rule.getMetadata();
        if (meta == null || meta.isEmpty()) {
            return false;
        }
        Object mode = meta.get("verificationMode");
        if (mode != null && "MANUAL".equalsIgnoreCase(String.valueOf(mode))) {
            return true;
        }
        Object disposition = meta.get("dataGapDisposition");
        if (disposition != null && "MANUAL_VERIFICATION".equalsIgnoreCase(String.valueOf(disposition))) {
            return true;
        }
        Object d = meta.get("disposition");
        return d != null && ("MANUAL_INPUT".equalsIgnoreCase(String.valueOf(d))
                || "MANUAL_REVIEW".equalsIgnoreCase(String.valueOf(d)));
    }

    /** Credit Manager Ignore / Delete — retained in draft, never an activation blocker. */
    public static boolean isExcludedFromActivation(CiPolicyRuleCandidate rule) {
        Map<String, Object> meta = rule.getMetadata();
        if (meta == null || meta.isEmpty()) {
            return false;
        }
        if (Boolean.TRUE.equals(meta.get("excludedFromActivation")) || Boolean.TRUE.equals(meta.get("deleted"))) {
            return true;
        }
        Object d = meta.get("disposition");
        if (d == null) {
            return false;
        }
        String ds = String.valueOf(d).toUpperCase(Locale.ROOT);
        return "IGNORED".equals(ds) || "DELETED".equals(ds) || "EXCLUDED".equals(ds);
    }

    private static String dispositionOf(CiPolicyRuleCandidate rule) {
        Map<String, Object> meta = rule.getMetadata();
        if (meta == null || meta.get("disposition") == null) {
            return null;
        }
        return String.valueOf(meta.get("disposition"));
    }

    private String calculationHint(String path, CiPolicyRuleCandidate rule) {
        String sys = rule.getSystemRuleId() == null ? "" : rule.getSystemRuleId();
        if (sys.contains("ADB") && sys.contains("EDI")) {
            return "ADB / EDI (capacity ratio)";
        }
        if (sys.contains("INWARD") && sys.contains("100")) {
            return "Boundary branch on transaction count vs return ratio/count";
        }
        if (path != null && path.contains("turnover")) {
            return "Period turnover aggregation";
        }
        return null;
    }

    private String recommendedForMissing(String path, String status) {
        String name = BusinessDataSourceCatalog.businessName(path);
        if (METRIC_REQUIRED.equals(status)) {
            return "Define and implement metric " + name + ", or revise the policy rule.";
        }
        return "Configure a primary data source for " + name
                + ", add an application field, mark manual Credit Manager verification, or modify/remove the rule.";
    }

    private String rollupStatus(List<String> statuses) {
        if (statuses.isEmpty()) {
            return MAPPING_REQUIRED;
        }
        List<String> priority = List.of(
                NOT_IMPLEMENTABLE,
                ENGINEERING_REQUIRED,
                MATCH_CAPABILITY_REQUIRED,
                INTEGRATION_CONFIGURATION_REQUIRED,
                WORKFLOW_CONFIGURATION_REQUIRED,
                DATA_SOURCE_REQUIRED,
                METRIC_REQUIRED,
                RECONCILIATION_REQUIRED,
                APPLICATION_INPUT_REQUIRED,
                DEFINITION_REQUIRED,
                MAPPING_REQUIRED,
                MANUAL_VERIFICATION,
                MANUAL_INPUT_REQUIRED,
                READY_WITH_FALLBACK,
                READY);
        for (String p : priority) {
            if (statuses.contains(p)) {
                return p;
            }
        }
        return statuses.get(0);
    }

    private boolean isBlockedStatus(String status) {
        return DecisionPolicyDesignReadiness.isBlockedDesignStatus(status);
    }

    private String automationClass(String status) {
        if (READY.equals(status) || READY_WITH_FALLBACK.equals(status)) {
            return "AUTOMATABLE";
        }
        if (MANUAL_INPUT_REQUIRED.equals(status) || MANUAL_VERIFICATION.equals(status)) {
            return "MANUAL";
        }
        return "BLOCKED";
    }

    private boolean isCritical(CiPolicyRuleCandidate rule) {
        DecisionPolicyDomain domain = DecisionPolicyRuleMetadata.domainOf(rule.getMetadata(), rule.getScope());
        if (domain.isKycOrEligibility()) {
            // KYC-4: mandatory verification / platform guardrail / HARD KYC — not every informational fact
            Map<String, Object> meta = rule.getMetadata() == null ? Map.of() : rule.getMetadata();
            if ("PLATFORM_GUARDRAIL".equalsIgnoreCase(String.valueOf(meta.get("guardrailClass")))) {
                return true;
            }
            String req = String.valueOf(meta.getOrDefault("kycRequirementType", ""));
            if ("VERIFICATION".equals(req) || "COMPLETION_REQUIREMENT".equals(req)
                    || "REGULATORY_GUARDRAIL".equals(req)) {
                return true;
            }
            String type = rule.getRuleType() == null ? "" : rule.getRuleType().toUpperCase(Locale.ROOT);
            return "KNOCKOUT".equals(type) || "HARD".equals(type) || "HARD_RULE".equals(type);
        }
        String type = rule.getRuleType() == null ? "" : rule.getRuleType().toUpperCase(Locale.ROOT);
        if ("KNOCKOUT".equals(type) || "HARD".equals(type) || "CRITICAL".equals(type)) {
            return true;
        }
        String sys = rule.getSystemRuleId() == null ? "" : rule.getSystemRuleId().toUpperCase(Locale.ROOT);
        return sys.contains("SCORE") || sys.contains("DPD") || sys.contains("WRITEOFF")
                || sys.contains("DBT") || sys.contains("LEGAL") || sys.contains("INWARD")
                || sys.contains("ADB") || sys.contains("EDI");
    }

    private String criticalClass(CiPolicyRuleCandidate rule) {
        String type = rule.getRuleType() == null ? "" : rule.getRuleType().toUpperCase(Locale.ROOT);
        if ("KNOCKOUT".equals(type)) {
            return "Knockout";
        }
        if ("HARD".equals(type)) {
            return "Hard Eligibility";
        }
        if (isCritical(rule)) {
            return "Decision Critical";
        }
        return "Informational";
    }

    @SuppressWarnings("unchecked")
    private List<String> extractPaths(CiPolicyRuleCandidate rule, CiPolicyInterpretation interp) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (interp != null && interp.getCandidateInputs() != null) {
            for (Object o : interp.getCandidateInputs()) {
                out.add(String.valueOf(o));
            }
        }
        if (out.isEmpty() && rule.getExpression() != null) {
            collectPaths(rule.getExpression(), out);
        }
        return new ArrayList<>(out);
    }

    @SuppressWarnings("unchecked")
    private void collectPaths(Object node, Collection<String> out) {
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> m = (Map<String, Object>) map;
            for (String key : List.of("metric", "fact", "applicationField", "path", "reconciliation")) {
                if (m.get(key) != null) {
                    out.add(String.valueOf(m.get(key)));
                }
            }
            for (Object v : m.values()) {
                collectPaths(v, out);
            }
        } else if (node instanceof List<?> list) {
            for (Object v : list) {
                collectPaths(v, out);
            }
        }
    }

    private List<Map<String, Object>> buildGaps(
            List<Map<String, Object>> rules,
            Map<String, Set<String>> elementToRules,
            Map<String, Map<String, Object>> byCode) {
        Map<String, Map<String, Object>> gapByKey = new LinkedHashMap<>();
        for (Map<String, Object> rule : rules) {
            boolean critical = Boolean.TRUE.equals(rule.get("critical"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> reqs = (List<Map<String, Object>>) rule.get("requirements");
            if (reqs == null) {
                continue;
            }
            for (Map<String, Object> req : reqs) {
                String status = String.valueOf(req.get("status"));
                if (READY.equals(status) || READY_WITH_FALLBACK.equals(status)) {
                    continue;
                }
                String code = String.valueOf(req.get("dataElementCode"));
                String gapType = gapType(status);
                String key = gapType + "|" + code;
                Map<String, Object> gap = gapByKey.computeIfAbsent(key, k -> {
                    Map<String, Object> g = new LinkedHashMap<>();
                    g.put("gapType", gapType);
                    g.put("whatIsMissing", req.get("businessName"));
                    g.put("dataElementCode", code);
                    g.put("status", status);
                    g.put("configuredSource", req.get("primarySource"));
                    g.put("options", resolutionOptions(status));
                    g.put("dependentRules", new ArrayList<String>());
                    g.put("affectedProducts", new LinkedHashSet<String>());
                    g.put("blocksPolicy", false);
                    g.put("recommendedResolution", req.get("recommendedAction"));
                    return g;
                });
                @SuppressWarnings("unchecked")
                List<String> deps = (List<String>) gap.get("dependentRules");
                deps.add(String.valueOf(rule.get("systemRuleId")));
                @SuppressWarnings("unchecked")
                Set<String> products = (Set<String>) gap.get("affectedProducts");
                products.add(String.valueOf(rule.get("productScope")));
                if (critical && isBlockedStatus(status)) {
                    gap.put("blocksPolicy", true);
                }
            }
        }
        List<Map<String, Object>> gaps = new ArrayList<>();
        for (Map<String, Object> g : gapByKey.values()) {
            @SuppressWarnings("unchecked")
            Set<String> products = (Set<String>) g.get("affectedProducts");
            g.put("affectedProducts", new ArrayList<>(products));
            @SuppressWarnings("unchecked")
            List<String> deps = (List<String>) g.get("dependentRules");
            g.put("dependentRuleCount", deps.size());
            gaps.add(g);
        }
        return gaps;
    }

    private String gapType(String status) {
        return switch (status) {
            case MANUAL_INPUT_REQUIRED -> "Missing Application Field";
            case MANUAL_VERIFICATION -> "Manual Verification";
            case DEFINITION_REQUIRED -> "Missing Business Definition";
            case METRIC_REQUIRED -> "Missing Metric";
            case MAPPING_REQUIRED -> "Missing Mapping";
            case RECONCILIATION_REQUIRED -> "Missing Reconciliation";
            case READY_WITH_FALLBACK -> "Unavailable Fallback";
            default -> "Missing Data Source";
        };
    }

    private List<String> resolutionOptions(String status) {
        if (MANUAL_VERIFICATION.equals(status) || MANUAL_INPUT_REQUIRED.equals(status)) {
            return List.of(
                    "Add to application form",
                    "Integrate a source",
                    "Mark as manual Credit Manager verification",
                    "Modify/remove the policy rule");
        }
        if (DEFINITION_REQUIRED.equals(status)) {
            return List.of(
                    "Resolve ambiguous term in Ambiguous Terms",
                    "Modify/remove the policy rule");
        }
        return List.of(
                "Add to application form",
                "Integrate / configure a source",
                "Mark as manual Credit Manager verification",
                "Modify/remove the policy rule");
    }

    private List<Map<String, Object>> buildNextActions(List<Map<String, Object>> gaps, List<Map<String, Object>> rules) {
        List<Map<String, Object>> actions = new ArrayList<>();
        int n = 0;
        for (Map<String, Object> gap : gaps) {
            if (n >= 5) {
                break;
            }
            if (!Boolean.TRUE.equals(gap.get("blocksPolicy"))
                    && !"Missing Data Source".equals(gap.get("gapType"))
                    && !"Missing Metric".equals(gap.get("gapType"))
                    && !"Missing Business Definition".equals(gap.get("gapType"))
                    && !"Missing Application Field".equals(gap.get("gapType"))) {
                continue;
            }
            n++;
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("sequence", n);
            a.put("title", gap.get("whatIsMissing"));
            a.put("detail", "Required by " + gap.get("dependentRuleCount") + " policy rule(s). "
                    + "Configured source: " + gap.get("configuredSource") + ".");
            a.put("recommended", gap.get("recommendedResolution"));
            a.put("options", gap.get("options"));
            a.put("blocksPolicy", gap.get("blocksPolicy"));
            actions.add(a);
        }
        if (actions.isEmpty() && !gaps.isEmpty()) {
            Map<String, Object> gap = gaps.get(0);
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("sequence", 1);
            a.put("title", gap.get("whatIsMissing"));
            a.put("detail", "Required by " + gap.get("dependentRuleCount") + " policy rule(s).");
            a.put("recommended", gap.get("recommendedResolution"));
            a.put("options", gap.get("options"));
            a.put("blocksPolicy", gap.get("blocksPolicy"));
            actions.add(a);
        }
        return actions;
    }

    private List<Map<String, Object>> buildMatrix(List<Map<String, Object>> rules) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> rule : rules) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> reqs = (List<Map<String, Object>>) rule.get("requirements");
            if (reqs == null) {
                continue;
            }
            for (Map<String, Object> req : reqs) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("policyRequirement", rule.get("ruleName"));
                row.put("businessRule", rule.get("businessRule"));
                row.put("dataNeeded", req.get("businessName"));
                row.put("dataElementCode", req.get("dataElementCode"));
                row.put("primarySource", req.get("primarySource"));
                row.put("fallback", req.get("fallbackSources"));
                row.put("availability", req.get("registryAvailability"));
                row.put("status", req.get("status"));
                row.put("category", req.get("category"));
                row.put("critical", rule.get("critical"));
                row.put("productScope", rule.get("productScope"));
                rows.add(row);
            }
        }
        return rows;
    }

    private List<Map<String, Object>> enrichSourceFamilies(
            Map<String, Map<String, Object>> byCode,
            Set<String> used,
            Map<String, String> metricCandidateAvailability) {
        List<Map<String, Object>> families = BusinessDataSourceCatalog.configuredSourceFamilies();
        for (Map<String, Object> f : families) {
            String cat = String.valueOf(f.get("category"));
            boolean anyUnavailable = used.stream()
                    .filter(p -> cat.equals(BusinessDataSourceCatalog.category(p)))
                    .anyMatch(p -> {
                        String av = metricCandidateAvailability.get(p);
                        if (av == null && byCode.get(p) != null) {
                            av = String.valueOf(byCode.get(p).get("availability"));
                        }
                        return "UNAVAILABLE".equalsIgnoreCase(av);
                    });
            boolean anyUsed = used.stream().anyMatch(p -> cat.equals(BusinessDataSourceCatalog.category(p)));
            f.put("availability", !anyUsed ? "Configured"
                    : (anyUnavailable ? "Partial — some required elements unavailable" : "Available"));
            f.put("usedByPolicy", anyUsed);
        }
        return families;
    }

    private String headline(int percent, int total, long ready, long manual, long blocked) {
        return percent + "% — " + ready + " of " + total
                + " policy rules can be executed using currently configured data"
                + (manual > 0 ? "; " + manual + " require manual input/verification" : "")
                + (blocked > 0 ? "; " + blocked + " currently blocked" : "")
                + ".";
    }

    private String analystMessage(Map<String, Object> summary) {
        long ready = ((Number) summary.get("ready")).longValue()
                + ((Number) summary.get("readyWithFallback")).longValue();
        long clarify = ((Number) summary.get("needsClarification")).longValue();
        long manual = ((Number) summary.get("manualVerification")).longValue();
        long missing = ((Number) summary.get("missingData")).longValue();
        int rules = ((Number) summary.get("rulesIdentified")).intValue();
        int elements = ((Number) summary.get("distinctDataElements")).intValue();
        StringBuilder sb = new StringBuilder();
        sb.append("I've now checked the policy against your configured data sources. ");
        sb.append("We identified ").append(rules).append(" rules requiring ")
                .append(elements).append(" distinct business data elements. ");
        sb.append("Your configured data sources can execute ").append(ready).append(" rules automatically. ");
        if (manual > 0) {
            sb.append(manual).append(" rule(s) require manual verification or application capture. ");
        }
        if (clarify > 0) {
            sb.append(clarify).append(" rule(s) need clarification. ");
        }
        if (missing > 0) {
            sb.append(missing).append(" rule(s) cannot currently be implemented because required data is unavailable. ");
        }
        if (clarify + missing + manual == 0) {
            sb.append("Most of the policy can be automated with the current configuration.");
        } else {
            sb.append("I found ").append(clarify + missing + Math.min(manual, 3))
                    .append(" area(s) requiring your attention before implementation.");
        }
        return sb.toString().trim();
    }

    private String recommendedActionForRule(String status, List<Map<String, Object>> requirements) {
        for (Map<String, Object> r : requirements) {
            if (!READY.equals(String.valueOf(r.get("status")))
                    && !READY_WITH_FALLBACK.equals(String.valueOf(r.get("status")))) {
                return String.valueOf(r.get("recommendedAction"));
            }
        }
        return "No action required for this rule.";
    }

    private Map<String, Object> applicationTimeContractNote() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "DOCUMENTED_NOT_ACTIVATED");
        m.put("summary", "Future production contract (not activated): Application → applicable approved policy → "
                + "required data elements → primary/fallback source → metrics/reconciliations → "
                + "evaluate ONE policy → recommendation / manual review / missing information → human workflow.");
        m.put("normalProcessingComparesMultiplePolicies", false);
        return m;
    }

    private Map<String, Object> packageStatusMapping() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("intended", List.of("DRAFT", "UNDER_REVIEW", "APPROVED", "ACTIVE", "SUPERSEDED", "RETIRED"));
        m.put("existingDocumentStatus", "DocumentStatus / ReviewState / DraftPackageStatus DRAFT_ONLY");
        m.put("existingExecutablePackageStatus",
                "DRAFT, REVIEW, APPROVED, SHADOW, ACTIVE, SUPERSEDED, RETIRED");
        m.put("note", "No enum rename in Day 6 — map intended labels onto existing package states.");
        return m;
    }

    private String friendlyRuleName(String systemRuleId) {
        if (systemRuleId == null || systemRuleId.isBlank()) {
            return "Generated rule";
        }
        return switch (systemRuleId) {
            case "BANK_STARTER_ADB_GTE_EDI" -> "STARTER — Banking Capacity";
            case "BANK_DIGILEAP_TXN_GTE_20" -> "DIGILEAP — Transaction Volume";
            case "BANK_DIGILEAP_ADB_DIV5_GTE_EDI" -> "DIGILEAP — Banking Capacity";
            case "BANK_SMART_SWITCH_SETTLEMENT_COUNT_GTE_20" -> "SMART SWITCH — Settlement Count";
            case "BANK_SMART_SWITCH_SETTLEMENT_DIV10_GTE_EDI" -> "SMART SWITCH — Settlement Capacity";
            case "BANK_REBOOST_TXN_GTE_30_IF_AMT_GT_60000" -> "REBOOST — Transaction Volume";
            case "BANK_REBOOST_ADB_DIV5_GTE_EDI_IF_AMT_GT_60000" -> "REBOOST — Banking Capacity";
            case "BANK_INWARD_RETURN_BRANCHED_100" -> "Inward Cheque Returns — 100 Boundary";
            case "BUREAU_SCORE_OR_NTC_OR_GTE_650" -> "Minimum Bureau Score / NTC";
            default -> systemRuleId.replace('_', ' ');
        };
    }

    private String businessRuleText(CiPolicyRuleCandidate r, CiPolicyInterpretation interp) {
        if (interp != null && interp.getNaturalLanguageMeaning() != null
                && !interp.getNaturalLanguageMeaning().isBlank()) {
            return interp.getNaturalLanguageMeaning();
        }
        return friendlyRuleName(r.getSystemRuleId());
    }

    private String productScope(CiPolicyRuleCandidate r, CiPolicyClause clause, CiPolicyInterpretation interp) {
        if (clause != null && clause.getProductScope() != null && !clause.getProductScope().isBlank()) {
            return clause.getProductScope();
        }
        if (interp != null && interp.getCandidateProductScope() != null
                && !interp.getCandidateProductScope().isEmpty()) {
            return interp.getCandidateProductScope().stream().map(String::valueOf)
                    .collect(Collectors.joining(", "));
        }
        Object scope = r.getScope() == null ? null : r.getScope().get("products");
        if (scope instanceof List<?> list && !list.isEmpty()) {
            return list.stream().map(String::valueOf).collect(Collectors.joining(", "));
        }
        return "All applicable products";
    }
}
