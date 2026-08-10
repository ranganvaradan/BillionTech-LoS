package com.los.core.creditintelligence.staging;

import com.los.core.creditintelligence.decisionpolicy.DecisionPolicyDomain;
import com.los.core.creditintelligence.decisionpolicy.DecisionPolicyRuleMetadata;
import com.los.core.creditintelligence.decisionpolicy.kyc.KycPolicyAuthoringSupport;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyAmbiguity;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyClause;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.domain.ClauseType;
import com.los.core.creditintelligence.policystudio.domain.DocumentStatus;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.service.PolicyImplementabilityService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Business-friendly Policy Studio read model for Credit Head / Policy team (Day 1).
 */
final class ProspectPolicyViewBuilder {

    private ProspectPolicyViewBuilder() {}

    static void enrich(Map<String, Object> out, PolicyStudioSession session, Map<String, Object> meta) {
        if (meta != null) {
            out.putAll(meta);
        }
        CiPolicyDocument doc = session.getDocument();
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("policyName", doc == null ? null : doc.getName());
        header.put("fileName", meta != null ? meta.get("fileName") : (doc == null ? null : doc.getOriginalFileReference()));
        header.put("version", doc == null ? null : doc.getDocumentVersion());
        header.put("uploadDate", doc == null || doc.getCreatedAt() == null
                ? Instant.now().toString() : doc.getCreatedAt().toString());
        header.put("status", doc == null ? null : friendlyStatus(doc.getStatus()));
        header.put("statusCode", doc == null ? null : doc.getStatus());
        header.put("documentType", doc == null ? null : doc.getDocumentType());
        if (doc != null && doc.getId() != null) {
            header.put("documentId", doc.getId().toString());
        }
        out.put("policyHeader", header);

        out.put("summaryCards", summaryCards(session));
        out.put("counts", counts(session));
        Map<String, Object> domainBreakdown = domainBreakdown(session);
        out.put("domainBreakdown", domainBreakdown);
        out.put("analystMessage", analystMessage(session, domainBreakdown));
        out.put("structure", structure(session));
        out.put("pipeline", pipeline(session));
        out.put("humanReviewBanner",
                "AI-generated interpretations require human review. Nothing is published automatically.");
        out.put("allowCanonicalAuthority", false);
        out.put("productionActive", false);
        out.put("authoritative", false);
        out.put("kycAuthoringOnly", true);
        out.put("policySelectsProvider", false);
        out.put("policyEnqueuesWorkflowStep", false);

        // Keep technical columns for Advanced / Technical details tab — not shown on Overview by default
        out.put("source", Map.of(
                "clauseCount", session.getClauses().size(),
                "clauses", summarizeClauses(session.getClauses())));
        out.put("interpretation", Map.of(
                "interpretationCount", session.getInterpretations().size(),
                "ambiguityCount", session.getAmbiguities().size(),
                "ambiguities", summarizeAmbiguities(session.getAmbiguities()),
                "mappings", session.getMappings().size()));
        out.put("executable", Map.of(
                "ruleCount", session.getRuleCandidates().size(),
                "rules", summarizeRules(session.getRuleCandidates()),
                "metricCandidateCount", session.getMetricCandidates().size(),
                "testCaseCount", session.getTestCases().size(),
                "conflictCount", session.getConflicts().size(),
                "completeness", session.getCompleteness() == null ? Map.of() : session.getCompleteness(),
                "readiness", session.getReadiness() == null ? Map.of() : session.getReadiness()));

        // Day 2 — Credit Head ambiguity + rule review cards (business language)
        ProspectDay2ViewBuilder.enrich(out, session);

        // Day 6 — Policy Implementability / Data Readiness (session-derived, no invented availability)
        Map<String, Object> implementability = new PolicyImplementabilityService().assess(session);
        out.put("implementability", implementability);
        Object summary = implementability.get("summary");
        if (summary instanceof Map<?, ?> s) {
            out.put("implementabilitySummary", s);
        }
    }

