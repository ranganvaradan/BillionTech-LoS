package com.los.core.creditintelligence.staging;

import com.los.core.creditintelligence.validation.domain.ValidationCaseCode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Day 3.5 calibrated prospect-demo applications (exactly 10).
 * Each fixture declares an explicit product so Banking BRE product rules do not cross-contaminate.
 * All entries: VALIDATION FIXTURE — NOT REAL BORROWER DATA.
 */
public final class StagingProspectSimulationCatalog {

    public static final String DATA_SOURCE_VALIDATION_FIXTURES = "VALIDATION_FIXTURES";
    public static final String DATA_SOURCE_STAGING_APPLICATIONS = "STAGING_APPLICATIONS";
    public static final String DEMO_RESOLUTION_BANNER =
            "DEMO FIXTURE RESOLUTION — CUSTOMER CONFIRMATION REQUIRED";

    private StagingProspectSimulationCatalog() {}

    public record DemoApp(
            String applicationCode,
            String displayName,
            String scenarioLabel,
            String scenarioPurpose,
            String demoCategory,
            String product,
            ValidationCaseCode baseCase,
            BigDecimal requestedAmount,
            String description,
            Map<String, Object> metricOverlay,
            Map<String, Object> factOverlay,
            /** Demo-only parameter resolutions (EDI, exactly-100, etc.) — not persisted as vocabulary. */
            Map<String, Object> demoResolutions,
            boolean crossSourceRefer,
            boolean legacyDefaultDependent,
            boolean variant
    ) {}

