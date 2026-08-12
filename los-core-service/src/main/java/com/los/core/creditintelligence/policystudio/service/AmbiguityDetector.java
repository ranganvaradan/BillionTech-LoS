package com.los.core.creditintelligence.policystudio.service;

import com.los.core.creditintelligence.decisionpolicy.kyc.KycPolicyAuthoringSupport;
import com.los.core.creditintelligence.policystudio.domain.AmbiguityType;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyAmbiguity;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyClause;
import com.los.core.creditintelligence.validation.service.PolicyAuthoringRegistry;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
public class AmbiguityDetector {

    private final PolicyAuthoringRegistry registry;

    public AmbiguityDetector(PolicyAuthoringRegistry registry) {
        this.registry = registry;
    }

    public AmbiguityDetector() {
        this(new PolicyAuthoringRegistry());
    }

    public List<CiPolicyAmbiguity> detect(List<CiPolicyClause> clauses, PolicyClauseExtractor.FixtureKind kind) {
        List<CiPolicyAmbiguity> out = new ArrayList<>();
        if (kind == PolicyClauseExtractor.FixtureKind.BANKING_BRE) {
            detectBanking(clauses, out);
        } else if (kind == PolicyClauseExtractor.FixtureKind.BUREAU_BRE) {
            detectBureau(clauses, out);
        } else if (kind == PolicyClauseExtractor.FixtureKind.KYC_BRE) {
            detectKyc(clauses, out);
        } else if (kind == PolicyClauseExtractor.FixtureKind.GENERIC) {
            // Mixed uploads: only KYC-tagged clauses
            List<CiPolicyClause> kycOnly = clauses.stream()
                    .filter(KycPolicyAuthoringSupport::isKycClause)
                    .toList();
            if (!kycOnly.isEmpty()) {
                detectKyc(kycOnly, out);
            }
        }
        return out;
    }

