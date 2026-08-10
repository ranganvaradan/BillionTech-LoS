package com.los.core.creditintelligence.policystudio.catalogue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.los.core.creditintelligence.policystudio.catalogue.ImplementationBinding.CANONICAL_EVALUATOR;
import static com.los.core.creditintelligence.policystudio.catalogue.ImplementationBinding.GOLDEN_STUDIO_TEMPLATE;
import static com.los.core.creditintelligence.policystudio.catalogue.ImplementationBinding.KYC_DSL;
import static com.los.core.creditintelligence.policystudio.catalogue.ImplementationBinding.LIMIT_SIZING;
import static com.los.core.creditintelligence.policystudio.catalogue.ImplementationBinding.METRIC_SERVICE;
import static com.los.core.creditintelligence.policystudio.catalogue.ImplementationBinding.PRODUCTION_HARD_RULE;
import static com.los.core.creditintelligence.policystudio.catalogue.ImplementationBinding.PRODUCTION_RULE_SET;
import static com.los.core.creditintelligence.policystudio.catalogue.ImplementationBinding.PRODUCTION_SCORECARD;
import static com.los.core.creditintelligence.policystudio.catalogue.ImplementationBinding.REGISTRY;
import static com.los.core.creditintelligence.policystudio.catalogue.ImplementationBinding.SHADOW_DECISION;

/**
 * Static catalogue of proven LOS underwriting capabilities (POLICY-DESIGN-2A / UX-2A).
 * No invented capabilities — every entry is backed by existing code/config.
 */
final class CreditCapabilityDefinitions {

    private CreditCapabilityDefinitions() {}

    static List<BusinessCapability> all() {
        List<BusinessCapability> list = new ArrayList<>();
        list.addAll(eligibility());
        list.addAll(kyc());
        list.addAll(bureau());
        list.addAll(banking());
        list.addAll(financial());
        list.addAll(gstBusiness());
        list.addAll(collateral());
        list.addAll(risk());
        list.addAll(limit());
        list.addAll(pricing());
        list.addAll(decision());
        return List.copyOf(list);
    }

    private static List<BusinessCapability> eligibility() {
        return List.of(
                BusinessCapability.builder("ELIG.MIN_BUREAU_SCORE")
                        .name("Eligibility bureau gate (product ruleset)")
                        .description("Product-level eligibility gate for minimum bureau score (UnderwritingRuleEngine "
                                + "minBureauScore). Prefer BUREAU.MIN_SCORE for Credit Manager authoring of the same "
                                + "business threshold — this ID is kept for production binding fidelity.")
                        .domain(CapabilityDomain.ELIGIBILITY)
                        .fact("BUREAU_SCORE / bureau.score")
                        .dataSource("Credit Bureau")
                        .operators("GTE", "LT")
                        .param(num("minimumScore", "Minimum score", "SCORE", 650))
                        .treatments("REJECT", "MANUAL_REVIEW", "SCORE_IMPACT")
                        .production(true).studio(true).manualReview(true)
                        .binding(b("elig-bureau-ruleset", PRODUCTION_RULE_SET, "UnderwritingRuleEngine", "minBureauScore", true))
                        .binding(b("elig-bureau-golden", GOLDEN_STUDIO_TEMPLATE, "DeterministicGoldenInterpretationProvider",
                                "BUREAU_SCORE_OR_NTC_OR_GTE_650", false))
                        .build(),
                BusinessCapability.builder("ELIG.MAX_REQUESTED_AMOUNT")
                        .name("Maximum requested amount")
                        .description("Requested loan amount must not exceed the policy ticket cap.")
                        .domain(CapabilityDomain.ELIGIBILITY)
                        .fact("REQUESTED_AMOUNT / requestedAmount")
                        .dataSource("Application")
                        .operators("LTE", "GT")
                        .param(money("maximumAmount", "Maximum amount", 10_000_000))
                        .treatments("REJECT", "MANUAL_REVIEW")
                        .production(true).studio(false)
                        .binding(b("elig-max-amt", PRODUCTION_RULE_SET, "UnderwritingRuleEngine", "maxLoanAmount", true))
                        .build(),
                BusinessCapability.builder("ELIG.REQUIRE_KYC_PASS")
                        .name("KYC must be successful")
                        .description("Overall KYC outcome must be successful before underwriting proceeds.")
                        .domain(CapabilityDomain.ELIGIBILITY)
                        .fact("kycPassEffective / KYC_QUALITY")
                        .dataSource("KYC")
                        .operators("EQ")
                        .parameterisable(false)
                        .treatments("REJECT")
                        .production(true).studio(true).manualInput(false)
                        .binding(b("elig-kyc", PRODUCTION_RULE_SET, "UnderwritingRuleEngine", "requireKycSuccess", true))
                        .binding(b("elig-kyc-score", PRODUCTION_SCORECARD, "ScorecardPolicyEngine", "KYC_QUALITY", true))
                        .binding(b("elig-kyc-dsl", KYC_DSL, "KycPolicyAuthoringSupport", "KYC_OVERALL_BEFORE_UW", false))
                        .build(),
                BusinessCapability.builder("ELIG.BUSINESS_VINTAGE_MIN")
                        .name("Minimum business vintage")
                        .description("Business must have operated for at least the configured period.")
                        .domain(CapabilityDomain.ELIGIBILITY)
                        .fact("businessStability")
                        .dataSource("Application / GST / Manual")
                        .operators("GTE", "LT")
                        .param(num("minimumValue", "Minimum value", "INTEGER", 3))
                        .param(enumParam("unit", "Unit", "YEARS", "Years (production SCF uses years)"))
                        .treatments("REJECT", "MANUAL_REVIEW")
                        .production(true).studio(false).manualInput(true)
                        .availability("MANUAL_INPUT_ALLOWED")
                        .binding(b("elig-vintage", PRODUCTION_HARD_RULE, "UnderwritingRuleEngine",
                                "businessStability / scf_hard_biz", true))
                        .build()
        );
    }