    private static Map<String, Object> counts(PolicyStudioSession session) {
        long openAmb = session.getAmbiguities().stream()
                .filter(a -> "OPEN".equals(a.getResolutionStatus())).count();
        long mapped = session.getMappings() == null ? 0 : session.getMappings().size();
        long interpreted = session.getInterpretations() == null ? 0 : session.getInterpretations().size();
        long readyRules = session.getRuleCandidates().stream()
                .filter(r -> r.getValidationErrors() == null || r.getValidationErrors().isEmpty())
                .count();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalClauses", session.getClauses().size());
        m.put("interpretedClauses", interpreted);
        m.put("mappedClauses", mapped);
        m.put("reviewRequired", openAmb);
        m.put("readyClauses", readyRules);
        m.put("rules", session.getRuleCandidates().size());
        m.put("tests", session.getTestCases().size());
        m.put("metrics", session.getMetricCandidates().size());
        m.put("ambiguities", session.getAmbiguities().size());
        return m;
    }

    private static Map<String, Object> domainBreakdown(PolicyStudioSession session) {
        long kycRules = 0;
        long eligibilityRules = 0;
        long creditRules = 0;
        long manualKyc = 0;
        for (CiPolicyRuleCandidate r : session.getRuleCandidates()) {
            DecisionPolicyDomain d = DecisionPolicyRuleMetadata.domainOf(r.getMetadata(), r.getScope());
            if (d == DecisionPolicyDomain.KYC) {
                kycRules++;
            } else if (d == DecisionPolicyDomain.ELIGIBILITY) {
                eligibilityRules++;
            } else {
                creditRules++;
            }
            Object req = r.getMetadata() == null ? null : r.getMetadata().get(DecisionPolicyRuleMetadata.KEY_REQUIREMENT_TYPE);
            if ("MANUAL_VERIFICATION".equals(String.valueOf(req))) {
                manualKyc++;
            }
        }
        long kycClauses = session.getClauses().stream().filter(KycPolicyAuthoringSupport::isKycClause).count();
        long openAmb = session.getAmbiguities().stream()
                .filter(a -> "OPEN".equals(a.getResolutionStatus())).count();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kycEligibilityRequirements", kycRules + eligibilityRules);
        m.put("kycRules", kycRules);
        m.put("eligibilityRules", eligibilityRules);
        m.put("creditRules", creditRules);
        m.put("kycClauses", kycClauses);
        m.put("ambiguousTerms", openAmb);
        m.put("manualVerification", manualKyc);
        m.put("definitions", session.getClauses().stream()
                .filter(c -> ClauseType.METRIC_DEFINITION.name().equals(c.getClauseType())
                        || ClauseType.METRIC_ADJUSTMENT.name().equals(c.getClauseType()))
                .count());
        return m;
    }

    private static String analystMessage(PolicyStudioSession session, Map<String, Object> domainBreakdown) {
        long kycElig = ((Number) domainBreakdown.getOrDefault("kycEligibilityRequirements", 0)).longValue();
        long credit = ((Number) domainBreakdown.getOrDefault("creditRules", 0)).longValue();
        long amb = ((Number) domainBreakdown.getOrDefault("ambiguousTerms", 0)).longValue();
        long defs = ((Number) domainBreakdown.getOrDefault("definitions", 0)).longValue();
        long unsupported = session.getRuleCandidates().stream()
                .filter(r -> Boolean.TRUE.equals((r.getMetadata() == null ? Map.of() : r.getMetadata()).get("unsupportedCapability"))
                        || Boolean.TRUE.equals((r.getMetadata() == null ? Map.of() : r.getMetadata()).get("matchCapabilityMissing")))
                .count();
        StringBuilder sb = new StringBuilder();
        sb.append("I identified ");
        if (kycElig > 0 || credit > 0) {
            sb.append(kycElig).append(" KYC & Eligibility requirement")
                    .append(kycElig == 1 ? "" : "s")
                    .append(" and ")
                    .append(credit).append(" credit rule")
                    .append(credit == 1 ? "" : "s")
                    .append(".");
        } else {
            sb.append(session.getRuleCandidates().size()).append(" policy rule candidate")
                    .append(session.getRuleCandidates().size() == 1 ? "" : "s").append(".");
        }
        if (defs > 0) {
            sb.append(" ").append(defs).append(" definition").append(defs == 1 ? "" : "s").append(".");
        }
        if (amb > 0) {
            sb.append(" I found ").append(amb).append(" term")
                    .append(amb == 1 ? "" : "s")
                    .append(" that need your confirmation.");
        }
        if (unsupported > 0) {
            sb.append(" I found ").append(unsupported)
                    .append(" KYC requirement")
                    .append(unsupported == 1 ? "" : "s")
                    .append(" that cannot currently be mapped to available verification data.");
        }
        sb.append(" Authored KYC rules remain draft/shadow authoring only — not production KYC authority.");
        return sb.toString();
    }

