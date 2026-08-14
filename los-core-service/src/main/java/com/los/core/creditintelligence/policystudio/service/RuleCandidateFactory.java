package com.los.core.creditintelligence.policystudio.service;

import com.los.core.creditintelligence.decisionpolicy.DecisionPolicyDomain;
import com.los.core.creditintelligence.decisionpolicy.DecisionPolicyRuleMetadata;
import com.los.core.creditintelligence.decisionpolicy.PolicyGuardrailClass;
import com.los.core.creditintelligence.decisionpolicy.kyc.KycBusinessOutcome;
import com.los.core.creditintelligence.decisionpolicy.kyc.KycPolicyAuthoringSupport;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyClause;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyInterpretation;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.domain.ClauseType;
import com.los.core.creditintelligence.policystudio.domain.ReviewState;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Assigns deterministic systemRuleId values — never AI-invented IDs.
 * KYC-3: stamps DecisionPolicyDomain metadata; does not select providers or enqueue workflow steps.
 */
@Component
public class RuleCandidateFactory {

    public List<CiPolicyRuleCandidate> create(
            CiPolicyDocument document,
            List<CiPolicyClause> clauses,
            List<CiPolicyInterpretation> interpretations,
            PolicyClauseExtractor.FixtureKind kind) {
        Map<UUID, CiPolicyInterpretation> byClause = new LinkedHashMap<>();
        for (CiPolicyInterpretation i : interpretations) {
            byClause.put(i.getClauseId(), i);
        }
        List<CiPolicyRuleCandidate> out = new ArrayList<>();
        for (CiPolicyClause clause : clauses) {
            boolean kyc = KycPolicyAuthoringSupport.isKycClause(clause);
            if (!kyc && (ClauseType.INFORMATION_REQUIREMENT.name().equals(clause.getClauseType())
                    || ClauseType.METRIC_ADJUSTMENT.name().equals(clause.getClauseType()))) {
                continue;
            }
            CiPolicyInterpretation interp = byClause.get(clause.getId());
            if (interp == null || interp.getCandidateExpression() == null
                    || interp.getCandidateExpression().isEmpty()) {
                continue;
            }
            // Skip non-executable UNKNOWN stubs
            if ("UNKNOWN".equalsIgnoreCase(String.valueOf(interp.getCandidateExpression().get("op")))) {
                continue;
            }

            KycPolicyAuthoringSupport.Classification kycClass = kyc
                    ? KycPolicyAuthoringSupport.classify(clause.getSourceText())
                    : null;

            String systemId = kyc && kycClass != null && kycClass.kycRelated()
                    ? kycClass.systemRuleId()
                    : assignSystemRuleId(clause, kind);
            Map<String, Object> lineage = new LinkedHashMap<>();
            lineage.put("documentId", document.getId().toString());
            lineage.put("documentName", document.getName());
            lineage.put("clauseId", clause.getId().toString());
            lineage.put("clauseNumber", clause.getClauseNumber());
            lineage.put("section", clause.getSection());
            lineage.put("sourceText", clause.getSourceText());
            lineage.put("sourceLocation", clause.getSourceLocation());
            lineage.put("interpretationId", interp.getId().toString());
            lineage.put("parentClauseId", clause.getParentClauseId() == null ? null : clause.getParentClauseId().toString());

            Map<String, Object> scope = new LinkedHashMap<>(clause.getEffectiveScope() == null
                    ? Map.of() : clause.getEffectiveScope());
            if (clause.getProductScope() != null) {
                scope.putIfAbsent("products", List.of(clause.getProductScope()));
            }

            String onTrue;
            String onFalse;
            String onMissing;
            if (kyc && kycClass != null && kycClass.kycRelated()) {
                // Condition true → success outcome; false → failure disposition
                onTrue = kycClass.onSuccess();
                onFalse = kycClass.onFailure();
                onMissing = kycClass.onMissing();
                scope.put(DecisionPolicyRuleMetadata.KEY_DOMAIN, kycClass.domain().name());
            } else {
                onTrue = "FAIL";
                onFalse = "PASS";
                onMissing = "DATA_INSUFFICIENT";
                if (isEligibilityPassExpression(clause)) {
                    onTrue = "PASS";
                    onFalse = "FAIL";
                }
                scope.putIfAbsent(DecisionPolicyRuleMetadata.KEY_DOMAIN, DecisionPolicyDomain.CREDIT.name());
            }

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("provider", DeterministicGoldenInterpretationProvider.PROVIDER_CODE);
            metadata.put(DecisionPolicyRuleMetadata.KEY_POLICY_SELECTS_PROVIDER, false);
            metadata.put(DecisionPolicyRuleMetadata.KEY_POLICY_ENQUEUES_WORKFLOW, false);
            metadata.put("authoringOnly", true);
            metadata.put("allowCanonicalAuthority", false);
            if (kyc && kycClass != null && kycClass.kycRelated()) {
                metadata = DecisionPolicyRuleMetadata.stamp(
                        metadata,
                        kycClass.domain(),
                        kycClass.requirementType(),
                        kycClass.guardrailClass());
                metadata.put("businessTitle", kycClass.businessTitle());
                metadata.put("outcomeOnSuccess", kycClass.onSuccess());
                metadata.put("outcomeOnFailure", kycClass.onFailure());
                metadata.put("outcomeOnMissing", kycClass.onMissing());
                metadata.put("kycFactPaths", kycClass.factPaths());
                metadata.put("matchCapabilityMissing", kycClass.matchCapabilityMissing());
                metadata.put("unsupportedCapability", kycClass.unsupportedCapability());
                metadata.put("studioEditable", DecisionPolicyRuleMetadata.isStudioEditable(metadata));
                // Map MISSING_INFORMATION to DSL onMissing vocabulary
                if (KycBusinessOutcome.MISSING_INFORMATION.name().equals(onMissing)) {
                    onMissing = "DATA_INSUFFICIENT";
                }
            } else {
                metadata = DecisionPolicyRuleMetadata.stamp(
                        metadata,
                        DecisionPolicyDomain.CREDIT,
                        null,
                        PolicyGuardrailClass.UNRESOLVED);
            }

            out.add(CiPolicyRuleCandidate.builder()
                    .id(UUID.randomUUID())
                    .clauseId(clause.getId())
                    .systemRuleId(systemId)
                    .ruleVersion("DRAFT")
                    .ruleType("HARD")
                    .scope(scope)
                    .expression(interp.getCandidateExpression())
                    .onTrue(onTrue)
                    .onFalse(onFalse)
                    .onMissing(onMissing)
                    .confidence(interp.getConfidence() != null ? interp.getConfidence() : new BigDecimal("0.8000"))
                    .reviewStatus(ReviewState.AI_DRAFTED.name())
                    .lineage(lineage)
                    .metadata(metadata)
                    .build());
        }
        return out;
    }