    public static List<DemoApp> tenDemoApps() {
        List<DemoApp> apps = new ArrayList<>();

        // APP-001 Strong DigiLeap — tendency PASS
        apps.add(app(
                "APP_001_STRONG_DIGILEAP", "APP-001", "Strong DigiLeap",
                "Exercises DigiLeap capacity + transaction rules with healthy banking and clean bureau signals.",
                "Strong policy fit", "DIGILEAP",
                ValidationCaseCode.CASE_A_STRONG, "1000000",
                "Adequate adjusted ADB, txn≥20, low inward returns",
                metrics(
                        "banking.avg_daily_balance_3m", 220000,
                        "banking.transaction_count.average_monthly_3m", 32,
                        "banking.transaction_count.total_3m", 140,
                        "banking.inward_return.ratio_3m", 1.5,
                        "banking.inward_return.count_3m", 1,
                        "banking.settlement.avg_daily_3m", 120000,
                        "banking.settlement.count_monthly_avg_3m", 28,
                        "bureau.score", 740,
                        "bureau.status_ntc", false,
                        "bureau.max_dpd_6m", 0,
                        "bureau.write_off", false,
                        "bureau.overdue.age_months", 0,
                        "bureau.overdue.amount", 0,
                        "bureau.cc_overdue_amount", 0,
                        "bureau.settled", false,
                        "bureau.restructured", false,
                        "bureau.legal_suit", false,
                        "bureau.multiple_pan", false,
                        "bureau.inquiries.current_month", 1,
                        "bureau.account_sold", false,
                        "application.proposed_edi", 28000),
                Map.of("application.proposed_edi", 28000, "application.loan_amount", 1000000),
                demoRes("EDI", "application.proposed_edi", "exactly_100", "treat_100_as_ratio_branch"),
                false, false, false));

        // APP-002 DigiLeap capacity tight — PASS + counter-offer tendency via Decision Engine
        apps.add(app(
                "APP_002_DIGILEAP_CAPACITY_TIGHT", "APP-002", "Capacity constrained",
                "DigiLeap eligibility passes at a tight ADB/EDI margin; recommendation should constrain amount.",
                "Capacity constrained", "DIGILEAP",
                ValidationCaseCode.CASE_A_STRONG, "1200000",
                "Eligibility passes; capacity supports a lower amount",
                metrics(
                        "banking.avg_daily_balance_3m", 160000, // /5 = 32000 vs EDI 30000 → pass narrowly
                        "banking.transaction_count.average_monthly_3m", 22,
                        "banking.transaction_count.total_3m", 110,
                        "banking.inward_return.ratio_3m", 2.0,
                        "banking.inward_return.count_3m", 2,
                        "bureau.score", 700,
                        "bureau.status_ntc", false,
                        "bureau.max_dpd_6m", 0,
                        "bureau.write_off", false,
                        "bureau.cc_overdue_amount", 0,
                        "bureau.settled", false,
                        "bureau.restructured", false,
                        "bureau.legal_suit", false,
                        "bureau.multiple_pan", false,
                        "bureau.inquiries.current_month", 2,
                        "bureau.account_sold", false,
                        "application.proposed_edi", 30000),
                Map.of("application.proposed_edi", 30000, "application.loan_amount", 1200000),
                demoRes("EDI", "application.proposed_edi", "exactly_100", "treat_100_as_ratio_branch"),
                false, false, false));

        // APP-003 Starter strong — PASS
        apps.add(app(
                "APP_003_STARTER_STRONG", "APP-003", "Strong Starter",
                "Starter ADB ≥ proposed EDI with clean bureau; Smart Switch/DigiLeap rules must not execute.",
                "Strong policy fit", "STARTER",
                ValidationCaseCode.CASE_A_STRONG, "500000",
                "ADB >= EDI; good bureau",
                metrics(
                        "banking.avg_daily_balance_3m", 180000,
                        "banking.transaction_count.average_monthly_3m", 15,
                        "banking.transaction_count.total_3m", 70,
                        "banking.inward_return.ratio_3m", 1.0,
                        "banking.inward_return.count_3m", 0,
                        "bureau.score", 720,
                        "bureau.status_ntc", false,
                        "bureau.max_dpd_6m", 0,
                        "bureau.write_off", false,
                        "bureau.cc_overdue_amount", 0,
                        "bureau.settled", false,
                        "bureau.restructured", false,
                        "bureau.legal_suit", false,
                        "bureau.multiple_pan", false,
                        "bureau.inquiries.current_month", 0,
                        "bureau.account_sold", false,
                        "application.proposed_edi", 25000),
                Map.of("application.proposed_edi", 25000, "application.loan_amount", 500000),
                demoRes("EDI", "application.proposed_edi", "exactly_100", "treat_100_as_count_branch"),
                false, false, false));

        // APP-004 High inquiries — Bureau FAIL; DigiLeap txn below 20 also fails Banking BRE (second FAIL)
        apps.add(app(
                "APP_004_HIGH_INQUIRIES", "APP-004", "High inquiries",
                "Bureau current-month inquiries > 3 (Bureau BRE FAIL). DigiLeap average monthly txn < 20 so Banking BRE also FAILs on the product rule — not via Smart Switch.",
                "Bureau exception", "DIGILEAP",
                ValidationCaseCode.CASE_A_STRONG, "900000",
                "Bureau inquiries >3 in current month; DigiLeap txn volume below minimum",
                metrics(
                        "banking.avg_daily_balance_3m", 200000,
                        "banking.transaction_count.average_monthly_3m", 12,
                        "banking.transaction_count.total_3m", 55,
                        "banking.inward_return.ratio_3m", 1.2,
                        "banking.inward_return.count_3m", 1,
                        "bureau.score", 710,
                        "bureau.status_ntc", false,
                        "bureau.max_dpd_6m", 0,
                        "bureau.write_off", false,
                        "bureau.cc_overdue_amount", 0,
                        "bureau.settled", false,
                        "bureau.restructured", false,
                        "bureau.legal_suit", false,
                        "bureau.multiple_pan", false,
                        "bureau.inquiries.current_month", 5,
                        "bureau.account_sold", false,
                        "application.proposed_edi", 30000),
                Map.of("application.proposed_edi", 30000, "application.loan_amount", 900000),
                demoRes("EDI", "application.proposed_edi", "exactly_100", "treat_100_as_ratio_branch"),
                false, false, false));

        // APP-005 DPD fail — FAIL on bureau; banking DigiLeap with high inward returns for Banking BRE FAIL
        apps.add(app(
                "APP_005_DPD_FAIL", "APP-005", "Recent DPD",
                "Bureau max DPD > 30 in 6 months. Banking path uses elevated inward returns so Banking BRE also fails a universal banking rule.",
                "Bureau exception", "DIGILEAP",
                ValidationCaseCode.CASE_A_STRONG, "1000000",
                "DPD >30 within 6 months; elevated inward cheque returns",
                metrics(
                        "banking.avg_daily_balance_3m", 190000,
                        "banking.transaction_count.average_monthly_3m", 26,
                        "banking.transaction_count.total_3m", 130,
                        "banking.inward_return.ratio_3m", 8.0, // >5% when txn>100 → fail inward
                        "banking.inward_return.count_3m", 12,
                        "bureau.score", 690,
                        "bureau.status_ntc", false,
                        "bureau.max_dpd_6m", 45,
                        "bureau.write_off", false,
                        "bureau.cc_overdue_amount", 0,
                        "bureau.settled", false,
                        "bureau.restructured", false,
                        "bureau.legal_suit", false,
                        "bureau.multiple_pan", false,
                        "bureau.inquiries.current_month", 1,
                        "bureau.account_sold", false,
                        "application.proposed_edi", 30000),
                Map.of("application.proposed_edi", 30000, "application.loan_amount", 1000000),
                demoRes("EDI", "application.proposed_edi", "exactly_100", "treat_100_as_ratio_branch"),
                false, false, false));

        // APP-006 Turnover conflict — REFER via cross-source overlay when product rules pass
        apps.add(app(
                "APP_006_TURNOVER_CONFLICT", "APP-006", "Turnover mismatch",
                "GST vs bank turnover materially conflict. DigiLeap banking capacity passes; cross-source validation drives REFER.",
                "Cross-source mismatch", "DIGILEAP",
                ValidationCaseCode.CASE_C_TURNOVER_CONFLICT, "1100000",
                "GST/bank material variance with otherwise acceptable DigiLeap banking",
                metrics(
                        "banking.avg_daily_balance_3m", 200000,
                        "banking.transaction_count.average_monthly_3m", 24,
                        "banking.transaction_count.total_3m", 115,
                        "banking.inward_return.ratio_3m", 2.0,
                        "banking.inward_return.count_3m", 2,
                        "gst.turnover.trailing_12m", 100000000,
                        "bank.turnover.trailing_12m", 60000000,
                        "bureau.score", 705,
                        "bureau.status_ntc", false,
                        "bureau.max_dpd_6m", 0,
                        "bureau.write_off", false,
                        "bureau.cc_overdue_amount", 0,
                        "bureau.settled", false,
                        "bureau.restructured", false,
                        "bureau.legal_suit", false,
                        "bureau.multiple_pan", false,
                        "bureau.inquiries.current_month", 1,
                        "bureau.account_sold", false,
                        "application.proposed_edi", 32000),
                Map.of("application.proposed_edi", 32000, "application.loan_amount", 1100000),
                demoRes("EDI", "application.proposed_edi", "exactly_100", "treat_100_as_ratio_branch"),
                true, false, false));

        // APP-007 Obligation conflict — REFER
        apps.add(app(
                "APP_007_OBLIGATION_CONFLICT", "APP-007", "Obligation mismatch",
                "Bureau EMI vs bank EMI conflict with DigiLeap capacity passing. Cross-source validation drives REFER.",
                "Cross-source mismatch", "DIGILEAP",
                ValidationCaseCode.CASE_D_OBLIGATION_CONFLICT, "1000000",
                "Bureau vs bank EMI conflict",
                metrics(
                        "banking.avg_daily_balance_3m", 210000,
                        "banking.transaction_count.average_monthly_3m", 27,
                        "banking.transaction_count.total_3m", 118,
                        "banking.inward_return.ratio_3m", 1.8,
                        "banking.inward_return.count_3m", 2,
                        "obligation.total_emi", 182000,
                        "bureau.emi.monthly", 182000,
                        "bank.emi.monthly", 96000,
                        "bureau.score", 700,
                        "bureau.status_ntc", false,
                        "bureau.max_dpd_6m", 5,
                        "bureau.write_off", false,
                        "bureau.cc_overdue_amount", 0,
                        "bureau.settled", false,
                        "bureau.restructured", false,
                        "bureau.legal_suit", false,
                        "bureau.multiple_pan", false,
                        "bureau.inquiries.current_month", 2,
                        "bureau.account_sold", false,
                        "application.proposed_edi", 30000),
                Map.of("application.proposed_edi", 30000, "application.loan_amount", 1000000),
                demoRes("EDI", "application.proposed_edi", "exactly_100", "treat_100_as_ratio_branch"),
                true, false, false));

        // APP-008 Missing banking — DI
        apps.add(app(
                "APP_008_MISSING_BANKING", "APP-008", "Missing banking",
                "Required banking metrics unavailable for DigiLeap. Expect DATA_INSUFFICIENT — missing must not become zero. Also illustrates legacy-default dependence when current LOS invents banking.",
                "Missing data", "DIGILEAP",
                ValidationCaseCode.CASE_B_LEGACY_DEFAULT, "800000",
                "Required banking unavailable",
                metrics(
                        "bureau.score", 700,
                        "bureau.status_ntc", false,
                        "application.proposed_edi", 30000),
                Map.of("application.proposed_edi", 30000, "application.loan_amount", 800000),
                demoRes("EDI", "application.proposed_edi"),
                false, true, false));

        // APP-009 Missing bureau — DI for Bureau BRE; DigiLeap banking present → Banking BRE PASS tendency
        apps.add(app(
                "APP_009_MISSING_BUREAU", "APP-009", "Missing bureau history",
                "Insufficient bureau tradeline/payment history. Banking BRE may still evaluate DigiLeap; Bureau BRE should be DATA_INSUFFICIENT.",
                "Missing data", "DIGILEAP",
                ValidationCaseCode.CASE_E_INCOMPLETE, "850000",
                "Insufficient tradeline/payment history",
                metrics(
                        "banking.avg_daily_balance_3m", 195000,
                        "banking.transaction_count.average_monthly_3m", 25,
                        "banking.transaction_count.total_3m", 112,
                        "banking.inward_return.ratio_3m", 1.5,
                        "banking.inward_return.count_3m", 1,
                        "application.proposed_edi", 30000),
                Map.of("application.proposed_edi", 30000, "application.loan_amount", 850000),
                demoRes("EDI", "application.proposed_edi", "exactly_100", "treat_100_as_ratio_branch"),
                false, false, false));

        // APP-010 Smart Switch — exercises settlement path only
        apps.add(app(
                "APP_010_SMART_SWITCH", "APP-010", "Smart Switch settlement",
                "Populates QR settlement metrics for Smart Switch. DigiLeap/Starter/Reboost rules must not execute.",
                "Strong policy fit", "SMART_SWITCH",
                ValidationCaseCode.CASE_A_STRONG, "1000000",
                "Settlement count and average daily settlement populated for Smart Switch",
                metrics(
                        "banking.avg_daily_balance_3m", 150000,
                        "banking.transaction_count.average_monthly_3m", 20,
                        "banking.transaction_count.total_3m", 105,
                        "banking.inward_return.ratio_3m", 2.0,
                        "banking.inward_return.count_3m", 2,
                        "banking.settlement.avg_daily_3m", 400000, // /10 = 40000 >= EDI 35000
                        "banking.settlement.count_monthly_avg_3m", 25,
                        "bureau.score", 715,
                        "bureau.status_ntc", false,
                        "bureau.max_dpd_6m", 0,
                        "bureau.write_off", false,
                        "bureau.cc_overdue_amount", 0,
                        "bureau.settled", false,
                        "bureau.restructured", false,
                        "bureau.legal_suit", false,
                        "bureau.multiple_pan", false,
                        "bureau.inquiries.current_month", 1,
                        "bureau.account_sold", false,
                        "application.proposed_edi", 35000),
                Map.of("application.proposed_edi", 35000, "application.loan_amount", 1000000),
                demoRes("EDI", "application.proposed_edi", "exactly_100", "treat_100_as_ratio_branch",
                        "QR_SETTLEMENT", "banking.settlement.avg_daily_3m"),
                false, false, false));

        return apps;
    }