    private static List<Map<String, Object>> summaryCards(PolicyStudioSession session) {
        Map<String, Integer> byCategory = new LinkedHashMap<>();
        // Prefer domain-aware rule counts when KYC rules exist
        Map<String, Object> domains = domainBreakdown(session);
        long kycElig = ((Number) domains.getOrDefault("kycEligibilityRequirements", 0)).longValue();
        long credit = ((Number) domains.getOrDefault("creditRules", 0)).longValue();
        if (kycElig > 0) {
            byCategory.put("KYC & Eligibility", (int) kycElig);
        }
        if (credit > 0 && kycElig > 0) {
            byCategory.put("Credit Rules", (int) credit);
        }
        for (CiPolicyClause c : session.getClauses()) {
            String cat = categoryForClause(c);
            if ("KYC & Eligibility".equals(cat) && kycElig > 0) {
                continue; // already counted via rules
            }
            if ("Credit Rules".equals(cat) && kycElig > 0) {
                continue;
            }
            byCategory.merge(cat, 1, Integer::sum);
        }
        int amb = session.getAmbiguities().size();
        if (amb > 0) {
            byCategory.put("Ambiguous Terms", amb);
        }
        long manual = ((Number) domains.getOrDefault("manualVerification", 0)).longValue();
        if (manual > 0) {
            byCategory.put("Manual Verification", (int) manual);
        }
        long missingMetrics = session.getMetricCandidates().stream()
                .filter(m -> m.getCandidateCanonicalCode() == null
                        || m.getCandidateCanonicalCode().isBlank()
                        || "UNKNOWN".equalsIgnoreCase(m.getCandidateCanonicalCode())
                        || Boolean.TRUE.equals((m.getMetadata() == null ? Map.of() : m.getMetadata()).get("missing")))
                .count();
        // Also surface metric candidates that look incomplete
        if (missingMetrics == 0) {
            missingMetrics = session.getMetricCandidates().stream()
                    .filter(m -> "DATA_INSUFFICIENT".equals(m.getMissingDataPolicy()))
                    .count();
        }
        if (missingMetrics > 0) {
            byCategory.put("Missing Metrics", (int) missingMetrics);
        }

        List<Map<String, Object>> cards = new ArrayList<>();
        for (Map.Entry<String, Integer> e : byCategory.entrySet()) {
            if (e.getValue() == null || e.getValue() <= 0) {
                continue;
            }
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("label", e.getKey());
            card.put("count", e.getValue());
            cards.add(card);
        }
        return cards;
    }

    private static List<Map<String, Object>> structure(PolicyStudioSession session) {
        Map<String, List<CiPolicyClause>> bySection = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (CiPolicyClause c : session.getClauses()) {
            String section = c.getSection() == null || c.getSection().isBlank()
                    ? categoryForClause(c) : c.getSection().trim();
            bySection.computeIfAbsent(section, k -> new ArrayList<>()).add(c);
        }
        List<Map<String, Object>> sections = new ArrayList<>();
        int sectionIdx = 0;
        for (Map.Entry<String, List<CiPolicyClause>> e : bySection.entrySet()) {
            sectionIdx++;
            List<Map<String, Object>> children = new ArrayList<>();
            int i = 0;
            for (CiPolicyClause c : e.getValue()) {
                i++;
                Map<String, Object> child = new LinkedHashMap<>();
                child.put("index", i);
                child.put("title", clauseTitle(c));
                child.put("clauseType", friendlyClauseType(c.getClauseType()));
                child.put("productScope", c.getProductScope());
                children.add(child);
            }
            long rules = session.getRuleCandidates().stream()
                    .filter(r -> clauseInSection(r.getClauseId(), e.getValue()))
                    .count();
            long ambs = session.getAmbiguities().stream()
                    .filter(a -> clauseInSection(a.getClauseId(), e.getValue()))
                    .count();
            long metrics = session.getMetricCandidates().stream()
                    .filter(m -> clauseInSection(m.getClauseId(), e.getValue()))
                    .count();
            Map<String, Object> sec = new LinkedHashMap<>();
            sec.put("name", e.getKey());
            sec.put("order", sectionIdx);
            sec.put("clauseCount", e.getValue().size());
            sec.put("rules", rules);
            sec.put("ambiguities", ambs);
            sec.put("missingMetrics", metrics);
            sec.put("items", children);
            sections.add(sec);
        }
        return sections;
    }