    private boolean isEligibilityPassExpression(CiPolicyClause clause) {
        String lower = clause.getSourceText().toLowerCase(Locale.ROOT);
        if (lower.contains("inward") && (lower.contains("return") || lower.contains("cheque"))) {
            return true;
        }
        return lower.contains("equal or above") || lower.contains("must be minimum")
                || lower.contains("650") || (clause.getParentClauseId() != null);
    }

    static String assignSystemRuleId(CiPolicyClause clause, PolicyClauseExtractor.FixtureKind kind) {
        String lower = clause.getSourceText().toLowerCase(Locale.ROOT);
        if (kind == PolicyClauseExtractor.FixtureKind.BANKING_BRE) {
            if (lower.contains("starter")) {
                return "BANK_STARTER_ADB_GTE_EDI";
            }
            if ((lower.contains("digileap") || lower.contains("digi leap")) && lower.contains("transaction")) {
                return "BANK_DIGILEAP_TXN_GTE_20";
            }
            if (lower.contains("digileap") || lower.contains("digi leap")) {
                return "BANK_DIGILEAP_ADB_DIV5_GTE_EDI";
            }
            if (lower.contains("smart switch") && (lower.contains("number") || lower.contains("count")
                    || lower.contains("minimum of 20 settlements") || lower.contains("settlements"))) {
                if (lower.contains("settlement") && !lower.contains("equal or above the proposed")) {
                    return "BANK_SMART_SWITCH_SETTLEMENT_COUNT_GTE_20";
                }
            }
            if (lower.contains("smart switch")) {
                return "BANK_SMART_SWITCH_SETTLEMENT_DIV10_GTE_EDI";
            }
            if ((lower.contains("reboost") || lower.contains("re boost")) && lower.contains("transaction")) {
                return "BANK_REBOOST_TXN_GTE_30_IF_AMT_GT_60000";
            }
            if (lower.contains("reboost") || lower.contains("re boost")) {
                return "BANK_REBOOST_ADB_DIV5_GTE_EDI_IF_AMT_GT_60000";
            }
            if (lower.contains("inward")) {
                return "BANK_INWARD_RETURN_BRANCHED_100";
            }
            return "BANK_RULE_" + Math.abs(clause.getSourceText().hashCode());
        }
        if (kind == PolicyClauseExtractor.FixtureKind.BUREAU_BRE) {
            if (clause.getParentClauseId() != null) {
                return "BUREAU_OVERDUE_CHILD_" + clause.getClauseNumber();
            }
            if (lower.contains("score")) {
                if (lower.contains("-1") || lower.contains("ntc") || lower.contains("thin file")
                        || lower.contains("thin-file") || lower.contains("new to credit")) {
                    return "BUREAU_SCORE_OR_NTC_OR_GTE_650";
                }
                return "BUREAU_MIN_SCORE";
            }
            if (lower.contains("write-off") || lower.contains("write off")) {
                return "BUREAU_NO_WRITEOFF_EXCEPT_CC";
            }
            if (lower.contains("overdue rule")) {
                return "BUREAU_OVERDUE_EXCEPTION_PARENT";
            }
            if (lower.contains("no loan overdue")) {
                return "BUREAU_NO_OVERDUE_EXCEPT_DOCUMENTED";
            }
            if (lower.contains("credit card overdue") || lower.contains("overdue amounts greater")) {
                return "BUREAU_CC_OVERDUE_GT_5000";
            }
            if (lower.contains("dpd") || lower.contains("days past due")) {
                return "BUREAU_DPD_LAST_6M";
            }
            if (lower.contains("settled") || lower.contains("restructured")) {
                return "BUREAU_SETTLED_OR_RESTRUCTURED";
            }
            if (lower.contains("legal suit")) {
                return "BUREAU_LEGAL_SUIT";
            }
            if (lower.contains("dbt") || lower.contains("pwos") || lower.contains("lss")) {
                return "BUREAU_DBT_PWOS_LSS";
            }
            if (lower.contains("pan")) {
                return "BUREAU_MULTIPLE_PAN";
            }
            if (lower.contains("inquir") || lower.contains("enquir")) {
                return "BUREAU_INQUIRIES_CURRENT_MONTH";
            }
            if (lower.contains("account sold")) {
                return "BUREAU_ACCOUNT_SOLD";
            }
            return "BUREAU_RULE_" + Math.abs(clause.getSourceText().hashCode());
        }
        if (kind == PolicyClauseExtractor.FixtureKind.KYC_BRE
                || KycPolicyAuthoringSupport.isKycClause(clause)) {
            KycPolicyAuthoringSupport.Classification c = KycPolicyAuthoringSupport.classify(clause.getSourceText());
            if (c.kycRelated() && c.systemRuleId() != null) {
                return c.systemRuleId();
            }
            return "KYC_RULE_" + Math.abs(clause.getSourceText().hashCode());
        }
        return "POLICY_RULE_" + Math.abs(clause.getSourceText().hashCode());
    }
}