    private void detectKyc(List<CiPolicyClause> clauses, List<CiPolicyAmbiguity> out) {
        boolean validPan = false;
        boolean materialMismatch = false;
        boolean successfulVkyc = false;
        boolean addressMatch = false;
        boolean highRiskGeo = false;
        boolean authorisedSignatory = false;
        boolean acceptableCkyc = false;
        boolean amountBoundary = false;
        boolean substantialMismatch = false;
        boolean failureDisposition = false;

        for (CiPolicyClause c : clauses) {
            String lower = text(c).toLowerCase(Locale.ROOT);
            KycPolicyAuthoringSupport.Classification cl = KycPolicyAuthoringSupport.classify(text(c));

            if (!validPan && lower.contains("valid pan")) {
                out.add(amb(c, AmbiguityType.UNKNOWN_BUSINESS_TERM, "valid PAN",
                        "What constitutes a valid PAN — format-only vs successful provider verification — needs confirmation",
                        List.of("FORMAT_ONLY", "PROVIDER_VERIFIED", "ASK_CUSTOMER"),
                        "PROVIDER_VERIFIED"));
                validPan = true;
            }
            if (!materialMismatch && (lower.contains("material") && lower.contains("mismatch"))) {
                out.add(amb(c, AmbiguityType.BOUNDARY_AMBIGUITY, "material name mismatch",
                        "Material mismatch threshold is undefined — confirm match tolerance before executable mapping",
                        List.of("EXACT_MATCH", "FUZZY_THRESHOLD", "MANUAL_REVIEW_ALWAYS", "ASK_CUSTOMER"),
                        "ASK_CUSTOMER"));
                materialMismatch = true;
            }
            if (!substantialMismatch && lower.contains("substantial mismatch")) {
                out.add(amb(c, AmbiguityType.BOUNDARY_AMBIGUITY, "substantial mismatch",
                        "Substantial mismatch is undefined — confirm when auto-fail vs refer",
                        List.of("REFER", "FAIL", "ASK_CUSTOMER"),
                        "ASK_CUSTOMER"));
                substantialMismatch = true;
            }
            if (!successfulVkyc && (lower.contains("successful vkyc") || lower.contains("successful video kyc"))) {
                out.add(amb(c, AmbiguityType.UNKNOWN_BUSINESS_TERM, "successful VKYC",
                        "Confirm what status counts as completed VKYC (COMPLETED / APPROVED / SUCCESS)",
                        List.of("STATUS_COMPLETED", "STATUS_APPROVED", "ASK_CUSTOMER"),
                        "STATUS_COMPLETED"));
                successfulVkyc = true;
            }
            if (!addressMatch && lower.contains("acceptable address")) {
                out.add(amb(c, AmbiguityType.UNKNOWN_BUSINESS_TERM, "acceptable address match",
                        "Address match capability / tolerance is not confirmed in repository evidence",
                        List.of("EXACT", "FUZZY", "MANUAL_REVIEW", "ASK_CUSTOMER"),
                        "ASK_CUSTOMER"));
                addressMatch = true;
            }
            if (!highRiskGeo && lower.contains("high-risk geography")) {
                out.add(amb(c, AmbiguityType.UNCLEAR_SCOPE, "high-risk geography",
                        "Geography list / applicability is undefined",
                        List.of("DEFINE_REGION_LIST", "ASK_CUSTOMER"),
                        "ASK_CUSTOMER"));
                highRiskGeo = true;
            }
            if (!authorisedSignatory && (lower.contains("authorised signatory") || lower.contains("authorized signatory"))) {
                out.add(amb(c, AmbiguityType.UNSUPPORTED_DATA, "company authorised signatory",
                        "Authorised signatory verification is not proven available — DATA_SOURCE_REQUIRED",
                        List.of("ENGINEERING_REQUIRED", "MANUAL_VERIFICATION", "ASK_CUSTOMER"),
                        "ENGINEERING_REQUIRED"));
                authorisedSignatory = true;
            }
            if (!acceptableCkyc && lower.contains("acceptable ckyc")) {
                out.add(amb(c, AmbiguityType.UNKNOWN_BUSINESS_TERM, "acceptable CKYC",
                        "Confirm CKYC acceptance criteria (download success vs full match)",
                        List.of("DOWNLOAD_SUCCESS", "FULL_MATCH", "ASK_CUSTOMER"),
                        "ASK_CUSTOMER"));
                acceptableCkyc = true;
            }
            if (!amountBoundary && (lower.contains("lakh") || lower.contains("500000") || lower.contains("5,00,000"))) {
                out.add(amb(c, AmbiguityType.BOUNDARY_AMBIGUITY, "loan above ₹5 lakh",
                        "Confirm inclusive/exclusive threshold and currency normalisation for VKYC boundary",
                        List.of("GT_500000", "GTE_500000", "ASK_CUSTOMER"),
                        "GT_500000"));
                amountBoundary = true;
            }
            if (!failureDisposition && cl.kycRelated()
                    && (lower.contains("fail") || lower.contains("reject"))
                    && !lower.contains("refer") && !lower.contains("missing")) {
                out.add(amb(c, AmbiguityType.UNCLEAR_OPERATOR, "failure disposition",
                        "Confirm whether unverified KYC is FAIL vs MISSING_INFORMATION vs REFER when evidence is inconclusive",
                        List.of("FAIL", "MISSING_INFORMATION", "REFER", "ASK_CUSTOMER"),
                        "ASK_CUSTOMER"));
                failureDisposition = true;
            }
            if (cl.matchCapabilityMissing()) {
                out.add(amb(c, AmbiguityType.MISSING_METRIC, "PAN name match capability",
                        "Name-match requirement present but dedicated production match capability is not proven — MATCH CAPABILITY REQUIRED",
                        List.of("MATCH_CAPABILITY_REQUIRED", "MANUAL_VERIFICATION", "ASK_CUSTOMER"),
                        "MATCH_CAPABILITY_REQUIRED"));
            }
            if (cl.unsupportedCapability()) {
                out.add(amb(c, AmbiguityType.UNSUPPORTED_DATA, cl.primaryFact() == null ? "unsupported KYC" : cl.primaryFact(),
                        "Capability not proven by repository evidence — must not be marked AVAILABLE",
                        List.of("DATA_SOURCE_REQUIRED", "MAPPING_REQUIRED", "ASK_CUSTOMER"),
                        "DATA_SOURCE_REQUIRED"));
            }
        }
    }