    private static Map<String, Object> pipeline(PolicyStudioSession session) {
        String status = session.getDocument() == null ? DocumentStatus.UPLOADED.name()
                : session.getDocument().getStatus();
        List<Map<String, Object>> stages = new ArrayList<>();
        String[][] defs = {
                {"Uploaded", DocumentStatus.UPLOADED.name()},
                {"Parsed", DocumentStatus.PARSED.name()},
                {"Interpreted", DocumentStatus.INTERPRETING.name()},
                {"Mapping Review", DocumentStatus.REVIEW_REQUIRED.name()},
                {"Rule Review", DocumentStatus.REVIEW_REQUIRED.name()},
                {"Test Review", DocumentStatus.REVIEW_REQUIRED.name()},
                {"Ready for Draft Policy", DocumentStatus.DRAFT_READY.name()}
        };
        int currentIndex = stageIndex(status, session);
        for (int i = 0; i < defs.length; i++) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("key", defs[i][0]);
            s.put("label", defs[i][0]);
            String state;
            if (i < currentIndex) {
                state = "DONE";
            } else if (i == currentIndex) {
                state = "CURRENT";
            } else {
                state = "PENDING";
            }
            s.put("state", state);
            stages.add(s);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("currentStage", defs[Math.min(currentIndex, defs.length - 1)][0]);
        out.put("progressPercent", Math.round((currentIndex / (double) Math.max(1, defs.length - 1)) * 100.0));
        out.put("stages", stages);
        out.put("documentStatus", status);
        return out;
    }

    private static int stageIndex(String status, PolicyStudioSession session) {
        if (DocumentStatus.DRAFT_READY.name().equals(status)
                || DocumentStatus.APPROVED_FOR_POLICY_BUILD.name().equals(status)) {
            return 6;
        }
        if (DocumentStatus.REVIEW_REQUIRED.name().equals(status)) {
            if (!session.getTestCases().isEmpty() && !session.getRuleCandidates().isEmpty()) {
                return 5;
            }
            if (!session.getRuleCandidates().isEmpty()) {
                return 4;
            }
            return 3;
        }
        if (DocumentStatus.INTERPRETING.name().equals(status)) {
            return 2;
        }
        if (DocumentStatus.PARSED.name().equals(status) || DocumentStatus.PARSING.name().equals(status)) {
            return 1;
        }
        // After processUpload, typically REVIEW_REQUIRED or DRAFT_READY — if UPLOADED leftover:
        if (!session.getClauses().isEmpty() && !session.getInterpretations().isEmpty()) {
            return 3;
        }
        if (!session.getClauses().isEmpty()) {
            return 1;
        }
        return 0;
    }

    private static String categoryForClause(CiPolicyClause c) {
        String type = c.getClauseType() == null ? "" : c.getClauseType().toUpperCase(Locale.ROOT);
        String section = c.getSection() == null ? "" : c.getSection().toLowerCase(Locale.ROOT);
        String text = ((c.getSourceText() == null ? "" : c.getSourceText())
                + " " + section).toLowerCase(Locale.ROOT);

        if (KycPolicyAuthoringSupport.isKycClause(c)
                || section.contains("kyc")
                || section.contains("identity")) {
            return "KYC & Eligibility";
        }
        if (type.equals(ClauseType.METRIC_DEFINITION.name()) || type.equals(ClauseType.METRIC_ADJUSTMENT.name())) {
            return "Definitions";
        }
        if (type.equals(ClauseType.ELIGIBILITY.name())) {
            return "Eligibility Rules";
        }
        if (type.equals(ClauseType.PRICING_RULE.name()) || text.contains("pricing") || text.contains("interest")) {
            return "Pricing Rules";
        }
        if (type.equals(ClauseType.LIMIT_RULE.name()) || text.contains("limit") || text.contains("foir")
                || text.contains("exposure")) {
            return "Limit Rules";
        }
        if (type.equals(ClauseType.AUTHORITY_RULE.name()) || text.contains("authority") || text.contains("delegation")) {
            return "Authority Rules";
        }
        if (type.equals(ClauseType.EXCEPTION.name()) || type.equals(ClauseType.EXCLUSION.name())
                || text.contains("exception")) {
            return "Exceptions";
        }
        if (type.equals(ClauseType.INFORMATION_REQUIREMENT.name()) || text.contains("document")
                || text.contains("information")) {
            return "Information Requirements";
        }
        if (section.contains("bureau") || text.contains("cibil") || text.contains("bureau") || text.contains("dpd")
                || text.contains("write-off") || text.contains("write off")) {
            return "Bureau Rules";
        }
        if (section.contains("gst") || text.contains(" gst") || text.contains("gstr")) {
            return "GST Rules";
        }
        if (section.contains("itr") || text.contains("itr") || text.contains("tax") || text.contains("form 26")) {
            return "Tax / ITR Rules";
        }
        if (section.contains("bank") || text.contains("banking") || text.contains("abb") || text.contains("turnover")
                || text.contains("emi bounce") || text.contains("cheque")) {
            return "Banking Rules";
        }
        if (type.equals(ClauseType.HARD_RULE.name()) || type.equals(ClauseType.SOFT_RULE.name())
                || type.equals(ClauseType.DECISION_RULE.name()) || type.equals(ClauseType.REFERRAL_RULE.name())) {
            return "Eligibility Rules";
        }
        return "Definitions";
    }