    private static List<BusinessCapability> kyc() {
        return List.of(
                BusinessCapability.builder("KYC.PAN_VERIFIED")
                        .name("PAN verified")
                        .description("PAN must be present and verified.")
                        .domain(CapabilityDomain.KYC)
                        .fact("kyc.pan.verified")
                        .dataSource("KYC provider")
                        .operators("EQ")
                        .parameterisable(false)
                        .treatments("REJECT", "MANUAL_REVIEW")
                        .production(false).studio(true)
                        .binding(b("kyc-pan", KYC_DSL, "KycPolicyAuthoringSupport", "KYC_PAN_VERIFIED", false))
                        .build(),
                BusinessCapability.builder("KYC.GSTIN_VERIFIED")
                        .name("GSTIN verified")
                        .description("GSTIN verification where required for the borrower type.")
                        .domain(CapabilityDomain.KYC)
                        .fact("kyc.gstin.verified")
                        .dataSource("KYC / GST")
                        .operators("EQ")
                        .parameterisable(false)
                        .treatments("REJECT", "MANUAL_REVIEW")
                        .production(false).studio(true)
                        .binding(b("kyc-gstin", KYC_DSL, "KycPolicyAuthoringSupport", "KYC_GSTIN", false))
                        .build(),
                BusinessCapability.builder("KYC.VKYC_AMOUNT_BOUNDARY")
                        .name("VKYC required above amount")
                        .description("Video KYC must be completed when requested amount exceeds the threshold.")
                        .domain(CapabilityDomain.KYC)
                        .fact("requested_amount + kyc.vkyc.completed")
                        .dataSource("Application + KYC")
                        .operators("GT", "EQ")
                        .param(money("thresholdAmount", "Amount threshold", null))
                        .treatments("REJECT", "MANUAL_REVIEW")
                        .production(false).studio(true)
                        .binding(b("kyc-vkyc", KYC_DSL, "KycPolicyAuthoringSupport", "KYC_VKYC_AMOUNT_BOUNDARY", false))
                        .build()
        );
    }