    private void detectBanking(List<CiPolicyClause> clauses, List<CiPolicyAmbiguity> out) {
        boolean edi = false;
        boolean avgTxn = false;
        boolean boundary100 = false;
        boolean depositions = false;
        boolean settlement = false;
        boolean gaming = false;

        for (CiPolicyClause c : clauses) {
            String lower = text(c).toLowerCase(Locale.ROOT);
            // GATE2: token-safe — "credit".contains("edi") must NOT create Proposed EDI ambiguity
            if (!edi && com.los.core.creditintelligence.policystudio.parameters.BusinessConceptMatching
                    .isProposedEdiPhrase(lower)
                    && !com.los.core.creditintelligence.policystudio.parameters.BusinessConceptMatching
                    .isWriteOffPhrase(lower)) {
                out.add(amb(c, AmbiguityType.UNKNOWN_BUSINESS_TERM, "EDI",
                        "EDI is not a guaranteed canonical term; candidate application.proposed_edi requires customer confirmation",
                        List.of(
                                "application.proposed_edi",
                                "eligible_disposable_income",
                                "proposed_emi",
                                "CREATE_POLICY_PARAMETER",
                                "EXISTING_LENDER_PARAMETER",
                                "OTHER"),
                        "application.proposed_edi"));
                edi = true;
            }
            if (!avgTxn && lower.contains("average monthly transaction")) {
                out.add(amb(c, AmbiguityType.MULTIPLE_CANONICAL_MATCHES, "Average monthly transactions",
                        "Multiple transaction-count metrics may match",
                        List.of(
                                "banking.transaction_count.average_monthly_3m",
                                "banking.business_transaction_count.average_monthly_3m",
                                "banking.credit_transaction_count.average_monthly_3m",
                                "CREATE NEW METRIC"),
                        "banking.transaction_count.average_monthly_3m"));
                avgTxn = true;
            }
            if (!boundary100 && lower.contains("more than 100") && lower.contains("less than 100")) {
                out.add(amb(c, AmbiguityType.BOUNDARY_AMBIGUITY, "exactly 100 transactions",
                        "Source says more than 100 / less than 100 — leaves exactly 100 unresolved; do not silently decide",
                        List.of(
                                "treat_100_as_ratio_branch",
                                "treat_100_as_count_branch",
                                "create_separate_rule_for_100",
                                "ASK_CUSTOMER"),
                        "ASK_CUSTOMER"));
                out.add(amb(c, AmbiguityType.UNCLEAR_OPERATOR, "more than 100 / less than 100",
                        "Operators leave equality at 100 unspecified",
                        List.of("GT/LT", "GTE/LTE", "ASK_CUSTOMER"),
                        "ASK_CUSTOMER"));
                boundary100 = true;
            }
            if (!depositions && lower.contains("average depositions")) {
                out.add(amb(c, AmbiguityType.UNCLEAR_DENOMINATOR, "average depositions",
                        "Denominator for bulk deposit exclusion (>10x average depositions) is ambiguous",
                        List.of("average_credit_amount_3m", "average_deposit_count_3m", "average_deposit_value_3m", "ASK_CUSTOMER"),
                        "ASK_CUSTOMER"));
                depositions = true;
            }
            if (!settlement && (lower.contains("settlement") || lower.contains("qr settlement"))) {
                Map<String, Object> m = registry.registry();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> metrics = (List<Map<String, Object>>) m.get("metrics");
                boolean unavailable = metrics.stream()
                        .anyMatch(x -> String.valueOf(x.get("code")).startsWith("banking.settlement")
                                && "UNAVAILABLE".equals(x.get("availability")));
                if (unavailable) {
                    out.add(amb(c, AmbiguityType.MISSING_METRIC, "Average Daily Settlement",
                            "Settlement metrics marked UNAVAILABLE in registry — create metric candidate",
                            List.of("banking.settlement.avg_daily_3m", "NEW_METRIC_CANDIDATE"),
                            "NEW_METRIC_CANDIDATE"));
                    settlement = true;
                }
            }
            if (!gaming && lower.contains("online gaming")) {
                out.add(amb(c, AmbiguityType.UNSUPPORTED_DATA, "online gaming",
                        "Online gaming transaction classification availability must be confirmed",
                        List.of("TAXONOMY_AVAILABLE", "UNSUPPORTED", "ASK_CUSTOMER"),
                        "ASK_CUSTOMER"));
                gaming = true;
            }
        }
    }

