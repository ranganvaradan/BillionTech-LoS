package com.los.core.creditintelligence.decisionpolicy.kyc;

import com.los.core.creditintelligence.decisionpolicy.DecisionPolicyDomain;
import com.los.core.creditintelligence.decisionpolicy.DecisionPolicyRuleMetadata;
import com.los.core.creditintelligence.decisionpolicy.DecisionPolicyType;
import com.los.core.creditintelligence.decisionpolicy.KycRequirementType;
import com.los.core.creditintelligence.decisionpolicy.PolicyGuardrailClass;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.service.BusinessDataSourceCatalog;
import com.los.core.creditintelligence.policystudio.service.PolicyImplementabilityService;
import com.los.core.model.enums.KycStepType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
/**
 * KYC-4: Decision Policy design-time readiness rollup.
 * Extends {@link PolicyImplementabilityService} — not a separate readiness engine.
 * Assesses policy requirements vs workflow capability vs integration evidence.
 * Does not activate production KYC / underwriting / canonical authority.
 */
public final class DecisionPolicyDesignReadiness {

    public static final String APPLICATION_INPUT_REQUIRED = "APPLICATION_INPUT_REQUIRED";
    public static final String MATCH_CAPABILITY_REQUIRED = "MATCH_CAPABILITY_REQUIRED";
    public static final String WORKFLOW_CONFIGURATION_REQUIRED = "WORKFLOW_CONFIGURATION_REQUIRED";
    public static final String INTEGRATION_CONFIGURATION_REQUIRED = "INTEGRATION_CONFIGURATION_REQUIRED";
    public static final String PLATFORM_GUARDRAIL = "PLATFORM_GUARDRAIL";
    public static final String ENGINEERING_REQUIRED = "ENGINEERING_REQUIRED";
    public static final String BUSINESS_DEFINITION_REQUIRED = "BUSINESS_DEFINITION_REQUIRED";
    public static final String MANUAL_CONFIGURATION_REQUIRED = "MANUAL_CONFIGURATION_REQUIRED";

    public static final String OVERALL_IMPLEMENTATION_READY = "IMPLEMENTATION READY";
    public static final String OVERALL_NEEDS_ATTENTION = "NEEDS ATTENTION";
    public static final String OVERALL_BLOCKED = "BLOCKED";

    private DecisionPolicyDesignReadiness() {}

    public static void enrich(
            Map<String, Object> assessOut,
            PolicyStudioSession session,
            KycIntegrationRoutingProbe probe
    ) {
        if (assessOut == null || session == null) {
            return;
        }
        KycIntegrationRoutingProbe routing = probe == null
                ? KycIntegrationRoutingProbe.designTimeOnly()
                : probe;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rules = (List<Map<String, Object>>) assessOut.get("rules");
        if (rules == null) {
            rules = List.of();
        }

        // Overlay KYC-aware statuses onto requirement rows
        for (Map<String, Object> ruleRow : rules) {
            overlayKycRequirementStatuses(ruleRow, session, routing);
        }

        List<Map<String, Object>> kycRequirements = buildKycRequirementRows(rules, session, routing);
        List<Map<String, Object>> domainCards = buildDomainCards(rules, kycRequirements, session);
        Map<String, Object> overall = buildOverall(domainCards, rules, session);
        List<Map<String, Object>> nextActions = buildDecisionPolicyNextActions(kycRequirements, rules, assessOut);
        List<Map<String, Object>> consolidatedSources = buildConsolidatedSources(rules, kycRequirements);

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) assessOut.get("summary");
        if (summary == null) {
            summary = new LinkedHashMap<>();
            assessOut.put("summary", summary);
        }

        boolean hasKycRequirements = !kycRequirements.isEmpty();
        boolean decisionPolicy = isDecisionPolicySession(session) || hasKycRequirements;

        // Critical KYC gaps block Decision Policy / KYC-domain draft readiness only —
        // do not redefine credit-only CREDIT_POLICY draft gates unexpectedly.
        long criticalKycBlocked = kycRequirements.stream()
                .filter(r -> Boolean.TRUE.equals(r.get("critical")))
                .filter(r -> isBlockedDesignStatus(String.valueOf(r.get("status"))))
                .count();
        summary.put("draftBlockedByCriticalKycGap", hasKycRequirements && criticalKycBlocked > 0);
        summary.put("criticalKycBlocked", criticalKycBlocked);
        if (hasKycRequirements && criticalKycBlocked > 0) {
            summary.put("draftBlockedByCriticalDataGap", true);
        }

        // Prefer Decision Policy overall band only when KYC requirements are present
        // or the session is explicitly a Decision / KYC policy sample.
        if (decisionPolicy && hasKycRequirements) {
            String overallStatus = String.valueOf(overall.get("status"));
            summary.put("decisionPolicyReadinessStatus", overallStatus);
            summary.put("readinessBand", mapOverallToBand(overallStatus));
            summary.put("headline", overall.get("headline"));
            Object pct = overall.get("implementationReadinessPercent");
            if (pct instanceof Number) {
                summary.put("implementationReadinessPercent", ((Number) pct).intValue());
            }
        }

        summary.put("allowCanonicalAuthority", false);
        summary.put("readinessKind", "POLICY_DESIGN_READINESS");
        summary.put("runtimeHealthDistinct", true);

        assessOut.put("decisionPolicyDomainReadiness", domainCards);
        assessOut.put("kycRequirementReadiness", kycRequirements);
        assessOut.put("decisionPolicyOverall", overall);
        assessOut.put("decisionPolicyDataSources", consolidatedSources);
        if (hasKycRequirements && !nextActions.isEmpty()) {
            assessOut.put("nextActions", nextActions);
        }
        if (hasKycRequirements) {
            assessOut.put("analystMessage",
                    buildAnalystMessage(overall, domainCards, kycRequirements, true));
        }
        assessOut.put("designTimeVsRuntimeNote",
                "Policy design readiness uses configured capabilities and provider implementations. "
                        + "Temporary provider outage is a runtime operational issue and does not redefine design implementability.");
        assessOut.put("allowCanonicalAuthority", false);