    private static List<BusinessCapability> bureau() {
        return List.of(
                BusinessCapability.builder("BUREAU.MIN_SCORE")
                        .name("Minimum bureau score")
                        .description("Credit bureau score must be at least the configured minimum.")
                        .domain(CapabilityDomain.BUREAU)
                        .fact("BUREAU_SCORE / bureau.score")
                        .dataSource("Credit Bureau")
                        .operators("GTE", "LT")
                        .param(num("minimumScore", "Minimum score", "SCORE", 650))
                        .treatments("REJECT", "MANUAL_REVIEW", "SCORE_IMPACT")
                        .production(true).studio(true)
                        .binding(b("bureau-hr", PRODUCTION_HARD_RULE, "UnderwritingRuleEngine",
                                "BUREAU_SCORE / scf_hard_bureau / pl_hard_low_bureau", true))
                        .binding(b("bureau-sc", PRODUCTION_SCORECARD, "ScorecardPolicyEngine", "BUREAU_SCORE rows", true))
                        .binding(b("bureau-golden", GOLDEN_STUDIO_TEMPLATE, "DeterministicGoldenInterpretationProvider",
                                "BUREAU_SCORE_OR_NTC_OR_GTE_650", false))
                        .binding(b("bureau-reg", REGISTRY, "PolicyAuthoringRegistry", "bureau.score", false))
                        .build(),
                BusinessCapability.builder("BUREAU.LIVE_UNSECURED_MAX")
                        .name("Maximum live unsecured loans")
                        .description("Count of live unsecured loans must not exceed the maximum.")
                        .domain(CapabilityDomain.BUREAU)
                        .fact("LIVE_UNSECURED_LOAN_COUNT / bureau.live_unsecured_loan_count")
                        .dataSource("Credit Bureau")
                        .operators("LTE", "GT")
                        .param(num("maximumCount", "Maximum count", "INTEGER", 6))
                        .treatments("REJECT", "MANUAL_REVIEW")
                        .production(true).studio(false)
                        .binding(b("bureau-unsec-hr", PRODUCTION_HARD_RULE, "UnderwritingRuleEngine",
                                "scf_hard_unsec", true))
                        .binding(b("bureau-unsec-can", CANONICAL_EVALUATOR, "CanonicalBureauRuleEvaluator",
                                "HARD_LIVE_UNSECURED", false))
                        .binding(b("bureau-unsec-metric", METRIC_SERVICE, "BureauMetricService",
                                "bureau.live_unsecured_loan_count", false))
                        .build(),
                BusinessCapability.builder("BUREAU.ENQUIRIES_MAX")
                        .name("Maximum bureau enquiries")
                        .description("Bureau enquiries in the configured window must not exceed the maximum.")
                        .domain(CapabilityDomain.BUREAU)
                        .fact("BUREAU_ENQUIRIES_3M / bureau.inquiries.*")
                        .dataSource("Credit Bureau")
                        .operators("LTE", "GT")
                        .param(num("maximumCount", "Maximum enquiries", "INTEGER", 21))
                        .param(num("windowMonths", "Window (months)", "INTEGER", 3))
                        .treatments("REJECT", "MANUAL_REVIEW")
                        .production(true).studio(true)
                        .binding(b("bureau-enq-hr", PRODUCTION_HARD_RULE, "UnderwritingRuleEngine",
                                "scf_hard_enq / BUREAU_ENQUIRIES_3M", true))
                        .binding(b("bureau-enq-golden", GOLDEN_STUDIO_TEMPLATE, "DeterministicGoldenInterpretationProvider",
                                "BUREAU_INQUIRIES_CURRENT_MONTH", false))
                        .build(),
                BusinessCapability.builder("BUREAU.MAX_DPD")
                        .name("Maximum DPD")
                        .description("Maximum days past due in the lookback window.")
                        .domain(CapabilityDomain.BUREAU)
                        .fact("bureau.max_dpd_6m / bureau.max_dpd_12m")
                        .dataSource("Credit Bureau")
                        .operators("LTE", "GT")
                        .param(num("maximumDays", "Maximum DPD (days)", "INTEGER", 30))
                        .param(num("windowMonths", "Window (months)", "INTEGER", 6))
                        .treatments("REJECT", "MANUAL_REVIEW")
                        .production(true).studio(true)
                        .binding(b("bureau-dpd-golden", GOLDEN_STUDIO_TEMPLATE, "DeterministicGoldenInterpretationProvider",
                                "BUREAU_DPD_LAST_6M", false))
                        .binding(b("bureau-dpd-metric", METRIC_SERVICE, "BureauMetricService", "max_dpd_*", false))
                        .build(),
                BusinessCapability.builder("BUREAU.CC_OVERDUE_MAX")
                        .name("Maximum credit-card overdue")
                        .description("Credit-card overdue amount must stay below the threshold.")
                        .domain(CapabilityDomain.BUREAU)
                        .fact("bureau.cc_overdue_amount")
                        .dataSource("Credit Bureau")
                        .operators("LT", "GT")
                        .param(money("maximumAmount", "Maximum overdue", 5000))
                        .treatments("REJECT", "MANUAL_REVIEW")
                        .production(false).studio(true)
                        .binding(b("bureau-cc", GOLDEN_STUDIO_TEMPLATE, "DeterministicGoldenInterpretationProvider",
                                "BUREAU_CC_OVERDUE_GT_5000", false))
                        .build(),
                BusinessCapability.builder("BUREAU.NTC_ALLOWED")
                        .name("NTC / thin-file treatment")
                        .description("Whether NTC or thin-file bureau status is allowed under the policy.")
                        .domain(CapabilityDomain.BUREAU)
                        .fact("bureau.status_ntc")
                        .dataSource("Credit Bureau")
                        .operators("EQ")
                        .param(enumParam("allowNtc", "Allow NTC", "true", "Allow NTC as an alternative to score"))
                        .treatments("REJECT", "MANUAL_REVIEW", "SCORE_IMPACT")
                        .production(true).studio(true)
                        .binding(b("bureau-ntc-sc", PRODUCTION_SCORECARD, "ScorecardPolicyEngine", "NTC_FLAG", true))
                        .binding(b("bureau-ntc-golden", GOLDEN_STUDIO_TEMPLATE, "DeterministicGoldenInterpretationProvider",
                                "BUREAU_SCORE_OR_NTC_OR_GTE_650", false))
                        .build()
        );
    }