    private void detectBureau(List<CiPolicyClause> clauses, List<CiPolicyAmbiguity> out) {
        boolean ntc = false, clean = false, dbt = false, pwos = false, lss = false;
        boolean overdueAge = false, creditAfter = false, cleanMonths = false;

        for (CiPolicyClause c : clauses) {
            String lower = text(c).toLowerCase(Locale.ROOT);
            if (!ntc && (lower.contains("ntc") || (lower.contains("score") && lower.contains("650")))) {
                out.add(amb(c, AmbiguityType.UNKNOWN_BUSINESS_TERM, "NTC",
                        "NTC canonical representation may differ across Equifax / SurePass CIBIL / CRIF — use bureau.status_ntc",
                        List.of("bureau.status_ntc", "provider_raw_ntc", "ASK_CUSTOMER"),
                        "bureau.status_ntc"));
                ntc = true;
            }
            if (!clean && lower.contains("clean")) {
                out.add(amb(c, AmbiguityType.UNKNOWN_BUSINESS_TERM, "CLEAN string",
                        "What constitutes CLEAN? DPD=0 / no delinquency / provider-specific CLEAN code",
                        List.of(
                                "DPD_EQ_0",
                                "DPD_LTE_30",
                                "NO_ADVERSE_BUREAU_STATUS",
                                "CUSTOMER_COMPOSITE_RULE",
                                "OTHER"),
                        "ASK_CUSTOMER"));
                clean = true;
            }
            if (lower.contains("dbt") && !dbt) {
                out.add(amb(c, AmbiguityType.UNKNOWN_BUSINESS_TERM, "DBT",
                        "DBT string meaning requires customer/vocabulary confirmation",
                        List.of("ASK_CUSTOMER"), "ASK_CUSTOMER"));
                dbt = true;
            }
            if (lower.contains("pwos") && !pwos) {
                out.add(amb(c, AmbiguityType.UNKNOWN_BUSINESS_TERM, "PWOS",
                        "PWOS string meaning requires customer/vocabulary confirmation",
                        List.of("ASK_CUSTOMER"), "ASK_CUSTOMER"));
                pwos = true;
            }
            if (lower.contains("lss") && !lss) {
                out.add(amb(c, AmbiguityType.UNKNOWN_BUSINESS_TERM, "LSS",
                        "LSS string meaning requires customer/vocabulary confirmation",
                        List.of("ASK_CUSTOMER"), "ASK_CUSTOMER"));
                lss = true;
            }
            if ("1".equals(c.getClauseNumber()) && !overdueAge) {
                out.add(amb(c, AmbiguityType.MISSING_METRIC, "overdue reporting date age",
                        "bureau.overdue.age_months not equivalent to max-DPD — metric candidate required",
                        List.of("bureau.overdue.age_months", "NEW_METRIC_CANDIDATE"),
                        "NEW_METRIC_CANDIDATE"));
                overdueAge = true;
            }
            if ("2".equals(c.getClauseNumber()) && !creditAfter) {
                out.add(amb(c, AmbiguityType.MISSING_METRIC, "credit after overdue",
                        "bureau.credit_after_overdue.exists missing as first-class metric",
                        List.of("bureau.credit_after_overdue.exists", "NEW_METRIC_CANDIDATE"),
                        "NEW_METRIC_CANDIDATE"));
                creditAfter = true;
            }
            if ("3".equals(c.getClauseNumber()) && !cleanMonths) {
                out.add(amb(c, AmbiguityType.MISSING_METRIC, "clean history months",
                        "bureau.credit_after_overdue.clean_history_months required",
                        List.of("bureau.credit_after_overdue.clean_history_months", "NEW_METRIC_CANDIDATE"),
                        "NEW_METRIC_CANDIDATE"));
                cleanMonths = true;
            }
            if (lower.contains("write-off") || lower.contains("no loan overdue")) {
                out.add(amb(c, AmbiguityType.UNCLEAR_SCOPE, "loan vs credit-card exception",
                        "Exception handling for credit cards vs loans needs confirmation",
                        List.of("CC_EXCLUDED", "CC_SUBJECT_TO_SUBRULES", "ASK_CUSTOMER"),
                        "ASK_CUSTOMER"));
            }
        }
    }

    private String text(CiPolicyClause c) {
        return c.getNormalizedText() != null ? c.getNormalizedText() : c.getSourceText();
    }

    private CiPolicyAmbiguity amb(CiPolicyClause c, AmbiguityType type, String phrase, String desc,
                                  List<Object> options, String recommended) {
        return CiPolicyAmbiguity.builder()
                .id(UUID.randomUUID())
                .clauseId(c.getId())
                .ambiguityType(type.name())
                .phrase(phrase)
                .description(desc)
                .candidateOptions(options)
                .recommendedOption(recommended)
                .confidence(new BigDecimal("0.7000"))
                .severity("MATERIAL")
                .resolutionStatus("OPEN")
                .metadata(Map.of())
                .build();
    }
}