    private static String clauseTitle(CiPolicyClause c) {
        if (c.getClauseNumber() != null && !c.getClauseNumber().isBlank()) {
            return c.getClauseNumber() + (c.getProductScope() == null ? "" : " — " + c.getProductScope());
        }
        if (c.getProductScope() != null && !c.getProductScope().isBlank()) {
            return c.getProductScope();
        }
        String raw = c.getSourceText() == null ? "Clause" : c.getSourceText().strip();
        return raw.length() > 80 ? raw.substring(0, 80) + "…" : raw;
    }

    private static String friendlyClauseType(String type) {
        if (type == null) {
            return "General";
        }
        return type.replace('_', ' ').toLowerCase(Locale.ROOT);
    }

    private static String friendlyStatus(String status) {
        if (status == null) {
            return "Uploaded";
        }
        return switch (status) {
            case "UPLOADED" -> "Uploaded";
            case "PARSING", "PARSED" -> "Parsed";
            case "INTERPRETING" -> "Interpreted";
            case "REVIEW_REQUIRED" -> "Review required";
            case "DRAFT_READY" -> "Ready for draft policy";
            case "APPROVED_FOR_POLICY_BUILD" -> "Approved for draft build";
            case "REJECTED" -> "Rejected";
            case "SUPERSEDED" -> "Superseded";
            default -> status.replace('_', ' ');
        };
    }

    private static boolean clauseInSection(java.util.UUID clauseId, List<CiPolicyClause> clauses) {
        if (clauseId == null) {
            return false;
        }
        for (CiPolicyClause c : clauses) {
            if (clauseId.equals(c.getId())) {
                return true;
            }
        }
        return false;
    }

    private static List<Map<String, Object>> summarizeClauses(List<CiPolicyClause> clauses) {
        List<Map<String, Object>> out = new ArrayList<>();
        int i = 0;
        for (CiPolicyClause c : clauses) {
            if (i++ >= 40) {
                break;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("section", c.getSection());
            m.put("clauseType", c.getClauseType());
            m.put("productScope", c.getProductScope());
            String raw = c.getSourceText();
            m.put("rawTextPreview", raw == null ? null
                    : (raw.length() > 180 ? raw.substring(0, 180) + "…" : raw));
            out.add(m);
        }
        return out;
    }

    private static List<Map<String, Object>> summarizeAmbiguities(List<CiPolicyAmbiguity> ambiguities) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (CiPolicyAmbiguity a : ambiguities) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId() == null ? null : a.getId().toString());
            m.put("type", a.getAmbiguityType());
            m.put("phrase", a.getPhrase());
            m.put("resolutionStatus", a.getResolutionStatus());
            m.put("severity", a.getSeverity());
            out.add(m);
        }
        return out;
    }

    private static List<Map<String, Object>> summarizeRules(List<CiPolicyRuleCandidate> rules) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (CiPolicyRuleCandidate r : rules) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("systemRuleId", r.getSystemRuleId());
            m.put("ruleType", r.getRuleType());
            m.put("onMissing", r.getOnMissing());
            m.put("expression", r.getExpression());
            m.put("scope", r.getScope());
            out.add(m);
        }
        return out;
    }
}