    private static List<BusinessCapability> banking() {
        return List.of(
                BusinessCapability.builder("BANK.CHEQUE_BOUNCE_MAX")
                        .name("Maximum cheque bounces")
                        .description("Cheque / payment return count in a window must not exceed the maximum. "
                                + "Normalized across CHEQUE_BOUNCES_*, cheque_return_*, and related metrics.")
                        .domain(CapabilityDomain.BANKING)
                        .fact("CHEQUE_BOUNCES_* / banking.cheque_return_count_*")
                        .dataSource("Bank statement / AA")
                        .operators("LTE", "GT", "EQ")
                        .param(num("windowMonths", "Window (months)", "INTEGER", 3))
                        .param(num("maximumCount", "Maximum bounces", "INTEGER", 0))
                        .treatments("REJECT", "MANUAL_REVIEW")
                        .production(true).studio(false)
                        .binding(b("bank-bounce-hr", PRODUCTION_HARD_RULE, "UnderwritingRuleEngine",
                                "CHEQUE_BOUNCES_3M / CHEQUE_BOUNCES_12M", true))
                        .binding(b("bank-bounce-can", CANONICAL_EVALUATOR, "CanonicalBankingRuleEvaluator",
                                "BANK_CHEQUE_RETURN_MAX", false))
                        .binding(b("bank-bounce-metric", METRIC_SERVICE, "BankingMetricService",
                                "banking.cheque_return_count_*", false))
                        .build(),
                BusinessCapability.builder("BANK.TURNOVER_PCT_GST_MIN")
                        .name("Minimum banking / GST turnover")
                        .description("Banking turnover as a percentage of GST turnover must meet the minimum.")
                        .domain(CapabilityDomain.BANKING)
                        .fact("BANKING_TURNOVER_PCT_GST")
                        .dataSource("Bank statement + GST")
                        .operators("GTE", "LT")
                        .param(pct("minimumPercentage", "Minimum percentage", 75))
                        .treatments("REJECT", "MANUAL_REVIEW", "SCORE_IMPACT")
                        .production(true).studio(false)
                        .binding(b("bank-gst-hr", PRODUCTION_HARD_RULE, "UnderwritingRuleEngine",
                                "scf_hard_bank_to", true))
                        .binding(b("bank-gst-sc", PRODUCTION_SCORECARD, "ScorecardPolicyEngine",
                                "BANKING_TURNOVER_PCT_GST", true))
                        .build(),
                BusinessCapability.builder("BANK.ABB_MIN")
                        .name("Minimum average daily balance (ABB)")
                        .description("Average daily balance over the lookback window must meet the minimum.")
                        .domain(CapabilityDomain.BANKING)
                        .fact("banking.avg_daily_balance_3m")
                        .dataSource("Bank statement / AA")
                        .operators("GTE", "LT")
                        .param(money("minimumAmount", "Minimum ABB", null))
                        .param(num("windowMonths", "Window (months)", "INTEGER", 3))
                        .treatments("REJECT", "MANUAL_REVIEW", "REFER")
                        .production(false).studio(true)
                        .binding(b("bank-abb-can", CANONICAL_EVALUATOR, "CanonicalBankingRuleEvaluator",
                                "BANK_ABB_MINIMUM", false))
                        .binding(b("bank-abb-reg", REGISTRY, "PolicyAuthoringRegistry",
                                "banking.avg_daily_balance_3m", false))
                        .binding(b("bank-abb-golden", GOLDEN_STUDIO_TEMPLATE, "DeterministicGoldenInterpretationProvider",
                                "BANK_STARTER_ADB_GTE_EDI / DIGILEAP ADB", false))
                        .build(),
                BusinessCapability.builder("BANK.CC_UTIL_MAX")
                        .name("Maximum CC utilisation")
                        .description("Credit-card utilisation percentage must stay below the maximum.")
                        .domain(CapabilityDomain.BANKING)
                        .fact("CC_UTILISATION_PCT")
                        .dataSource("Bank / Bureau")
                        .operators("LT", "GTE")
                        .param(pct("maximumPercentage", "Maximum utilisation %", 95))
                        .treatments("REJECT", "MANUAL_REVIEW")
                        .production(true).studio(false)
                        .binding(b("bank-cc", PRODUCTION_HARD_RULE, "UnderwritingRuleEngine", "scf_hard_cc", true))
                        .build(),
                BusinessCapability.builder("BANK.INWARD_RETURN_MAX")
                        .name("Inward return ratio / count")
                        .description("Inward cheque/ECS/ENACH returns — Studio Banking BRE template. "
                                + "Related to, but not identical with, production CHEQUE_BOUNCES_* keys.")
                        .domain(CapabilityDomain.BANKING)
                        .fact("banking.inward_return.ratio_3m / count_3m")
                        .dataSource("Bank statement")
                        .operators("LTE", "GT")
                        .param(pct("maximumRatioPercent", "Maximum return ratio %", 5))
                        .param(num("maximumCount", "Maximum return count", "INTEGER", 5))
                        .param(num("windowMonths", "Window (months)", "INTEGER", 3))
                        .treatments("REJECT", "MANUAL_REVIEW")
                        .production(false).studio(true)
                        .binding(b("bank-inward", GOLDEN_STUDIO_TEMPLATE, "DeterministicGoldenInterpretationProvider",
                                "BANK_INWARD_RETURN_BRANCHED_100", false))
                        .build()
        );
    }