        // Keep KYC-2 section keys populated from domain cards when present
        if (!domainCards.isEmpty()) {
            assessOut.put("decisionPolicySections", domainCards);
        }
    }

    static void overlayKycRequirementStatuses(
            Map<String, Object> ruleRow,
            PolicyStudioSession session,
            KycIntegrationRoutingProbe routing
    ) {
        CiPolicyRuleCandidate rule = findRule(session, ruleRow);
        if (rule == null) {
            return;
        }
        DecisionPolicyDomain domain = DecisionPolicyRuleMetadata.domainOf(rule.getMetadata(), rule.getScope());
        if (!domain.isKycOrEligibility()) {
            return;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reqs = (List<Map<String, Object>>) ruleRow.get("requirements");
        if (reqs == null) {
            return;
        }
        List<String> statuses = new ArrayList<>();
        for (Map<String, Object> req : reqs) {
            String path = String.valueOf(req.get("dataElementCode"));
            Map<String, Object> assessed = assessKycPath(path, rule, routing);
            req.putAll(assessed);
            statuses.add(String.valueOf(assessed.get("status")));
        }
        String rollup = rollupKycStatuses(statuses);
        // Manual verification designation on rule
        if (isGovernedManual(rule) && !statuses.stream().allMatch(DecisionPolicyDesignReadiness::isBlockedDesignStatus)) {
            if (statuses.stream().anyMatch(s -> MANUAL_CONFIGURATION_REQUIRED.equals(s)
                    || MATCH_CAPABILITY_REQUIRED.equals(s)
                    || ENGINEERING_REQUIRED.equals(s)
                    || NOT_IMPL(s))) {
                // keep blocked statuses
            } else if (KycRequirementType.MANUAL_VERIFICATION.name().equals(
                    String.valueOf(rule.getMetadata() == null ? "" : rule.getMetadata().get("kycRequirementType")))) {
                rollup = PolicyImplementabilityService.MANUAL_VERIFICATION;
            }
        }
        ruleRow.put("implementability", rollup);
        ruleRow.put("decisionDomain", domain.name());
        ruleRow.put("automationClass", automationClass(rollup));
        if (isKycCritical(rule) && isBlockedDesignStatus(rollup)) {
            ruleRow.put("critical", true);
            ruleRow.put("criticalClass", "KYC / Eligibility Critical");
        }
    }

    public static Map<String, Object> assessKycPath(
            String path,
            CiPolicyRuleCandidate rule,
            KycIntegrationRoutingProbe routing
    ) {
        Map<String, Object> catalog = BusinessDataSourceCatalog.describe(path);
        Map<String, Object> out = new LinkedHashMap<>(catalog);
        Map<String, Object> meta = rule == null || rule.getMetadata() == null ? Map.of() : rule.getMetadata();

        boolean matchMissing = Boolean.TRUE.equals(meta.get("matchCapabilityMissing"))
                || (path != null && path.toLowerCase(Locale.ROOT).contains("name_match"));
        boolean unsupported = Boolean.TRUE.equals(meta.get("unsupportedCapability"))
                || (path != null && KycFactCatalog.unsupportedCapabilityCodes().stream()
                .anyMatch(u -> path.equalsIgnoreCase(u) || path.toLowerCase(Locale.ROOT).contains(
                        u.replace("kyc.", "").replace(".", "_"))));

        PolicyGuardrailClass guardrail = DecisionPolicyRuleMetadata.guardrailOf(meta);
        String reqType = String.valueOf(meta.getOrDefault("kycRequirementType", ""));

        out.put("policyRequirementExists", true);
        out.put("requirementType", reqType);
        out.put("guardrailClass", guardrail.name());
        out.put("platformGuardrail", guardrail == PolicyGuardrailClass.PLATFORM_GUARDRAIL);
        out.put("studioEditable", guardrail != PolicyGuardrailClass.PLATFORM_GUARDRAIL);
        out.put("designTimeAssessment", true);
        out.put("runtimeOutageDistinct", true);

        if (path != null && path.startsWith("application.")) {
            boolean exists = KycCapabilityEvidence.applicationInputExists(path);
            out.put("workflowCapability", "N/A — application input");
            out.put("primarySource", "Application intake");
            out.put("fallbackSource", null);
            out.put("status", exists
                    ? PolicyImplementabilityService.READY
                    : APPLICATION_INPUT_REQUIRED);
            out.put("recommendedAction", exists
                    ? "No action — application field exists in LOS intake model."
                    : "Add this field to the application / intake model, or revise the policy.");
            return out;
        }

        if (unsupported) {
            out.put("workflowCapability", "Not supported");
            out.put("primarySource", null);
            out.put("fallbackSource", null);
            out.put("status", PolicyImplementabilityService.NOT_IMPLEMENTABLE);
            out.put("recommendedAction",
                    "Capability is not proven in repository evidence (e.g. PEP / UBO / signatory). "
                            + "Options: Manual Verification, Integrate a source, or Modify policy.");
            return out;
        }

        if (matchMissing || KycRequirementType.MATCH_REQUIREMENT.name().equals(reqType)) {
            out.put("workflowCapability", "No governed matcher");
            out.put("primarySource", null);
            out.put("fallbackSource", null);
            out.put("matchCapabilityExists", false);
            out.put("status", MATCH_CAPABILITY_REQUIRED);
            out.put("recommendedAction",
                    "Configure/implement a governed match capability with business thresholds — do not invent match scores.");
            return out;
        }

        if (KycRequirementType.MANUAL_VERIFICATION.name().equals(reqType)
                || "MANUAL".equalsIgnoreCase(String.valueOf(meta.get("verificationMode")))) {
            boolean governed = isGovernedManual(rule);
            out.put("workflowCapability", "Manual verification");
            out.put("primarySource", "Credit / KYC reviewer");
            out.put("status", governed
                    ? PolicyImplementabilityService.MANUAL_VERIFICATION
                    : MANUAL_CONFIGURATION_REQUIRED);
            out.put("requiredActor", meta.getOrDefault("requiredActor", governed ? "KYC Reviewer" : null));
            out.put("requiredEvidence", meta.getOrDefault("requiredEvidence",
                    governed ? "Supporting KYC evidence / appraisal note" : null));
            out.put("outcome", "PASS / REFER / FAIL");
            out.put("recommendedAction", governed
                    ? "Manual verification designated — remains visible; not ignored."
                    : "Complete manual verification configuration (reviewer role, evidence, PASS/REFER/FAIL).");
            return out;
        }

        Optional<KycStepType> stepOpt = KycCapabilityEvidence.stepForFact(path);
        if (stepOpt.isEmpty()) {
            // Fact present in registry as AVAILABLE but no step mapping (e.g. overall outcome)
            if (path != null && path.startsWith("kyc.overall")) {
                out.put("workflowCapability", "Aggregate KYC outcome (authoring fact)");
                out.put("primarySource", "Normalized KYC facts");
                out.put("status", PolicyImplementabilityService.READY);
                out.put("recommendedAction", "No action — aggregate outcome is derived from KYC facts.");
                return out;
            }
            if (path != null && (path.endsWith(".present") || path.contains("present"))) {
                out.put("workflowCapability", "Application / document presence");
                out.put("primarySource", "Application intake");
                out.put("status", PolicyImplementabilityService.READY);
                return out;
            }
            out.put("status", PolicyImplementabilityService.MAPPING_REQUIRED);
            out.put("recommendedAction", "Map this KYC requirement to a known fact / workflow step.");
            return out;
        }

        KycStepType step = stepOpt.get();
        Map<String, Object> stepDesc = KycCapabilityEvidence.describeStep(step);
        out.putAll(stepDesc);
        out.put("workflowCapability", step.name());
        out.put("primarySource", stepDesc.get("primaryProvider"));
        out.put("fallbackSource", stepDesc.get("fallbackProvider"));

        boolean workflowOk = KycCapabilityEvidence.hasWorkflowCapability(step);
        boolean implOk = KycCapabilityEvidence.hasProviderImplementation(step);

        if (!workflowOk || !implOk) {
            // Enum-only without implementation (e.g. EMAIL_OTP)
            out.put("status", Boolean.TRUE.equals(stepDesc.get("enumOnlyWithoutImplementation"))
                    ? ENGINEERING_REQUIRED
                    : WORKFLOW_CONFIGURATION_REQUIRED);
            out.put("recommendedAction",
                    "Workflow capability or provider implementation is missing for " + step.name()
                            + ". Do not mark READY from enum presence alone.");
            if (guardrail == PolicyGuardrailClass.PLATFORM_GUARDRAIL) {
                out.put("guardrailProtected", true);
                out.put("guardrailExecutable", false);
            }
            return out;
        }

        // Live AggregatorConfig overlay when available
        if (routing.isLiveConfigurationProbe()) {
            Optional<KycIntegrationRoutingProbe.Routing> rt = routing.routingFor(step);
            if (rt.isEmpty()) {
                out.put("status", INTEGRATION_CONFIGURATION_REQUIRED);
                out.put("primaryConfigured", false);
                out.put("fallbackConfigured", false);
                out.put("recommendedAction",
                        "Workflow capability exists and a provider implementation is present, "
                                + "but no active integration routing is configured for " + step.name() + ".");
                return out;
            }
            KycIntegrationRoutingProbe.Routing r = rt.get();
            out.put("primaryConfigured", r.primaryConfigured());
            out.put("fallbackConfigured", r.fallbackConfigured());
            if (r.primaryName() != null) {
                out.put("primarySource", r.primaryName());
            }
            if (r.fallbackName() != null) {
                out.put("fallbackSource", r.fallbackName());
            }
            if (!r.primaryConfigured() && r.fallbackConfigured()) {
                out.put("status", PolicyImplementabilityService.READY_WITH_FALLBACK);
                out.put("recommendedAction",
                        "Primary unavailable in config; compatible fallback is configured — design-ready with fallback.");
                return out;
            }
            if (!r.primaryConfigured()) {
                out.put("status", INTEGRATION_CONFIGURATION_REQUIRED);
                out.put("recommendedAction",
                        "No primary or fallback integration routing configured for " + step.name() + ".");
                return out;
            }
        }

        // Design-time: implementation + workflow present; fallback label listed → READY (or READY_WITH_FALLBACK if only fallback)
        boolean hasFallbackLabel = KycCapabilityEvidence.fallbackProviderLabel(step) != null;
        out.put("primaryConfigured", true); // design-time evidence of implementation
        out.put("fallbackConfigured", hasFallbackLabel);
        out.put("status", PolicyImplementabilityService.READY);
        out.put("recommendedAction", "No action — workflow capability and provider implementation are present.");
        if (guardrail == PolicyGuardrailClass.PLATFORM_GUARDRAIL) {
            out.put("status", PLATFORM_GUARDRAIL);
            // PLATFORM_GUARDRAIL that IS executable still contributes as ready-protected
            out.put("guardrailProtected", true);
            out.put("guardrailExecutable", true);
            out.put("implementabilityEquivalent", PolicyImplementabilityService.READY);
        }
        return out;
    }

    private static List<Map<String, Object>> buildKycRequirementRows(
            List<Map<String, Object>> rules,
            PolicyStudioSession session,
            KycIntegrationRoutingProbe routing
    ) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> ruleRow : rules) {
            CiPolicyRuleCandidate rule = findRule(session, ruleRow);
            if (rule == null) {
                continue;
            }
            DecisionPolicyDomain domain = DecisionPolicyRuleMetadata.domainOf(rule.getMetadata(), rule.getScope());
            if (!domain.isKycOrEligibility()) {
                continue;
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> reqs = (List<Map<String, Object>>) ruleRow.get("requirements");
            if (reqs == null || reqs.isEmpty()) {
                Map<String, Object> row = baseKycRow(ruleRow, rule, domain);
                row.put("dataNeeded", "Unmapped");
                row.put("workflowCapability", "—");
                row.put("primarySource", "—");
                row.put("fallbackSource", "—");
                row.put("status", PolicyImplementabilityService.MAPPING_REQUIRED);
                row.put("action", "Complete executable mapping for this KYC requirement.");
                row.put("critical", isKycCritical(rule));
                rows.add(row);
                continue;
            }
            for (Map<String, Object> req : reqs) {
                Map<String, Object> row = baseKycRow(ruleRow, rule, domain);
                row.put("dataNeeded", req.getOrDefault("businessName", req.get("dataElementCode")));
                row.put("dataElementCode", req.get("dataElementCode"));
                row.put("requirementType", req.getOrDefault("requirementType",
                        rule.getMetadata() == null ? null : rule.getMetadata().get("kycRequirementType")));
                row.put("workflowCapability", req.getOrDefault("workflowCapability", "—"));
                row.put("primarySource", req.getOrDefault("primarySource", req.get("primaryProvider")));
                row.put("fallbackSource", req.getOrDefault("fallbackSource", req.get("fallbackProvider")));
                String status = String.valueOf(req.get("status"));
                if (PLATFORM_GUARDRAIL.equals(status)
                        && Boolean.TRUE.equals(req.get("guardrailExecutable"))) {
                    row.put("statusDisplay", "PROTECTED / READY");
                    row.put("status", PolicyImplementabilityService.READY);
                    row.put("platformGuardrail", true);
                } else {
                    row.put("status", status);
                    row.put("statusDisplay", status.replace('_', ' '));
                    row.put("platformGuardrail", Boolean.TRUE.equals(req.get("platformGuardrail")));
                }
                row.put("action", req.get("recommendedAction"));
                row.put("manualVerificationOption", req.get("manualCapturePossible"));
                row.put("critical", isKycCritical(rule)
                        || Boolean.TRUE.equals(ruleRow.get("critical")));
                row.put("availability", req.get("registryAvailability"));
                rows.add(row);
            }
        }
        return rows;
    }

    private static Map<String, Object> baseKycRow(
            Map<String, Object> ruleRow,
            CiPolicyRuleCandidate rule,
            DecisionPolicyDomain domain
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("requirement", ruleRow.getOrDefault("ruleName", rule.getSystemRuleId()));
        row.put("businessRule", ruleRow.get("businessRule"));
        row.put("systemRuleId", rule.getSystemRuleId());
        row.put("decisionDomain", domain.name());
        row.put("domainLabel", domain == DecisionPolicyDomain.ELIGIBILITY
                ? "Eligibility" : "KYC");
        return row;
    }

    private static List<Map<String, Object>> buildDomainCards(
            List<Map<String, Object>> rules,
            List<Map<String, Object>> kycRequirements,
            PolicyStudioSession session
    ) {
        List<Map<String, Object>> cards = new ArrayList<>();

        // KYC & Eligibility from kyc requirement rows
        if (!kycRequirements.isEmpty()) {
            cards.add(countCard("KYC_ELIGIBILITY", "KYC & Eligibility", kycRequirements));
        }

        Map<DecisionPolicyDomain, List<Map<String, Object>>> byDomain = new LinkedHashMap<>();
        for (Map<String, Object> ruleRow : rules) {
            CiPolicyRuleCandidate rule = findRule(session, ruleRow);
            DecisionPolicyDomain d = rule == null
                    ? DecisionPolicyDomain.CREDIT
                    : DecisionPolicyRuleMetadata.domainOf(rule.getMetadata(), rule.getScope());
            if (d.isKycOrEligibility()) {
                continue;
            }
            byDomain.computeIfAbsent(d, k -> new ArrayList<>()).add(ruleRow);
        }

        addIfPresent(cards, "CREDIT_UNDERWRITING", "Credit Underwriting",
                merge(byDomain.get(DecisionPolicyDomain.CREDIT)));
        addScoreCard(cards, session, byDomain.get(DecisionPolicyDomain.RISK_SCORE));
        addIfPresent(cards, "LIMIT_PRICING", "Limit / Pricing",
                merge(byDomain.get(DecisionPolicyDomain.LIMIT), byDomain.get(DecisionPolicyDomain.PRICING)));
        addDecisionReviewCard(cards, session, byDomain.get(DecisionPolicyDomain.DECISION_REVIEW));

        // Always include credit if any non-KYC rules exist under CREDIT default
        long creditLike = rules.stream().filter(r -> {
            CiPolicyRuleCandidate rule = findRule(session, r);
            if (rule == null) {
                return true;
            }
            DecisionPolicyDomain d = DecisionPolicyRuleMetadata.domainOf(rule.getMetadata(), rule.getScope());
            return d == DecisionPolicyDomain.CREDIT || (!d.isKycOrEligibility()
                    && d != DecisionPolicyDomain.RISK_SCORE
                    && d != DecisionPolicyDomain.LIMIT
                    && d != DecisionPolicyDomain.PRICING
                    && d != DecisionPolicyDomain.DECISION_REVIEW);
        }).count();
        if (creditLike > 0 && cards.stream().noneMatch(c -> "CREDIT_UNDERWRITING".equals(c.get("sectionCode")))) {
            List<Map<String, Object>> creditRules = rules.stream().filter(r -> {
                CiPolicyRuleCandidate rule = findRule(session, r);
                if (rule == null) {
                    return true;
                }
                return DecisionPolicyRuleMetadata.domainOf(rule.getMetadata(), rule.getScope())
                        == DecisionPolicyDomain.CREDIT
                        || !DecisionPolicyRuleMetadata.domainOf(rule.getMetadata(), rule.getScope()).isKycOrEligibility();
            }).filter(r -> {
                CiPolicyRuleCandidate rule = findRule(session, r);
                if (rule == null) {
                    return true;
                }
                DecisionPolicyDomain d = DecisionPolicyRuleMetadata.domainOf(rule.getMetadata(), rule.getScope());
                return d == DecisionPolicyDomain.CREDIT
                        || (d != DecisionPolicyDomain.RISK_SCORE && d != DecisionPolicyDomain.LIMIT
                        && d != DecisionPolicyDomain.PRICING && d != DecisionPolicyDomain.DECISION_REVIEW
                        && !d.isKycOrEligibility());
            }).toList();
            // Avoid double-counting KYC — filter again
            creditRules = creditRules.stream().filter(r -> {
                CiPolicyRuleCandidate rule = findRule(session, r);
                return rule == null || !DecisionPolicyRuleMetadata.domainOf(rule.getMetadata(), rule.getScope()).isKycOrEligibility();
            }).toList();
            if (!creditRules.isEmpty()) {
                cards.add(countCardFromRules("CREDIT_UNDERWRITING", "Credit Underwriting", creditRules));
            }
        }

        for (Map<String, Object> c : cards) {
            c.put("allowCanonicalAuthority", false);
            c.put("note", "Policy design readiness — not production authority");
        }
        return cards;
    }

    private static void addScoreCard(
            List<Map<String, Object>> cards,
            PolicyStudioSession session,
            List<Map<String, Object>> riskRules
    ) {
        boolean mentionsScore = session.getRuleCandidates().stream().anyMatch(r -> {
            String sys = r.getSystemRuleId() == null ? "" : r.getSystemRuleId().toUpperCase(Locale.ROOT);
            Map<String, Object> meta = r.getMetadata() == null ? Map.of() : r.getMetadata();
            String biz = String.valueOf(meta.getOrDefault("businessTitle", "")).toLowerCase(Locale.ROOT);
            return sys.contains("SCORE") || biz.contains("scorecard") || biz.contains("bureau score");
        });
        if ((riskRules == null || riskRules.isEmpty()) && !mentionsScore) {
            return;
        }
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("sectionCode", "RISK_SCORE");
        card.put("sectionName", "Risk / Score");
        // Honest: live scorecard remains legacy production configuration — Policy Studio does not own execution
        card.put("scorecardLinkage", "LIVE_SCORECARD_CONFIGURATION");
        card.put("scorecardOwnedByPolicyStudio", false);
        if (riskRules != null && !riskRules.isEmpty()) {
            Map<String, Object> counts = countCardFromRules("RISK_SCORE", "Risk / Score", riskRules);
            card.putAll(counts);
        } else {
            card.put("requirements", 1);
            card.put("ready", 1);
            card.put("manual", 0);
            card.put("needsAttention", 0);
            card.put("blocked", 0);
            card.put("status", "READY");
            card.put("detail", "Scorecard linkage refers to live / legacy scorecard configuration — not Policy Studio execution.");
        }
        cards.add(card);
    }

    private static void addDecisionReviewCard(
            List<Map<String, Object>> cards,
            PolicyStudioSession session,
            List<Map<String, Object>> decisionRules
    ) {
        if (decisionRules != null && !decisionRules.isEmpty()) {
            cards.add(countCardFromRules("DECISION_REVIEW", "Decision / Review", decisionRules));
            return;
        }
        boolean hasApprovalMeta = session.getRuleCandidates().stream().anyMatch(r -> {
            Map<String, Object> m = r.getMetadata();
            return m != null && (m.containsKey("authorityMatrix") || m.containsKey("camRequired")
                    || m.containsKey("makerChecker"));
        });
        // Do not invent CAM/authority counts — only surface when metadata exists
        if (!hasApprovalMeta) {
            return;
        }
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("sectionCode", "DECISION_REVIEW");
        card.put("sectionName", "Decision / Review");
        card.put("requirements", 1);
        card.put("ready", 1);
        card.put("manual", 0);
        card.put("needsAttention", 0);
        card.put("blocked", 0);
        card.put("status", "READY");
        card.put("detail", "Decision/review metadata present in authoring — not activated in production.");
        cards.add(card);
    }

    private static void addIfPresent(List<Map<String, Object>> cards, String code, String name,
                                     List<Map<String, Object>> ruleRows) {
        if (ruleRows == null || ruleRows.isEmpty()) {
            return;
        }
        cards.add(countCardFromRules(code, name, ruleRows));
    }

    @SafeVarargs
    private static List<Map<String, Object>> merge(List<Map<String, Object>>... lists) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (lists == null) {
            return out;
        }
        for (List<Map<String, Object>> l : lists) {
            if (l != null) {
                out.addAll(l);
            }
        }
        return out;
    }

    private static Map<String, Object> countCard(String code, String name, List<Map<String, Object>> rows) {
        long ready = rows.stream().filter(r -> isReadyStatus(String.valueOf(r.get("status")))).count();
        long manual = rows.stream().filter(r -> isManualStatus(String.valueOf(r.get("status")))).count();
        long blocked = rows.stream().filter(r -> isBlockedDesignStatus(String.valueOf(r.get("status")))).count();
        long attention = rows.size() - ready - manual - blocked;
        if (attention < 0) {
            attention = 0;
        }
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("sectionCode", code);
        card.put("sectionName", name);
        card.put("requirements", rows.size());
        card.put("ready", ready);
        card.put("manual", manual);
        card.put("needsAttention", attention);
        card.put("blocked", blocked);
        card.put("status", domainStatus(ready, manual, attention, blocked, rows.size()));
        return card;
    }

    private static Map<String, Object> countCardFromRules(String code, String name, List<Map<String, Object>> ruleRows) {
        long ready = ruleRows.stream().filter(r -> isReadyStatus(String.valueOf(r.get("implementability")))).count();
        long manual = ruleRows.stream().filter(r -> isManualStatus(String.valueOf(r.get("implementability")))).count();
        long blocked = ruleRows.stream().filter(r -> isBlockedDesignStatus(String.valueOf(r.get("implementability")))).count();
        long attention = ruleRows.size() - ready - manual - blocked;
        if (attention < 0) {
            attention = 0;
        }
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("sectionCode", code);
        card.put("sectionName", name);
        card.put("requirements", ruleRows.size());
        card.put("ready", ready);
        card.put("manual", manual);
        card.put("needsAttention", attention);
        card.put("blocked", blocked);
        card.put("status", domainStatus(ready, manual, attention, blocked, ruleRows.size()));
        return card;
    }

    private static String domainStatus(long ready, long manual, long attention, long blocked, int total) {
        if (total == 0) {
            return "UNKNOWN";
        }
        if (blocked > 0) {
            return blocked >= total ? "BLOCKED" : "NEEDS_WORK";
        }
        if (attention > 0) {
            return "NEEDS_WORK";
        }
        return "READY";
    }

    private static Map<String, Object> buildOverall(
            List<Map<String, Object>> domainCards,
            List<Map<String, Object>> rules,
            PolicyStudioSession session
    ) {
        int requirements = 0;
        int ready = 0;
        int manual = 0;
        int needsAttention = 0;
        int blocked = 0;
        for (Map<String, Object> c : domainCards) {
            requirements += ((Number) c.getOrDefault("requirements", 0)).intValue();
            ready += ((Number) c.getOrDefault("ready", 0)).intValue();
            manual += ((Number) c.getOrDefault("manual", 0)).intValue();
            needsAttention += ((Number) c.getOrDefault("needsAttention", 0)).intValue();
            blocked += ((Number) c.getOrDefault("blocked", 0)).intValue();
        }

        // Critical KYC blockers
        boolean criticalBlock = rules.stream().anyMatch(r -> {
            if (!Boolean.TRUE.equals(r.get("critical"))) {
                return false;
            }
            CiPolicyRuleCandidate rule = findRule(session, r);
            if (rule == null) {
                return isBlockedDesignStatus(String.valueOf(r.get("implementability")));
            }
            DecisionPolicyDomain d = DecisionPolicyRuleMetadata.domainOf(rule.getMetadata(), rule.getScope());
            return d.isKycOrEligibility() && isBlockedDesignStatus(String.valueOf(r.get("implementability")));
        });

        String status;
        if (criticalBlock || blocked > 0 && ready + manual == 0) {
            status = OVERALL_BLOCKED;
        } else if (blocked > 0 || needsAttention > 0) {
            status = OVERALL_NEEDS_ATTENTION;
        } else if (requirements == 0) {
            status = OVERALL_NEEDS_ATTENTION;
        } else {
            status = OVERALL_IMPLEMENTATION_READY;
        }

        int percent = requirements <= 0 ? 0
                : (int) Math.round(100.0 * (ready + manual) / requirements);

        Map<String, Object> overall = new LinkedHashMap<>();
        overall.put("status", status);
        overall.put("implementationReadinessPercent", percent);
        overall.put("requirements", requirements);
        overall.put("ready", ready);
        overall.put("manual", manual);
        overall.put("needsAttention", needsAttention);
        overall.put("blocked", blocked);
        overall.put("criticalKycBlocked", criticalBlock);
        overall.put("headline", status + " — " + ready + " ready, " + manual + " manual, "
                + needsAttention + " needs attention, " + blocked + " blocked of " + requirements + " requirements.");
        overall.put("allowCanonicalAuthority", false);
        overall.put("demoLabel", detectDemoLabel(session));
        return overall;
    }

    private static String detectDemoLabel(PolicyStudioSession session) {
        String name = documentName(session).toLowerCase(Locale.ROOT);
        if (name.contains("kyc") || name.contains("eligibility") || name.contains("validation")) {
            return "DEMO POLICY / VALIDATION SAMPLE";
        }
        return null;
    }

    private static String documentName(PolicyStudioSession session) {
        if (session.getDocument() != null && session.getDocument().getName() != null) {
            return session.getDocument().getName();
        }
        return "";
    }

    private static List<Map<String, Object>> buildDecisionPolicyNextActions(
            List<Map<String, Object>> kycRequirements,
            List<Map<String, Object>> rules,
            Map<String, Object> assessOut
    ) {
        Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
        for (Map<String, Object> req : kycRequirements) {
            String status = String.valueOf(req.get("status"));
            if (isReadyStatus(status) || isManualStatus(status) && !MANUAL_CONFIGURATION_REQUIRED.equals(status)) {
                if (PolicyImplementabilityService.MANUAL_VERIFICATION.equals(status)) {
                    continue; // governed manual is not an open action
                }
            }
            if (isReadyStatus(status)) {
                continue;
            }
            String key = status + "|" + req.get("dataElementCode");
            Map<String, Object> a = byKey.computeIfAbsent(key, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("title", req.getOrDefault("requirement", req.get("dataNeeded")));
                m.put("dataElementCode", req.get("dataElementCode"));
                m.put("status", status);
                m.put("detail", "Required by KYC / Eligibility rule(s). Status: " + status + ".");
                m.put("recommended", req.get("action"));
                m.put("options", resolutionOptions(status));
                m.put("blocksPolicy", Boolean.TRUE.equals(req.get("critical")) && isBlockedDesignStatus(status));
                m.put("dependentRuleCount", 0);
                m.put("domain", "KYC & Eligibility");
                return m;
            });
            a.put("dependentRuleCount", ((Number) a.get("dependentRuleCount")).intValue() + 1);
            a.put("detail", "Required by " + a.get("dependentRuleCount") + " KYC rule(s). Status: " + status + ".");
        }

        // Also surface FOIR/DSCR / pricing gaps from credit rules
        for (Map<String, Object> ruleRow : rules) {
            String st = String.valueOf(ruleRow.get("implementability"));
            if (!isBlockedDesignStatus(st) && !PolicyImplementabilityService.METRIC_REQUIRED.equals(st)
                    && !PolicyImplementabilityService.DATA_SOURCE_REQUIRED.equals(st)
                    && !PolicyImplementabilityService.MAPPING_REQUIRED.equals(st)) {
                continue;
            }
            String sys = String.valueOf(ruleRow.get("systemRuleId"));
            String name = String.valueOf(ruleRow.get("ruleName"));
            boolean limitPricing = sys.toUpperCase(Locale.ROOT).contains("FOIR")
                    || sys.toUpperCase(Locale.ROOT).contains("DSCR")
                    || name.toLowerCase(Locale.ROOT).contains("foir")
                    || name.toLowerCase(Locale.ROOT).contains("dscr")
                    || name.toLowerCase(Locale.ROOT).contains("pricing")
                    || name.toLowerCase(Locale.ROOT).contains("ltv");
            if (!limitPricing && !Boolean.TRUE.equals(ruleRow.get("critical"))) {
                continue;
            }
            String key = st + "|" + sys;
            byKey.computeIfAbsent(key, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("title", name);
                m.put("status", st);
                m.put("detail", "Required by credit / limit-pricing rule " + sys + ".");
                m.put("recommended", ruleRow.get("recommendedAction"));
                m.put("options", resolutionOptions(st));
                m.put("blocksPolicy", Boolean.TRUE.equals(ruleRow.get("critical")));
                m.put("dependentRuleCount", 1);
                m.put("domain", limitPricing ? "Limit / Pricing" : "Credit Underwriting");
                return m;
            });
        }

        List<Map<String, Object>> actions = new ArrayList<>();
        int n = 0;
        // Prefer blockers first
        List<Map<String, Object>> ordered = byKey.values().stream()
                .sorted((a, b) -> Boolean.compare(
                        !Boolean.TRUE.equals(a.get("blocksPolicy")),
                        !Boolean.TRUE.equals(b.get("blocksPolicy"))))
                .toList();
        for (Map<String, Object> gap : ordered) {
            if (n >= 8) {
                break;
            }
            n++;
            Map<String, Object> a = new LinkedHashMap<>(gap);
            a.put("sequence", n);
            actions.add(a);
        }
        if (actions.isEmpty()) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> existing = (List<Map<String, Object>>) assessOut.get("nextActions");
            return existing == null ? List.of() : existing;
        }
        return actions;
    }

    private static List<Map<String, Object>> buildConsolidatedSources(
            List<Map<String, Object>> rules,
            List<Map<String, Object>> kycRequirements
    ) {
        List<Map<String, Object>> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, Object> req : kycRequirements) {
            String code = String.valueOf(req.get("dataElementCode"));
            if (!seen.add(code)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("businessElement", req.get("dataNeeded"));
            row.put("category", categoryFor(code));
            row.put("primarySource", req.get("primarySource"));
            row.put("fallback", req.get("fallbackSource"));
            row.put("requiredBy", req.get("requirement"));
            row.put("status", req.get("status"));
            out.add(row);
        }
        for (Map<String, Object> ruleRow : rules) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> reqs = (List<Map<String, Object>>) ruleRow.get("requirements");
            if (reqs == null) {
                continue;
            }
            for (Map<String, Object> req : reqs) {
                String code = String.valueOf(req.get("dataElementCode"));
                if (!seen.add(code)) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("businessElement", req.get("businessName"));
                row.put("category", categoryFor(code));
                row.put("primarySource", req.get("primarySource"));
                row.put("fallback", req.get("fallbackSources"));
                row.put("requiredBy", ruleRow.get("ruleName"));
                row.put("status", req.get("status"));
                out.add(row);
            }
        }
        return out;
    }

    private static String categoryFor(String code) {
        if (code == null) {
            return "Other";
        }
        String c = code.toLowerCase(Locale.ROOT);
        if (c.startsWith("application.")) {
            return "Application Inputs";
        }
        if (c.startsWith("kyc.")) {
            return "KYC / Identity";
        }
        if (c.startsWith("bureau.")) {
            return "Bureau";
        }
        if (c.startsWith("banking.") || c.startsWith("bank.")) {
            return "Banking";
        }
        if (c.startsWith("gst.")) {
            return "GST";
        }
        if (c.startsWith("itr.")) {
            return "ITR";
        }
        if (c.contains("cin") || c.contains("mca")) {
            return "MCA / Business Registry";
        }
        if (c.contains("score")) {
            return "Scorecard Inputs";
        }
        if (c.contains("foir") || c.contains("dscr") || c.contains("ltv") || c.contains("pricing")) {
            return "Decision Inputs";
        }
        return "Other";
    }

    private static String buildAnalystMessage(
            Map<String, Object> overall,
            List<Map<String, Object>> domainCards,
            List<Map<String, Object>> kycRequirements,
            boolean decisionPolicy
    ) {
        StringBuilder sb = new StringBuilder();
        if (decisionPolicy) {
            sb.append("I checked the complete Decision Policy against your configured application fields, ")
                    .append("workflows and integrations. ");
        } else {
            sb.append("I checked this policy against your currently configured data sources. ");
        }
        long kycBlocked = kycRequirements.stream()
                .filter(r -> isBlockedDesignStatus(String.valueOf(r.get("status")))
                        || MATCH_CAPABILITY_REQUIRED.equals(String.valueOf(r.get("status")))
                        || INTEGRATION_CONFIGURATION_REQUIRED.equals(String.valueOf(r.get("status")))
                        || WORKFLOW_CONFIGURATION_REQUIRED.equals(String.valueOf(r.get("status")))
                        || ENGINEERING_REQUIRED.equals(String.valueOf(r.get("status"))))
                .count();
        long kycManual = kycRequirements.stream()
                .filter(r -> isManualStatus(String.valueOf(r.get("status"))))
                .count();
        if (kycBlocked > 0) {
            sb.append("I found ").append(kycBlocked)
                    .append(" KYC requirement(s) that cannot currently be automated. ");
        }
        if (kycManual > 0) {
            sb.append(kycManual).append(" KYC requirement(s) are designated for manual verification. ");
        }
        sb.append("Overall design readiness: ").append(overall.get("status")).append(". ");
        sb.append("Counts: ").append(overall.get("ready")).append(" ready, ")
                .append(overall.get("manual")).append(" manual, ")
                .append(overall.get("needsAttention")).append(" needs attention, ")
                .append(overall.get("blocked")).append(" blocked");
        if (overall.get("demoLabel") != null) {
            sb.append(" (").append(overall.get("demoLabel")).append(")");
        }
        sb.append(". allowCanonicalAuthority=false.");
        return sb.toString();
    }

    private static List<String> resolutionOptions(String status) {
        if (MATCH_CAPABILITY_REQUIRED.equals(status)) {
            return List.of("Configure/implement governed match capability", "Mark manual verification", "Modify policy");
        }
        if (INTEGRATION_CONFIGURATION_REQUIRED.equals(status) || WORKFLOW_CONFIGURATION_REQUIRED.equals(status)) {
            return List.of("Configure integration / workflow", "Manual Verification", "Modify policy");
        }
        if (ENGINEERING_REQUIRED.equals(status) || PolicyImplementabilityService.NOT_IMPLEMENTABLE.equals(status)) {
            return List.of("Manual Verification", "Integrate a source", "Modify policy");
        }
        if (APPLICATION_INPUT_REQUIRED.equals(status)) {
            return List.of("Add application field", "Modify policy");
        }
        if (MANUAL_CONFIGURATION_REQUIRED.equals(status)) {
            return List.of("Complete manual verification configuration", "Modify policy");
        }
        return List.of("Add to application form", "Integrate a source", "Mark manual verification", "Modify/remove rule");
    }

    private static String rollupKycStatuses(List<String> statuses) {
        if (statuses.isEmpty()) {
            return PolicyImplementabilityService.MAPPING_REQUIRED;
        }
        List<String> priority = List.of(
                PolicyImplementabilityService.NOT_IMPLEMENTABLE,
                ENGINEERING_REQUIRED,
                MATCH_CAPABILITY_REQUIRED,
                INTEGRATION_CONFIGURATION_REQUIRED,
                WORKFLOW_CONFIGURATION_REQUIRED,
                MANUAL_CONFIGURATION_REQUIRED,
                PolicyImplementabilityService.DATA_SOURCE_REQUIRED,
                PolicyImplementabilityService.METRIC_REQUIRED,
                APPLICATION_INPUT_REQUIRED,
                BUSINESS_DEFINITION_REQUIRED,
                PolicyImplementabilityService.DEFINITION_REQUIRED,
                PolicyImplementabilityService.MAPPING_REQUIRED,
                PolicyImplementabilityService.MANUAL_VERIFICATION,
                PolicyImplementabilityService.MANUAL_INPUT_REQUIRED,
                PLATFORM_GUARDRAIL,
                PolicyImplementabilityService.READY_WITH_FALLBACK,
                PolicyImplementabilityService.READY);
        for (String p : priority) {
            if (statuses.contains(p)) {
                if (PLATFORM_GUARDRAIL.equals(p)) {
                    return PolicyImplementabilityService.READY;
                }
                return p;
            }
        }
        return statuses.get(0);
    }

    public static boolean isReadyStatus(String status) {
        return PolicyImplementabilityService.READY.equals(status)
                || PolicyImplementabilityService.READY_WITH_FALLBACK.equals(status)
                || PLATFORM_GUARDRAIL.equals(status);
    }

    public static boolean isManualStatus(String status) {
        return PolicyImplementabilityService.MANUAL_VERIFICATION.equals(status)
                || PolicyImplementabilityService.MANUAL_INPUT_REQUIRED.equals(status);
    }

    public static boolean isBlockedDesignStatus(String status) {
        return PolicyImplementabilityService.DATA_SOURCE_REQUIRED.equals(status)
                || PolicyImplementabilityService.METRIC_REQUIRED.equals(status)
                || PolicyImplementabilityService.MAPPING_REQUIRED.equals(status)
                || PolicyImplementabilityService.DEFINITION_REQUIRED.equals(status)
                || PolicyImplementabilityService.RECONCILIATION_REQUIRED.equals(status)
                || PolicyImplementabilityService.NOT_IMPLEMENTABLE.equals(status)
                || MATCH_CAPABILITY_REQUIRED.equals(status)
                || WORKFLOW_CONFIGURATION_REQUIRED.equals(status)
                || INTEGRATION_CONFIGURATION_REQUIRED.equals(status)
                || ENGINEERING_REQUIRED.equals(status)
                || APPLICATION_INPUT_REQUIRED.equals(status)
                || MANUAL_CONFIGURATION_REQUIRED.equals(status)
                || BUSINESS_DEFINITION_REQUIRED.equals(status);
    }

    private static boolean NOT_IMPL(String s) {
        return PolicyImplementabilityService.NOT_IMPLEMENTABLE.equals(s) || ENGINEERING_REQUIRED.equals(s);
    }

    private static String automationClass(String status) {
        if (isReadyStatus(status)) {
            return "AUTOMATABLE";
        }
        if (isManualStatus(status)) {
            return "MANUAL";
        }
        return "BLOCKED";
    }

    private static String mapOverallToBand(String overallStatus) {
        if (OVERALL_IMPLEMENTATION_READY.equals(overallStatus)) {
            return "READY";
        }
        if (OVERALL_NEEDS_ATTENTION.equals(overallStatus)) {
            return "NEEDS WORK";
        }
        return "BLOCKED";
    }

    private static boolean isGovernedManual(CiPolicyRuleCandidate rule) {
        if (rule == null || rule.getMetadata() == null) {
            return false;
        }
        Map<String, Object> meta = rule.getMetadata();
        if ("MANUAL".equalsIgnoreCase(String.valueOf(meta.get("verificationMode")))) {
            return meta.get("requiredEvidence") != null || meta.get("requiredActor") != null
                    || meta.get("outcomeOnSuccess") != null;
        }
        if (KycRequirementType.MANUAL_VERIFICATION.name().equals(String.valueOf(meta.get("kycRequirementType")))) {
            // Authoring stamped MANUAL_VERIFICATION with outcome semantics from KYC-3
            return meta.get("outcomeOnSuccess") != null || meta.get("outcomeOnFailure") != null;
        }
        return "MANUAL_VERIFICATION".equalsIgnoreCase(String.valueOf(meta.get("dataGapDisposition")));
    }

    private static boolean isKycCritical(CiPolicyRuleCandidate rule) {
        if (rule == null) {
            return false;
        }
        Map<String, Object> meta = rule.getMetadata() == null ? Map.of() : rule.getMetadata();
        PolicyGuardrailClass g = DecisionPolicyRuleMetadata.guardrailOf(meta);
        if (g == PolicyGuardrailClass.PLATFORM_GUARDRAIL) {
            return true;
        }
        String req = String.valueOf(meta.getOrDefault("kycRequirementType", ""));
        if (KycRequirementType.VERIFICATION.name().equals(req)
                || KycRequirementType.COMPLETION_REQUIREMENT.name().equals(req)
                || KycRequirementType.REGULATORY_GUARDRAIL.name().equals(req)) {
            return true;
        }
        String type = rule.getRuleType() == null ? "" : rule.getRuleType().toUpperCase(Locale.ROOT);
        return "KNOCKOUT".equals(type) || "HARD".equals(type) || "HARD_RULE".equals(type);
    }

    private static boolean isDecisionPolicySession(PolicyStudioSession session) {
        Map<String, Object> readiness = session.getReadiness();
        if (readiness != null && readiness.get("policyType") != null) {
            return DecisionPolicyType.supportsKycEligibilitySection(String.valueOf(readiness.get("policyType")));
        }
        String name = documentName(session).toLowerCase(Locale.ROOT);
        return name.contains("kyc") || name.contains("decision policy");
    }

    private static CiPolicyRuleCandidate findRule(PolicyStudioSession session, Map<String, Object> ruleRow) {
        if (session == null || ruleRow == null) {
            return null;
        }
        String id = ruleRow.get("ruleId") == null ? null : String.valueOf(ruleRow.get("ruleId"));
        String sys = ruleRow.get("systemRuleId") == null ? null : String.valueOf(ruleRow.get("systemRuleId"));
        for (CiPolicyRuleCandidate r : session.getRuleCandidates()) {
            if (id != null && r.getId() != null && id.equals(r.getId().toString())) {
                return r;
            }
            if (sys != null && sys.equals(r.getSystemRuleId())) {
                return r;
            }
        }
        return null;
    }
}