    public static List<Map<String, Object>> demoCaseGroups() {
        return List.of(
                Map.of("key", "Strong policy fit", "label", "Strong policy fit",
                        "description", "Product rules satisfied with healthy evidence"),
                Map.of("key", "Capacity constrained", "label", "Capacity constrained",
                        "description", "Eligible but amount should be constrained"),
                Map.of("key", "Bureau exception", "label", "Bureau exception",
                        "description", "Hard bureau signals (DPD / inquiries)"),
                Map.of("key", "Cross-source mismatch", "label", "Cross-source mismatch",
                        "description", "GST/bank or obligation conflicts"),
                Map.of("key", "Missing data", "label", "Missing data",
                        "description", "Critical inputs unavailable — not coerced to zero"));
    }

    public static DemoApp require(String code) {
        String key = normalize(code);
        return tenDemoApps().stream()
                .filter(a -> a.applicationCode().equals(key)
                        || a.displayName().equalsIgnoreCase(key)
                        || a.displayName().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown demo application: " + code));
    }

    public static String normalize(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("application code required");
        }
        String u = code.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (u) {
            case "APP001", "APP_001", "CASE_A", "STRONG_DIGILEAP" -> "APP_001_STRONG_DIGILEAP";
            case "APP002", "APP_002", "CASE_B", "DIGILEAP_CAPACITY_TIGHT", "CAPACITY_CONSTRAINED" -> "APP_002_DIGILEAP_CAPACITY_TIGHT";
            case "APP003", "APP_003", "CASE_C", "STARTER_STRONG" -> "APP_003_STARTER_STRONG";
            case "APP004", "APP_004", "CASE_D", "HIGH_INQUIRIES" -> "APP_004_HIGH_INQUIRIES";
            case "APP005", "APP_005", "CASE_E", "DPD_FAIL", "RECENT_DPD" -> "APP_005_DPD_FAIL";
            case "APP006", "APP_006", "DEMO_STRONG", "TURNOVER_CONFLICT", "TURNOVER_MISMATCH" -> "APP_006_TURNOVER_CONFLICT";
            case "APP007", "APP_007", "DEMO_POOR_BUREAU", "OBLIGATION_CONFLICT" -> "APP_007_OBLIGATION_CONFLICT";
            case "APP008", "APP_008", "DEMO_WEAK_BANKING", "MISSING_BANKING" -> "APP_008_MISSING_BANKING";
            case "APP009", "APP_009", "DEMO_HIGH_OBLIGATIONS", "MISSING_BUREAU", "MISSING_BUREAU_HISTORY" -> "APP_009_MISSING_BUREAU";
            case "APP010", "APP_010", "DEMO_GST_BANK_MISMATCH", "SMART_SWITCH" -> "APP_010_SMART_SWITCH";
            default -> u;
        };
    }