    private static List<BusinessCapability> financial() {
        return List.of(
                BusinessCapability.builder("FIN.FOIR_MAX")
                        .name("Maximum FOIR / obligation ratio")
                        .description("Fixed obligation to income ratio must not exceed the maximum. "
                                + "Normalized across OBLIGATION_RATIO, obligation.ratio, and FOIR checks.")
                        .domain(CapabilityDomain.FINANCIAL)
                        .fact("OBLIGATION_RATIO / obligation.ratio / FOIR")
                        .dataSource("Income + obligations (bureau/bank/application)")
                        .operators("LTE", "GT")
                        .param(pct("maximumPercentage", "Maximum FOIR %", 50))
                        .treatments("REJECT", "MANUAL_REVIEW", "SCORE_IMPACT", "LIMIT_ADJUSTMENT")
                        .production(true).studio(false)
                        .binding(b("fin-foir-sc", PRODUCTION_SCORECARD, "ScorecardPolicyEngine", "OBLIGATION_RATIO", true))
                        .binding(b("fin-foir-cd", PRODUCTION_RULE_SET, "CreditDecisionServiceImpl", "FOIR 0.50", true))
                        .binding(b("fin-foir-reg", REGISTRY, "PolicyAuthoringRegistry", "obligation.ratio", false))
                        .binding(b("fin-foir-shadow", SHADOW_DECISION, "LimitMethodEngine", "FOIR_LIMIT", false))
                        .build(),
                BusinessCapability.builder("FIN.DSCR_MIN")
                        .name("Minimum DSCR")
                        .description("Debt service coverage ratio must meet the minimum.")
                        .domain(CapabilityDomain.FINANCIAL)
                        .fact("DSCR")
                        .dataSource("Financial statements / scorecard")
                        .operators("GTE", "LT")
                        .param(dec("minimumRatio", "Minimum DSCR", 1.25))
                        .treatments("REJECT", "MANUAL_REVIEW")
                        .production(true).studio(false).manualInput(true)
                        .availability("MANUAL_INPUT_ALLOWED")
                        .binding(b("fin-dscr", PRODUCTION_HARD_RULE, "UnderwritingRuleEngine", "scf_hard_dscr", true))
                        .build(),
                BusinessCapability.builder("FIN.INTEREST_COVERAGE_MIN")
                        .name("Minimum interest coverage")
                        .description("Interest coverage ratio must meet the minimum.")
                        .domain(CapabilityDomain.FINANCIAL)
                        .fact("INTEREST_COVERAGE")
                        .dataSource("Financial statements")
                        .operators("GTE", "LT")
                        .param(dec("minimumRatio", "Minimum ratio", 1.5))
                        .treatments("REJECT", "MANUAL_REVIEW")
                        .production(true).studio(false).manualInput(true)
                        .availability("MANUAL_INPUT_ALLOWED")
                        .binding(b("fin-ic", PRODUCTION_HARD_RULE, "UnderwritingRuleEngine", "scf_hard_ic", true))
                        .build(),
                BusinessCapability.builder("FIN.DEBT_EQUITY_MAX")
                        .name("Maximum debt to equity")
                        .description("Debt-to-equity ratio must not exceed the maximum.")
                        .domain(CapabilityDomain.FINANCIAL)
                        .fact("DEBT_TO_EQUITY")
                        .dataSource("Financial statements")
                        .operators("LTE", "GT")
                        .param(dec("maximumRatio", "Maximum ratio", 2.0))
                        .treatments("REJECT", "MANUAL_REVIEW")
                        .production(true).studio(false).manualInput(true)
                        .availability("MANUAL_INPUT_ALLOWED")
                        .binding(b("fin-de", PRODUCTION_HARD_RULE, "UnderwritingRuleEngine", "scf_hard_de", true))
                        .build(),
                BusinessCapability.builder("FIN.TOL_TNW_MAX")
                        .name("Maximum TOL / TNW")
                        .description("Total outside liabilities to tangible net worth must not exceed the maximum.")
                        .domain(CapabilityDomain.FINANCIAL)
                        .fact("TOL_TNW")
                        .dataSource("Financial statements")
                        .operators("LTE", "GT")
                        .param(dec("maximumRatio", "Maximum ratio", 7.0))
                        .treatments("REJECT", "MANUAL_REVIEW")
                        .production(true).studio(false).manualInput(true)
                        .availability("MANUAL_INPUT_ALLOWED")
                        .binding(b("fin-tol", PRODUCTION_HARD_RULE, "UnderwritingRuleEngine", "scf_hard_tol", true))
                        .build(),
                BusinessCapability.builder("FIN.ITR_INCOME_MIN")
                        .name("Minimum ITR income")
                        .description("ITR income must meet the configured minimum.")
                        .domain(CapabilityDomain.FINANCIAL)
                        .fact("ITR_INCOME")
                        .dataSource("ITR")
                        .operators("GTE", "LT")
                        .param(money("minimumAmount", "Minimum income", 300_000))
                        .treatments("REJECT", "MANUAL_REVIEW", "SCORE_IMPACT")
                        .production(true).studio(false)
                        .binding(b("fin-itr", PRODUCTION_HARD_RULE, "UnderwritingRuleEngine", "scf_hard_itr", true))
                        .build(),
                BusinessCapability.builder("FIN.PAT_POSITIVE")
                        .name("PAT must be positive")
                        .description("Profit after tax in the latest financial year must be positive.")
                        .domain(CapabilityDomain.FINANCIAL)
                        .fact("PAT")
                        .dataSource("Financial statements")
                        .operators("GT")
                        .parameterisable(false)
                        .treatments("REJECT", "MANUAL_REVIEW")
                        .production(true).studio(false).manualInput(true)
                        .availability("MANUAL_INPUT_ALLOWED")
                        .binding(b("fin-pat", PRODUCTION_HARD_RULE, "UnderwritingRuleEngine", "scf_hard_pat", true))
                        .build()
        );
    }

