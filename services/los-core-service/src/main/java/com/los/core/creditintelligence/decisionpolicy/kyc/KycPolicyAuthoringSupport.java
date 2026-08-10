package com.los.core.creditintelligence.decisionpolicy.kyc;

import com.los.core.creditintelligence.decisionpolicy.DecisionPolicyDomain;
import com.los.core.creditintelligence.decisionpolicy.DecisionPolicyRuleMetadata;
import com.los.core.creditintelligence.decisionpolicy.DecisionPolicyStages;
import com.los.core.creditintelligence.decisionpolicy.KycRequirementType;
import com.los.core.creditintelligence.decisionpolicy.PolicyGuardrailClass;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyClause;
import com.los.core.creditintelligence.policystudio.domain.ClauseType;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDsl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * KYC-3 authoring helpers — classify plain-English KYC/eligibility clauses and
 * build existing Policy DSL over normalized KYC facts.
 *
 * <p>Does not select providers, enqueue workflow steps, or change production KYC runtime.
 */
public final class KycPolicyAuthoringSupport {

    private static final Pattern AMOUNT_LAKH = Pattern.compile(
            "(?:above|over|exceeds?|greater than|>)\\s*(?:₹|rs\\.?|inr)?\\s*([0-9]+(?:\\.[0-9]+)?)\\s*lakh",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern AMOUNT_NUMBER = Pattern.compile(
            "(?:above|over|exceeds?|greater than|>)\\s*(?:₹|rs\\.?|inr)?\\s*([0-9][0-9,]*)",
            Pattern.CASE_INSENSITIVE);

    private KycPolicyAuthoringSupport() {}

    public record Classification(
            boolean kycRelated,
            DecisionPolicyDomain domain,
            KycRequirementType requirementType,
            PolicyGuardrailClass guardrailClass,
            String primaryFact,
            List<String> factPaths,
            List<String> applicationFields,
            String systemRuleId,
            String businessTitle,
            String onSuccess,
            String onFailure,
            String onMissing,
            boolean matchCapabilityMissing,
            boolean unsupportedCapability,
            Map<String, Object> dslExpression,
            String naturalLanguageMeaning,
            Map<String, Object> scopeHints
    ) {}

    /** True when source text looks like a KYC/eligibility demo or section. */
    public static boolean looksLikeKycPolicyDocument(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String t = text.toLowerCase(Locale.ROOT);
        return t.contains("kyc & eligibility")
                || t.contains("kyc and eligibility")
                || t.contains("identity verification policy")
                || (t.contains("demo policy") && (t.contains("kyc") || t.contains("video kyc") || t.contains("pan must")))
                || (t.contains("validation sample") && t.contains("kyc"));
    }

    public static boolean looksLikeKycClause(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        // Avoid tagging pure bureau "multiple PAN" / Permanent Account Number credit-report rules as KYC
        if (lower.contains("multiple pan")
                || lower.contains("multiple permanent account")
                || (lower.contains("credit report") && containsWord(lower, "pan"))
                || (lower.contains("bureau") && containsWord(lower, "pan"))) {
            return false;
        }
        return lower.contains("kyc")
                || containsWord(lower, "pan")
                || lower.contains("ckyc")
                || lower.contains("aadhaar")
                || lower.contains("aadhar")
                || lower.contains("vkyc")
                || lower.contains("video kyc")
                || lower.contains("physical kyc")
                || lower.contains("pkyc")
                || lower.contains("gstin")
                || lower.contains(" cin")
                || lower.startsWith("cin ")
                || lower.contains("udyam")
                || lower.contains("penny")
                || lower.contains("bank account")
                || lower.contains("name match")
                || lower.contains("name differs")
                || lower.contains("name mismatch")
                || lower.contains("authorised signatory")
                || lower.contains("authorized signatory")
                || lower.contains("pep")
                || lower.contains("sanctions")
                || lower.contains("ubo");
    }

    public static Classification classify(String sourceText) {
        String text = sourceText == null ? "" : sourceText.trim();
        String lower = text.toLowerCase(Locale.ROOT);
        if (!looksLikeKycClause(text)) {
            return nonKyc();
        }

        DecisionPolicyDomain domain = lower.contains("eligib") || lower.contains("must not proceed")
                || lower.contains("underwriting must not")
                ? DecisionPolicyDomain.ELIGIBILITY
                : DecisionPolicyDomain.KYC;

        PolicyGuardrailClass guardrail = PolicyGuardrailClass.UNRESOLVED;
        if (lower.contains("platform guardrail") || lower.contains("non-editable")
                || lower.contains("regulatory guardrail")) {
            guardrail = PolicyGuardrailClass.PLATFORM_GUARDRAIL;
        } else if (lower.contains("nbfc may configure") || lower.contains("lender configurable")) {
            guardrail = PolicyGuardrailClass.NBFC_CONFIGURABLE;
        }

        boolean unsupported = lower.contains("pep") || lower.contains("sanctions") || lower.contains("ubo")
                || lower.contains("authorised signatory") || lower.contains("authorized signatory");
        boolean match = lower.contains("name match") || lower.contains("name differs")
                || lower.contains("name mismatch") || lower.contains("must match")
                || (lower.contains("address") && lower.contains("match"));
        boolean manual = lower.contains("refer") || lower.contains("manual")
                || lower.contains("reviewer") || lower.contains("cannot be resolved");
        boolean boundary = lower.contains("above") || lower.contains("exceed")
                || lower.contains("greater than") || lower.contains("lakh");
        boolean completion = lower.contains("completed") || lower.contains("completion")
                || lower.contains("video kyc") || lower.contains("vkyc") || lower.contains("physical kyc");
        boolean information = lower.contains("available") || lower.contains("must be present")
                || lower.contains("must have") && (lower.contains("gstin") || lower.contains("cin") || lower.contains("pan"));
        boolean eligibility = domain == DecisionPolicyDomain.ELIGIBILITY
                || (lower.contains("company") && (lower.contains("cin") || lower.contains("gstin")));

        KycRequirementType reqType;
        if (guardrail == PolicyGuardrailClass.PLATFORM_GUARDRAIL) {
            reqType = KycRequirementType.REGULATORY_GUARDRAIL;
        } else if (unsupported && !match) {
            reqType = KycRequirementType.VERIFICATION;
        } else if (match && manual) {
            reqType = KycRequirementType.MANUAL_VERIFICATION;
        } else if (match) {
            reqType = KycRequirementType.MATCH_REQUIREMENT;
        } else if (manual) {
            reqType = KycRequirementType.MANUAL_VERIFICATION;
        } else if (boundary && (lower.contains("vkyc") || lower.contains("video kyc"))) {
            reqType = KycRequirementType.BOUNDARY_CONDITION;
        } else if (eligibility && (lower.contains("company") || lower.contains("cin") || lower.contains("gstin"))) {
            reqType = KycRequirementType.ELIGIBILITY_CONDITION;
        } else if (completion) {
            reqType = KycRequirementType.COMPLETION_REQUIREMENT;
        } else if (information && !lower.contains("verif")) {
            reqType = KycRequirementType.INFORMATION_REQUIREMENT;
        } else {
            reqType = KycRequirementType.VERIFICATION;
        }

        List<String> facts = new ArrayList<>();
        List<String> appFields = new ArrayList<>();
        String primary = null;
        String title = "KYC Requirement";
        String systemId = "KYC_RULE_" + Math.abs(text.hashCode());
        Map<String, Object> scope = new LinkedHashMap<>();
        boolean matchMissing = false;

        if (unsupported) {
            primary = lower.contains("pep") ? "kyc.pep.screened"
                    : lower.contains("ubo") ? "kyc.ubo.verified"
                    : lower.contains("sanction") ? "kyc.sanctions.cleared"
                    : "kyc.authorised_signatory.verified";
            facts.add(primary);
            title = "Unsupported KYC capability";
            systemId = "KYC_UNSUPPORTED_" + primary.replace('.', '_').toUpperCase(Locale.ROOT);
        } else if (lower.contains("company") && (containsWord(lower, "cin") || lower.contains("gstin"))) {
            domain = DecisionPolicyDomain.ELIGIBILITY;
            reqType = KycRequirementType.ELIGIBILITY_CONDITION;
            facts.add("kyc.cin.verified");
            facts.add("kyc.gstin.verified");
            appFields.add("borrower_type");
            primary = "kyc.cin.verified";
            title = "Company CIN & GSTIN";
            systemId = "KYC_COMPANY_CIN_GSTIN";
            scope.put("borrowerTypes", List.of("COMPANY"));
        } else if (containsWord(lower, "pan") && match) {
            primary = "kyc.pan.name_match";
            facts.add(primary);
            facts.add("kyc.pan.verified");
            appFields.add("application.applicant_name");
            title = "PAN Name Match";
            systemId = "KYC_PAN_NAME_MATCH";
            matchMissing = true; // production name-match capability not proven
        } else if (containsWord(lower, "pan")) {
            primary = lower.contains("available") || lower.contains("present")
                    ? "kyc.pan.present" : "kyc.pan.verified";
            facts.add(primary);
            title = primary.endsWith("present") ? "PAN Present" : "PAN Verification";
            systemId = primary.endsWith("present") ? "KYC_PAN_PRESENT" : "KYC_PAN_VERIFIED";
        } else if (lower.contains("ckyc")) {
            primary = lower.contains("available") ? "kyc.ckyc.available" : "kyc.ckyc.verified";
            facts.add(primary);
            title = "CKYC";
            systemId = "KYC_CKYC";
        } else if (lower.contains("aadhaar") || lower.contains("aadhar")) {
            primary = "kyc.aadhaar.verified";
            facts.add(primary);
            title = "Aadhaar Verification";
            systemId = "KYC_AADHAAR_VERIFIED";
        } else if (lower.contains("gstin")) {
            primary = lower.contains("available") || lower.contains("present")
                    ? "kyc.gstin.present" : "kyc.gstin.verified";
            facts.add(primary);
            title = "GSTIN";
            systemId = "KYC_GSTIN";
        } else if (lower.contains("cin") || lower.contains("mca")) {
            primary = lower.contains("available") || lower.contains("present")
                    ? "kyc.cin.present" : "kyc.cin.verified";
            facts.add(primary);
            title = "CIN / MCA";
            systemId = "KYC_CIN";
        } else if (lower.contains("udyam")) {
            primary = "kyc.udyam.verified";
            facts.add(primary);
            title = "Udyam Verification";
            systemId = "KYC_UDYAM_VERIFIED";
        } else if (lower.contains("bank account") || lower.contains("penny")) {
            primary = "kyc.bank_account.verified";
            facts.add(primary);
            title = "Bank Account Verification";
            systemId = "KYC_BANK_ACCOUNT_VERIFIED";
        } else if (lower.contains("physical kyc") || lower.contains("pkyc")) {
            primary = "kyc.pkyc.completed";
            facts.add(primary);
            title = "Physical KYC";
            systemId = "KYC_PKYC_COMPLETED";
        } else if (lower.contains("vkyc") || lower.contains("video kyc")) {
            primary = "kyc.vkyc.completed";
            facts.add(primary);
            title = "Video KYC";
            systemId = boundary ? "KYC_VKYC_AMOUNT_BOUNDARY" : "KYC_VKYC_COMPLETED";
            Long amount = parseAmountThreshold(lower);
            if (amount != null) {
                scope.put("requestedAmount", Map.of("op", "GT", "value", amount));
                appFields.add("requested_amount");
            }
        } else if (lower.contains("incomplete") || lower.contains("underwriting must not")) {
            primary = "kyc.overall.outcome";
            facts.add(primary);
            domain = DecisionPolicyDomain.ELIGIBILITY;
            reqType = KycRequirementType.ELIGIBILITY_CONDITION;
            title = "KYC Complete Before Underwriting";
            systemId = "KYC_OVERALL_BEFORE_UW";
        }

        String onSuccess = KycBusinessOutcome.PASS.name();
        String onFailure = KycBusinessOutcome.FAIL.name();
        String onMissing = KycBusinessOutcome.MISSING_INFORMATION.name();
        if (reqType == KycRequirementType.MANUAL_VERIFICATION || (match && manual)) {
            onFailure = KycBusinessOutcome.REFER.name();
            onSuccess = KycBusinessOutcome.PASS.name();
        }
        if (match && !manual) {
            onFailure = KycBusinessOutcome.REFER.name();
        }

        Map<String, Object> dsl = buildDsl(facts, appFields, scope, reqType, unsupported, matchMissing, lower);
        String meaning = buildMeaning(title, text, reqType, onSuccess, onFailure, onMissing);

        return new Classification(
                true, domain, reqType, guardrail, primary, facts, appFields, systemId, title,
                onSuccess, onFailure, onMissing, matchMissing, unsupported, dsl, meaning, scope);
    }

    public static void enrichClause(CiPolicyClause clause) {
        if (clause == null) {
            return;
        }
        Classification c = classify(clause.getSourceText());
        if (!c.kycRelated()) {
            return;
        }
        Map<String, Object> meta = clause.getMetadata() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(clause.getMetadata());
        meta = DecisionPolicyRuleMetadata.stamp(meta, c.domain(), c.requirementType(), c.guardrailClass());
        meta.put("businessTitle", c.businessTitle());
        meta.put("outcomeOnSuccess", c.onSuccess());
        meta.put("outcomeOnFailure", c.onFailure());
        meta.put("outcomeOnMissing", c.onMissing());
        meta.put("kycFactPaths", c.factPaths());
        meta.put("matchCapabilityMissing", c.matchCapabilityMissing());
        meta.put("unsupportedCapability", c.unsupportedCapability());
        meta.put("policySelectsProvider", false);
        meta.put("policyEnqueuesWorkflowStep", false);
        meta.put("authoringOnly", true);
        clause.setMetadata(meta);

        Map<String, Object> scope = clause.getEffectiveScope() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(clause.getEffectiveScope());
        scope.put(DecisionPolicyRuleMetadata.KEY_DOMAIN, c.domain().name());
        scope.putAll(c.scopeHints());
        clause.setEffectiveScope(scope);

        if (clause.getSection() == null || "GENERIC".equalsIgnoreCase(clause.getSection())
                || clause.getSection().isBlank()) {
            clause.setSection("KYC & Eligibility");
        }
        // Executable KYC requirements are HARD_RULE; keep semantic type in metadata
        if (ClauseType.UNKNOWN.name().equals(clause.getClauseType())
                || clause.getClauseType() == null) {
            clause.setClauseType(ClauseType.HARD_RULE.name());
        }
        if (c.requirementType() == KycRequirementType.ELIGIBILITY_CONDITION) {
            clause.setClauseType(ClauseType.ELIGIBILITY.name());
        }
    }

    public static void enrichClauses(List<CiPolicyClause> clauses) {
        if (clauses == null) {
            return;
        }
        for (CiPolicyClause c : clauses) {
            enrichClause(c);
        }
    }

    public static boolean isKycClause(CiPolicyClause clause) {
        if (clause == null) {
            return false;
        }
        Object domain = clause.getMetadata() == null ? null : clause.getMetadata().get(DecisionPolicyRuleMetadata.KEY_DOMAIN);
        if (domain != null) {
            DecisionPolicyDomain d = DecisionPolicyDomain.fromMetadata(domain);
            return d.isKycOrEligibility();
        }
        return looksLikeKycClause(clause.getSourceText());
    }

    private static Classification nonKyc() {
        return new Classification(
                false, DecisionPolicyDomain.CREDIT, null, PolicyGuardrailClass.UNRESOLVED,
                null, List.of(), List.of(), null, null, null, null, null,
                false, false, Map.of(), null, Map.of());
    }

    /** Avoid matching PAN inside "company". */
    private static boolean containsWord(String lower, String word) {
        if (lower == null || word == null || word.isBlank()) {
            return false;
        }
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b", Pattern.CASE_INSENSITIVE)
                .matcher(lower)
                .find();
    }

    private static Long parseAmountThreshold(String lower) {
        Matcher lakh = AMOUNT_LAKH.matcher(lower);
        if (lakh.find()) {
            double v = Double.parseDouble(lakh.group(1));
            return Math.round(v * 100_000L);
        }
        Matcher num = AMOUNT_NUMBER.matcher(lower);
        if (num.find()) {
            String raw = num.group(1).replace(",", "");
            try {
                return Long.parseLong(raw);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private static Map<String, Object> buildDsl(
            List<String> facts,
            List<String> appFields,
            Map<String, Object> scope,
            KycRequirementType reqType,
            boolean unsupported,
            boolean matchMissing,
            String lower
    ) {
        if (unsupported || matchMissing) {
            // Intentionally unmapped executable path — Data Readiness will flag MAPPING/SOURCE required
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("op", "EXISTS");
            m.put("arg", PolicyDsl.fact(facts.isEmpty() ? "kyc.unsupported" : facts.get(0)));
            m.put("mappingStatus", matchMissing ? "MATCH_CAPABILITY_REQUIRED" : "DATA_SOURCE_REQUIRED");
            return m;
        }
        if (facts.contains("kyc.cin.verified") && facts.contains("kyc.gstin.verified")) {
            return PolicyDsl.and(
                    PolicyDsl.eq(PolicyDsl.appField("borrower_type"), Map.of("const", "COMPANY")),
                    PolicyDsl.eq(PolicyDsl.fact("kyc.cin.verified"), Map.of("const", true)),
                    PolicyDsl.eq(PolicyDsl.fact("kyc.gstin.verified"), Map.of("const", true))
            );
        }
        if (facts.contains("kyc.vkyc.completed") && scope.get("requestedAmount") instanceof Map<?, ?> amt) {
            Object value = amt.get("value");
            return PolicyDsl.and(
                    PolicyDsl.gt(PolicyDsl.appField("requested_amount"), Map.of("const", value)),
                    PolicyDsl.eq(PolicyDsl.fact("kyc.vkyc.completed"), Map.of("const", true))
            );
        }
        if (reqType == KycRequirementType.MANUAL_VERIFICATION
                || (facts.contains("kyc.pan.name_match") && lower.contains("refer"))) {
            return PolicyDsl.iff(
                    PolicyDsl.eq(PolicyDsl.fact("kyc.pan.name_match"), Map.of("const", false)),
                    Map.of("const", "REFER"),
                    Map.of("const", "PASS")
            );
        }
        if (facts.contains("kyc.overall.outcome")) {
            return PolicyDsl.eq(PolicyDsl.fact("kyc.overall.outcome"), Map.of("const", "PASS"));
        }
        if (!facts.isEmpty()) {
            String fact = facts.get(0);
            if (fact.endsWith(".present") || fact.endsWith(".available") || fact.endsWith(".completed")
                    || fact.endsWith(".verified")) {
                return PolicyDsl.eq(PolicyDsl.fact(fact), Map.of("const", true));
            }
            return PolicyDsl.exists(PolicyDsl.fact(fact));
        }
        return Map.of("op", "UNKNOWN", "source", "kyc");
    }

    private static String buildMeaning(
            String title,
            String text,
            KycRequirementType reqType,
            String onSuccess,
            String onFailure,
            String onMissing
    ) {
        return title + ": " + text
                + " [type=" + reqType.name()
                + "; success=" + onSuccess
                + "; failure=" + onFailure
                + "; unavailable=" + onMissing
                + "; stage=" + DecisionPolicyStages.KYC_ELIGIBILITY
                + "; providerSelection=false; workflowEnqueue=false]";
    }
}