    public static Map<String, Object> listEntry(DemoApp app) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("applicationCode", app.applicationCode());
        m.put("displayName", app.displayName());
        m.put("scenarioLabel", app.scenarioLabel());
        m.put("scenarioPurpose", app.scenarioPurpose());
        m.put("demoCategory", app.demoCategory());
        m.put("product", app.product());
        m.put("description", app.description());
        m.put("requestedAmount", app.requestedAmount());
        m.put("requestedAmountDisplay", formatInrLakhs(app.requestedAmount()));
        m.put("baseCase", app.baseCase().name());
        m.put("variant", app.variant());
        m.put("crossSourceRefer", app.crossSourceRefer());
        m.put("legacyDefaultDependent", app.legacyDefaultDependent());
        m.put("fixtureBanner", StagingCaseCatalog.FIXTURE_BANNER);
        m.put("demoResolutionBanner", DEMO_RESOLUTION_BANNER);
        m.put("dataOrigin", "VALIDATION_FIXTURE");
        m.put("productionActive", false);
        m.put("authoritative", false);
        return m;
    }

    public static String formatInrLakhs(BigDecimal amount) {
        if (amount == null) {
            return "—";
        }
        double lakhs = amount.doubleValue() / 100_000.0;
        if (Math.abs(lakhs - Math.rint(lakhs)) < 0.05) {
            return "₹" + String.format(Locale.US, "%.0f", lakhs) + "L";
        }
        return "₹" + String.format(Locale.US, "%.1f", lakhs) + "L";
    }

    private static DemoApp app(
            String code, String display, String label, String purpose, String category, String product,
            ValidationCaseCode base, String amount, String description,
            Map<String, Object> metrics, Map<String, Object> facts, Map<String, Object> demoRes,
            boolean crossSourceRefer, boolean legacyDefault, boolean variant) {
        return new DemoApp(code, display, label, purpose, category, product, base, new BigDecimal(amount),
                description, metrics, facts, demoRes, crossSourceRefer, legacyDefault, variant);
    }

    private static Map<String, Object> metrics(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    private static Map<String, Object> demoRes(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("banner", DEMO_RESOLUTION_BANNER);
        m.put("persistedAsVocabulary", false);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