    private static List<BusinessCapability> gstBusiness() {
        return List.of(
                BusinessCapability.builder("GST.TURNOVER_MIN")
                        .name("Minimum GST turnover")
                        .description("Annual GST turnover must meet the policy minimum.")
                        .domain(CapabilityDomain.GST_BUSINESS)
                        .fact("ANNUAL_GST_TURNOVER / gst.turnover.trailing_12m")
                        .dataSource("GST")
                        .operators("GTE", "LT")
                        .param(money("minimumAmount", "Minimum turnover", 50_000_000))
                        .treatments("REJECT", "MANUAL_REVIEW", "SCORE_IMPACT")
                        .production(true).studio(false)
                        .binding(b("gst-hr", PRODUCTION_HARD_RULE, "UnderwritingRuleEngine", "scf_hard_gst_to", true))
                        .binding(b("gst-can", CANONICAL_EVALUATOR, "CanonicalGstRuleEvaluator",
                                "GST_TURNOVER_ELIGIBILITY", false))
                        .build()
        );
    }

    private static List<BusinessCapability> collateral() {
        return List.of(
                BusinessCapability.builder("COLL.LTV_MAX")
                        .name("Maximum LTV")
                        .description("Loan-to-value ratio must not exceed the maximum.")
                        .domain(CapabilityDomain.COLLATERAL)
                        .fact("LTV")
                        .dataSource("Collateral / valuation")
                        .operators("LTE", "GT")
                        .param(pct("maximumPercentage", "Maximum LTV %", 80))
                        .treatments("REJECT", "MANUAL_REVIEW", "SCORE_IMPACT", "LIMIT_ADJUSTMENT")
                        .production(true).studio(false)
                        .binding(b("coll-ltv", PRODUCTION_SCORECARD, "ScorecardPolicyEngine", "lap_hard_high_ltv", true))
                        .binding(b("coll-shadow", SHADOW_DECISION, "CollateralEngine", "maxLtv", false))
                        .build()
        );
    }

    private static List<BusinessCapability> risk() {
        return List.of(
                BusinessCapability.builder("RISK.SCORECARD_BANDS")
                        .name("Scorecard decision bands")
                        .description("Weighted scorecard percentage bands for approve / manual review / reject.")
                        .domain(CapabilityDomain.RISK_EXCEPTIONS)
                        .fact("scorecard weighted %")
                        .dataSource("Scorecard parameters")
                        .operators("GTE")
                        .param(pct("approveAtOrAbove", "Approve at or above %", 70))
                        .param(pct("manualReviewAtOrAbove", "Manual review at or above %", 50))
                        .treatments("APPROVE", "MANUAL_REVIEW", "REJECT", "SCORE_IMPACT")
                        .production(true).studio(false)
                        .binding(b("risk-bands", PRODUCTION_SCORECARD, "ScorecardPolicyEngine", "thresholds_json", true))
                        .build()
        );
    }

    private static List<BusinessCapability> limit() {
        return List.of(
                BusinessCapability.builder("LIMIT.ABS_CAP")
                        .name("Absolute ticket cap")
                        .description("Absolute maximum sanctioned / requested amount under the policy.")
                        .domain(CapabilityDomain.LIMIT)
                        .fact("REQUESTED_AMOUNT / limit sizing")
                        .dataSource("Application + limit config")
                        .operators("LTE", "GT")
                        .param(money("maximumAmount", "Maximum amount", 10_000_000))
                        .treatments("REJECT", "MANUAL_REVIEW", "LIMIT_ADJUSTMENT")
                        .production(true).studio(false)
                        .binding(b("limit-abs", PRODUCTION_HARD_RULE, "UnderwritingRuleEngine", "scf_hard_abs_cap", true))
                        .binding(b("limit-sizing", LIMIT_SIZING, "LimitSizingService", "V83 limitSizing", true))
                        .build()
        );
    }

    private static List<BusinessCapability> pricing() {
        return List.of(
                BusinessCapability.builder("PRICE.RISK_PREMIUM")
                        .name("Risk-based pricing adjustment")
                        .description("Interest rate adjustment based on risk grade or conditioned approval.")
                        .domain(CapabilityDomain.PRICING)
                        .fact("interestRate / pricing components")
                        .dataSource("Decision / pricing config")
                        .operators("ADD")
                        .param(dec("premiumPercent", "Premium %", 1.5))
                        .treatments("PRICING_ADJUSTMENT")
                        .production(true).studio(false)
                        .binding(b("price-cd", PRODUCTION_RULE_SET, "CreditDecisionServiceImpl", "+1.5% conditioned", true))
                        .binding(b("price-shadow", SHADOW_DECISION, "PricingEngine", "risk/tenure premiums", false))
                        .build()
        );
    }

    private static List<BusinessCapability> decision() {
        return List.of(
                BusinessCapability.builder("DEC.DEFAULT_TREATMENT")
                        .name("Default policy decision treatment")
                        .description("Default outcome when hard rules pass — typically Manual Review or Approve.")
                        .domain(CapabilityDomain.DECISION_MANUAL_REVIEW)
                        .fact("rules_json.decision")
                        .dataSource("Policy configuration")
                        .operators("EQ")
                        .param(enumParam("defaultTreatment", "Default treatment", "MANUAL_REVIEW",
                                "APPROVE | MANUAL_REVIEW | REJECT"))
                        .treatments("APPROVE", "MANUAL_REVIEW", "REJECT")
                        .production(true).studio(false).manualReview(true)
                        .binding(b("dec-default", PRODUCTION_RULE_SET, "UnderwritingRuleEngine", "decision", true))
                        .build()
        );
    }

    private static ParameterDefinition num(String name, String label, String type, Object def) {
        return new ParameterDefinition(name, label, type, null, def, null);
    }

    private static ParameterDefinition money(String name, String label, Object def) {
        return new ParameterDefinition(name, label, "MONEY_INR", "INR", def, null);
    }

    private static ParameterDefinition pct(String name, String label, Object def) {
        return new ParameterDefinition(name, label, "PERCENT", "%", def, null);
    }

    private static ParameterDefinition dec(String name, String label, Object def) {
        return new ParameterDefinition(name, label, "DECIMAL", null, def, null);
    }

    private static ParameterDefinition enumParam(String name, String label, Object def, String desc) {
        return new ParameterDefinition(name, label, "ENUM", null, def, desc);
    }

    private static ImplementationBinding b(
            String id, String kind, String engine, String ref, boolean prod) {
        return new ImplementationBinding(id, kind, engine, ref, null, prod);
    }

    /** Capabilities shown only when advanced=true (alias / specialized production bindings). */
    static boolean primaryCatalogueVisible(String businessCapabilityId) {
        return !"ELIG.MIN_BUREAU_SCORE".equals(businessCapabilityId);
    }

    /** Search aliases (business language → capability ids). */
    static Map<String, List<String>> searchAliases() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("cibil", List.of("BUREAU.MIN_SCORE"));
        m.put("bureau score", List.of("BUREAU.MIN_SCORE"));
        m.put("bounce", List.of("BANK.CHEQUE_BOUNCE_MAX"));
        m.put("cheque", List.of("BANK.CHEQUE_BOUNCE_MAX"));
        m.put("foir", List.of("FIN.FOIR_MAX"));
        m.put("obligation", List.of("FIN.FOIR_MAX"));
        m.put("dscr", List.of("FIN.DSCR_MIN"));
        m.put("gst", List.of("GST.TURNOVER_MIN", "BANK.TURNOVER_PCT_GST_MIN"));
        m.put("turnover", List.of("GST.TURNOVER_MIN", "BANK.TURNOVER_PCT_GST_MIN"));
        m.put("vintage", List.of("ELIG.BUSINESS_VINTAGE_MIN"));
        m.put("ticket", List.of("LIMIT.ABS_CAP", "ELIG.MAX_REQUESTED_AMOUNT"));
        m.put("cap", List.of("LIMIT.ABS_CAP"));
        m.put("kyc", List.of("ELIG.REQUIRE_KYC_PASS", "KYC.PAN_VERIFIED", "KYC.GSTIN_VERIFIED"));
        return m;
    }

    /** Common capabilities surfaced first in Browse / Add Rule. */
    static List<String> commonCapabilityIds() {
        return List.of(
                "BUREAU.MIN_SCORE",
                "FIN.FOIR_MAX",
                "ELIG.BUSINESS_VINTAGE_MIN",
                "BANK.CHEQUE_BOUNCE_MAX",
                "BANK.TURNOVER_PCT_GST_MIN",
                "FIN.DSCR_MIN",
                "LIMIT.ABS_CAP");
    }

    /** Normalization notes exposed in advanced catalogue payload. */
    static List<Map<String, Object>> normalizationNotes() {
        List<Map<String, Object>> notes = new ArrayList<>();
        notes.add(note("BUREAU.MIN_SCORE",
                "Normalized across hardRules (BUREAU_SCORE), scorecard rows, golden BUREAU_SCORE_OR_NTC_OR_GTE_650, "
                        + "and registry bureau.score. Preferred Credit Manager capability for bureau score thresholds."));
        notes.add(note("ELIG.MIN_BUREAU_SCORE",
                "Related production eligibility ruleset binding (minBureauScore). Hidden from primary catalogue; "
                        + "shown under Advanced. Prefer BUREAU.MIN_SCORE for authoring — IDs not merged."));
        notes.add(note("BANK.CHEQUE_BOUNCE_MAX",
                "Normalized across CHEQUE_BOUNCES_3M/12M (production), banking.cheque_return_count_* (canonical), "
                        + "and CreditRules bounceCount6Months. Studio inward_return golden is a RELATED but DISTINCT "
                        + "capability (BANK.INWARD_RETURN_MAX) — not silently merged."));
        notes.add(note("FIN.FOIR_MAX",
                "Normalized across OBLIGATION_RATIO scorecard, CreditDecision FOIR 50%, registry obligation.ratio, "
                        + "and shadow FOIR_LIMIT. No Studio golden FOIR template today."));
        notes.add(note("GST.TURNOVER_MIN",
                "Normalized across ANNUAL_GST_TURNOVER hardRule and CanonicalGst GST_TURNOVER_ELIGIBILITY."));
        notes.add(note("ELIG.BUSINESS_VINTAGE_MIN",
                "Production uses businessStability in YEARS. MONTHS unit is accepted in UX-2C and converted to years "
                        + "in the draft expression (e.g. 24 months → 2.0 years)."));
        notes.add(note("LIMIT.ABS_CAP / ELIG.MAX_REQUESTED_AMOUNT",
                "Related: absolute SCF cap vs product maxLoanAmount. Kept as separate IDs; both amount caps."));
        notes.add(note("ELIG.REQUIRE_KYC_PASS",
                "Normalized across requireKycSuccess, KYC_QUALITY scorecard, and KYC DSL overall gate."));
        return notes;
    }

    private static Map<String, Object> note(String id, String text) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("businessCapabilityId", id);
        m.put("note", text);
        return m;
    }
}
