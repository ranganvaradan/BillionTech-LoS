package com.los.core.service.credit;

import com.los.core.model.dto.request.ManualCreditInputsRequest;
import com.los.core.model.entity.Document;
import com.los.core.model.entity.KycStepResult;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.KycStepType;
import com.los.core.model.enums.StepOutcome;
import com.los.core.repository.DocumentRepository;
import com.los.core.repository.KycStepResultRepository;
import com.los.core.service.document.OcrExtractionService;
import com.los.core.service.kyc.IKycOrchestrationService;
import com.los.core.service.loan.InvoiceDiscountingApplicationRules;
import com.los.core.service.underwriting.ApplicationScorecardParameterResolver;
import com.los.plp.service.InvoiceDiscountingVintageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.los.core.service.credit.CreditControlKeys.*;

@Service
@RequiredArgsConstructor
public class CreditControlService {

    private static final BigDecimal DEMO_DEFAULT_MONTHLY_INCOME = new BigDecimal("80000");
    private static final BigDecimal DEMO_DEFAULT_MONTHLY_OBLIGATION = new BigDecimal("20000");
    private static final int DEMO_DEFAULT_BUREAU_SCORE = 720;
    private static final BigDecimal DEMO_DEFAULT_FOIR_RATIO = new BigDecimal("0.25");
  /** Conservative placeholders when bank-statement extraction is not wired yet (does not override real inputs). */
    private static final BigDecimal GAP_DEFAULT_MONTHLY_INCOME = new BigDecimal("85000");
    private static final BigDecimal GAP_DEFAULT_MONTHLY_OBLIGATION = new BigDecimal("15000");
    private static final BigDecimal GAP_DEFAULT_AVERAGE_BANK_BALANCE = new BigDecimal("120000");
    private static final BigDecimal GAP_DEFAULT_FOIR_PERCENT = new BigDecimal("25");
    private static final BigDecimal GAP_DEFAULT_DTI_RATIO = new BigDecimal("18");
    private static final BigDecimal GAP_DEFAULT_BANK_METRIC = new BigDecimal("50000");
    private static final BigDecimal GAP_DEFAULT_BANK_COUNT = new BigDecimal("120");
    private static final BigDecimal SCF_MIN_ANNUAL_GST_TURNOVER = new BigDecimal("50000000");
    private static final BigDecimal SCF_GAP_ANNUAL_GST_TURNOVER = new BigDecimal("52000000");
    private static final BigDecimal SCF_GAP_ANNUAL_BANKING_TURNOVER = new BigDecimal("41000000");
    private static final BigDecimal SCF_GAP_ITR_INCOME = new BigDecimal("450000");
    private static final BigDecimal SCF_GAP_PAT = new BigDecimal("500000");
    private static final BigDecimal SCF_GAP_INTEREST_COVERAGE = new BigDecimal("1.6");
    private static final BigDecimal SCF_GAP_DEBT_TO_EQUITY = new BigDecimal("1.5");
    private static final BigDecimal SCF_GAP_EBITDA = new BigDecimal("650000");
    private static final BigDecimal SCF_GAP_DEBT_SERVICE = new BigDecimal("300000");

    /** Scorecard keys filled from GST / ITR / bank OCR extract (not manual underwriting forms). */
    private static final Set<String> DOCUMENT_EXTRACT_SCORECARD_KEYS = Set.of(
            "ANNUAL_GST_TURNOVER",
            "avgGmv3m",
            "active90days",
            "GST_INCOME",
            "ITR_INCOME",
            "PAT",
            "INTEREST_COVERAGE",
            "DEBT_TO_EQUITY",
            "EBITDA",
            "DEBT_SERVICE",
            "TOL",
            "TNW",
            "ANNUAL_BANKING_TURNOVER",
            "BANKING_TURNOVER_PCT_GST",
            "ABB_OBLIGATION_MULTIPLE",
            "CC_UTILISATION_PCT",
            "CHEQUE_BOUNCES_12M",
            "CHEQUE_BOUNCES_3M",
            "AVERAGE_BANK_BALANCE",
            "avgDailyBalance3m");

    private final IKycOrchestrationService kycOrchestrationService;
    private final InvoiceDiscountingVintageService invoiceDiscountingVintageService;
    private final DocumentRepository documentRepository;
    private final KycStepResultRepository kycStepResultRepository;
    private final OcrExtractionService ocrExtractionService;
    private final LimitSizingService limitSizingService;

    @SuppressWarnings("unchecked")
    public void mergeManualInputs(LoanApplication app, ManualCreditInputsRequest req) {
        Map<String, Object> fi = app.getFinancialInfo() != null
                ? new HashMap<>(app.getFinancialInfo())
                : new HashMap<>();
        Map<String, Object> cc = fi.get(ROOT) instanceof Map<?, ?> m
                ? new LinkedHashMap<>((Map<String, Object>) m)
                : new LinkedHashMap<>();
        Map<String, Object> manual = cc.get(MANUAL) instanceof Map<?, ?> m2
                ? new LinkedHashMap<>((Map<String, Object>) m2)
                : new LinkedHashMap<>();

        if (req.getPanName() != null) {
            putManualField(manual, "panName", req.getPanName());
        }
        if (req.getPanStatus() != null) {
            putManualField(manual, "panStatus", req.getPanStatus());
        }
        if (req.getAadhaarName() != null) {
            putManualField(manual, "aadhaarName", req.getAadhaarName());
        }
        if (req.getAadhaarStatus() != null) {
            putManualField(manual, "aadhaarStatus", req.getAadhaarStatus());
        }
        if (req.getMobileVerified() != null) {
            putManualField(manual, "mobileVerified", req.getMobileVerified());
        }
        if (req.getMonthlyIncome() != null) {
            putManualField(manual, "monthlyIncome", req.getMonthlyIncome().toPlainString());
        }
        if (req.getMonthlyObligation() != null) {
            putManualField(manual, "monthlyObligation", req.getMonthlyObligation().toPlainString());
        }
        if (req.getGstIncome() != null) {
            putManualField(manual, "gstIncome", req.getGstIncome().toPlainString());
        }
        if (req.getBankStatementIncome() != null) {
            putManualField(manual, "bankStatementIncome", req.getBankStatementIncome().toPlainString());
        }
        if (req.getAverageBankBalance() != null) {
            putManualField(manual, "averageBankBalance", req.getAverageBankBalance().toPlainString());
        }
        if (req.getObligationRatio() != null) {
            putManualField(manual, "obligationRatio", req.getObligationRatio().toPlainString());
        }
        if (req.getEmiObligation() != null) {
            putManualField(manual, "emiObligation", req.getEmiObligation().toPlainString());
        }
        if (req.getPropertyValue() != null) {
            putManualField(manual, "propertyValue", req.getPropertyValue().toPlainString());
        }
        if (req.getLtv() != null) {
            putManualField(manual, "ltv", req.getLtv().toPlainString());
        }
        if (req.getBusinessVintageMonths() != null) {
            putManualField(manual, "businessVintageMonths", String.valueOf(req.getBusinessVintageMonths()));
        }
        if (req.getIndustryRisk() != null) {
            putManualField(manual, "industryRisk", req.getIndustryRisk().trim().toUpperCase());
        }
        if (req.getRepaymentHistory() != null) {
            putManualField(manual, "repaymentHistory", req.getRepaymentHistory().trim().toUpperCase());
        }
        if (req.getEbitdaProxy() != null) {
            putManualField(manual, "ebitdaProxy", req.getEbitdaProxy().toPlainString());
        }
        if (req.getLeverageRatio() != null) {
            putManualField(manual, "leverageRatio", req.getLeverageRatio().toPlainString());
        }
        if (req.getSupportingDocumentIds() != null && !req.getSupportingDocumentIds().isEmpty()) {
            List<String> ids = req.getSupportingDocumentIds().stream().map(UUID::toString).toList();
            putManualField(manual, "supportingDocumentIds", ids);
        }
        if (req.getState() != null) {
            putManualField(manual, "state", req.getState());
        }
        if (req.getCity() != null) {
            putManualField(manual, "city", req.getCity());
        }
        if (req.getCreditRemarks() != null) {
            putManualField(manual, "creditRemarks", req.getCreditRemarks());
        }
        if (req.getManualKycOutcome() != null) {
            putManualField(manual, "manualKycOutcome", req.getManualKycOutcome().trim().toUpperCase());
        }

        if (req.getManualBureauScore() != null) {
            app.setManualBureauScore(req.getManualBureauScore());
            putManualField(manual, "bureauScore", String.valueOf(req.getManualBureauScore()));
        }
        if (req.getManualBureauRemarks() != null) {
            app.setManualBureauRemarks(req.getManualBureauRemarks());
            putManualField(manual, "bureauRemarks", req.getManualBureauRemarks());
        }

        mergeScorecardFieldsFromRequest(manual, req);

        if (req.getDecisionSources() != null) {
            Map<String, Object> ds = new LinkedHashMap<>();
            if (req.getDecisionSources().getBureauScoreSource() != null) {
                ds.put("bureauScoreSource", normalizeSource(req.getDecisionSources().getBureauScoreSource()));
            }
            if (req.getDecisionSources().getIncomeSource() != null) {
                ds.put("incomeSource", normalizeSource(req.getDecisionSources().getIncomeSource()));
            }
            if (req.getDecisionSources().getKycSource() != null) {
                ds.put("kycSource", normalizeSource(req.getDecisionSources().getKycSource()));
            }
            cc.put(DECISION_SOURCES, mergeDecisionSources(
                    (Map<String, Object>) Optional.ofNullable(cc.get(DECISION_SOURCES)).orElse(Map.of()),
                    ds));
        }

        cc.put(MANUAL, manual);
        cc.put(PROVIDER_SNAPSHOT, buildProviderSnapshot(app));
        fi.put(ROOT, cc);
        app.setFinancialInfo(fi);
    }

    /**
     * Merges scorecard underwriting fields (OTHER / GST / bank analytics) without touching bureau/KYC decision sources.
     * Used by staff roles that cannot access full manual credit input.
     */
    @SuppressWarnings("unchecked")
    public void mergeScorecardInputsOnly(LoanApplication app, ManualCreditInputsRequest req) {
        if (req == null) {
            return;
        }
        Map<String, Object> fi = app.getFinancialInfo() != null
                ? new HashMap<>(app.getFinancialInfo())
                : new HashMap<>();
        Map<String, Object> cc = fi.get(ROOT) instanceof Map<?, ?> m
                ? new LinkedHashMap<>((Map<String, Object>) m)
                : new LinkedHashMap<>();
        Map<String, Object> manual = cc.get(MANUAL) instanceof Map<?, ?> m2
                ? new LinkedHashMap<>((Map<String, Object>) m2)
                : new LinkedHashMap<>();
        mergeScorecardFieldsFromRequest(manual, req);
        cc.put(MANUAL, manual);
        cc.put(PROVIDER_SNAPSHOT, buildProviderSnapshot(app));
        fi.put(ROOT, cc);
        app.setFinancialInfo(fi);
    }

    @SuppressWarnings("unchecked")
    private void mergeScorecardFieldsFromRequest(Map<String, Object> manual, ManualCreditInputsRequest req) {
        mergeScorecardMetricField(manual, "avgDailyBalance3m", req.getAvgDailyBalance3m());
        mergeScorecardMetricField(manual, "avgMonthlyTransactions3m", req.getAvgMonthlyTransactions3m());
        mergeScorecardMetricField(manual, "avgMonthlySettlements3m", req.getAvgMonthlySettlements3m());
        mergeScorecardMetricField(manual, "monthlyTransactions3m", req.getMonthlyTransactions3m());
        mergeScorecardMetricField(manual, "inwardChequeReturns3m", req.getInwardChequeReturns3m());
        mergeScorecardMetricField(manual, "avgDailySettlements3m", req.getAvgDailySettlements3m());
        mergeScorecardMetricField(manual, "noOfTxns60days", req.getNoOfTxns60days());
        mergeScorecardMetricField(manual, "txnMth1", req.getTxnMth1());
        mergeScorecardMetricField(manual, "txnMth2", req.getTxnMth2());
        mergeScorecardMetricField(manual, "txnMth3", req.getTxnMth3());
        mergeScorecardMetricField(manual, "avgGmv3m", req.getAvgGmv3m());
        mergeScorecardMetricField(manual, "active90days", req.getActive90days());
        mergeScorecardMetricField(manual, "annualGstTurnover", req.getAnnualGstTurnover());
        mergeScorecardMetricField(manual, "itrIncome", req.getItrIncome());
        mergeScorecardMetricField(manual, "pat", req.getPat());
        mergeScorecardMetricField(manual, "interestCoverage", req.getInterestCoverage());
        mergeScorecardMetricField(manual, "debtToEquity", req.getDebtToEquity());
        mergeScorecardMetricField(manual, "ebitda", req.getEbitda());
        mergeScorecardMetricField(manual, "debtService", req.getDebtService());
        mergeScorecardMetricField(manual, "annualBankingTurnover", req.getAnnualBankingTurnover());
        mergeScorecardMetricField(manual, "bankingTurnoverPctGst", req.getBankingTurnoverPctGst());
        mergeScorecardMetricField(manual, "abbObligationMultiple", req.getAbbObligationMultiple());
        mergeScorecardMetricField(manual, "ccUtilisationPct", req.getCcUtilisationPct());
        mergeScorecardMetricField(manual, "chequeBounces12m", req.getChequeBounces12m());
        mergeScorecardMetricField(manual, "chequeBounces3m", req.getChequeBounces3m());
        mergeScorecardMetricField(manual, "liveUnsecuredLoanCount", req.getLiveUnsecuredLoanCount());
        mergeScorecardMetricField(manual, "bureauEnquiries3m", req.getBureauEnquiries3m());
        mergeScorecardYesNoField(manual, "ntcFlag", req.getNtcFlag());
        mergeScorecardYesNoField(manual, "residenceOwned", req.getResidenceOwned());
        mergeScorecardMetricField(manual, "residenceStability", req.getResidenceStability());
        mergeScorecardMetricField(manual, "businessStability", req.getBusinessStability());
        mergeScorecardYesNoField(manual, "existingLoanTrackRecordAll", req.getExistingLoanTrackRecordAll());
        mergeScorecardYesNoField(manual, "existingLoanTrackRecord15d", req.getExistingLoanTrackRecord15d());
        mergeScorecardYesNoField(manual, "qrTxnEDI", req.getQrTxnEDI());
        mergeScorecardYesNoField(manual, "eligibleOnePointFiveX", req.getEligibleOnePointFiveX());
        mergeScorecardMetricField(manual, "existingFbLimits", req.getExistingFbLimits());
        mergeScorecardMetricField(manual, "existingNfbLimits", req.getExistingNfbLimits());
        mergeScorecardMetricField(manual, "tolTnw", req.getTolTnw());
        mergeScorecardYesNoField(manual, "officeOwned", req.getOfficeOwned());
        mergeScorecardMetricField(manual, "dscr", req.getDscr());
        if (req.getScorecardMetrics() != null && !req.getScorecardMetrics().isEmpty()) {
            Map<String, Object> existing = manual.get("scorecardMetrics") instanceof Map<?, ?> m
                    ? new LinkedHashMap<>((Map<String, Object>) m)
                    : new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : req.getScorecardMetrics().entrySet()) {
                if (e.getKey() != null && !e.getKey().isBlank() && e.getValue() != null) {
                    putManualField(existing, e.getKey().trim(), e.getValue());
                }
            }
            manual.put("scorecardMetrics", existing);
        }
    }

    /**
     * Records a controlled KYC process override so underwriting can proceed using manual KYC pass
     * even when provider/computed KYC outcome is FAIL.
     */
    @SuppressWarnings("unchecked")
    public void applyManualKycPassOnProcessOverride(LoanApplication app, String remarks) {
        Map<String, Object> fi = app.getFinancialInfo() != null
                ? new LinkedHashMap<>(app.getFinancialInfo())
                : new LinkedHashMap<>();
        Map<String, Object> cc = fi.get(ROOT) instanceof Map<?, ?> existingCc
                ? new LinkedHashMap<>((Map<String, Object>) existingCc)
                : new LinkedHashMap<>();
        Map<String, Object> manual = cc.get(MANUAL) instanceof Map<?, ?> existingManual
                ? new LinkedHashMap<>((Map<String, Object>) existingManual)
                : new LinkedHashMap<>();
        Map<String, Object> ds = cc.get(DECISION_SOURCES) instanceof Map<?, ?> existingDs
                ? new LinkedHashMap<>((Map<String, Object>) existingDs)
                : new LinkedHashMap<>();

        putManualField(manual, "manualKycOutcome", "PASS");
        if (remarks != null && !remarks.isBlank()) {
            putManualField(manual, "kycOverrideRemarks", remarks.trim());
        }
        ds.put("kycSource", SRC_MANUAL);
        cc.put(MANUAL, manual);
        cc.put(DECISION_SOURCES, ds);
        fi.put(ROOT, cc);
        app.setFinancialInfo(fi);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mergeDecisionSources(Map<String, Object> old, Map<String, Object> updates) {
        Map<String, Object> o = new LinkedHashMap<>(old);
        o.putAll(updates);
        return o;
    }

    private void putManualField(Map<String, Object> manual, String key, Object value) {
        Map<String, Object> cell = new LinkedHashMap<>();
        cell.put("value", value);
        cell.put("source", SRC_MANUAL);
        cell.put("updatedAt", Instant.now().toString());
        manual.put(key, cell);
    }

    private void mergeScorecardMetricField(Map<String, Object> manual, String key, Object value) {
        if (value == null) {
            return;
        }
        putManualField(manual, key, value instanceof BigDecimal b ? b.toPlainString() : value);
    }

    private void mergeScorecardYesNoField(Map<String, Object> manual, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        putManualField(manual, key, value.trim().toUpperCase());
    }

    private static void putScorecardValue(Map<String, Object> manual, Map<String, BigDecimal> out, String key, String manKey) {
        if (manual.get(manKey) == null) {
            return;
        }
        BigDecimal b = toYesNoOrNumberBd(unwrapValue(manual.get(manKey)));
        if (b != null) {
            out.put(key, b);
        }
    }

    private static void putScorecardValueExact(Map<String, Object> manual, Map<String, BigDecimal> out, String key) {
        putScorecardValue(manual, out, key, key);
    }

    @SuppressWarnings("unchecked")
    private static void mergeScorecardMetricsMap(Map<String, Object> manual, Map<String, BigDecimal> out) {
        Object raw = manual.get("scorecardMetrics");
        if (!(raw instanceof Map<?, ?> map)) {
            return;
        }
        for (Map.Entry<?, ?> e : map.entrySet()) {
            String key = String.valueOf(e.getKey()).trim();
            if (key.isEmpty()) {
                continue;
            }
            BigDecimal b = toYesNoOrNumberBd(unwrapValue(e.getValue()));
            if (b != null) {
                out.put(key, b);
            }
        }
    }

    public Map<String, Object> buildProviderSnapshot(LoanApplication app) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("bureauScore", app.getBureauScore());
        String k = "";
        try {
            k = String.valueOf(kycOrchestrationService.computeKycOutcome(app.getId())
                    .getOrDefault("outcome", ""));
        } catch (Exception e) {
            k = "UNKNOWN";
        }
        snap.put("kycOutcome", k);
        if (app.getPersonalInfo() != null) {
            if (app.getPersonalInfo().get("panName") != null) {
                snap.put("panName", str(app.getPersonalInfo().get("panName")));
            }
            if (app.getPersonalInfo().get("state") != null) {
                snap.put("state", str(app.getPersonalInfo().get("state")));
            }
            if (app.getPersonalInfo().get("city") != null) {
                snap.put("city", str(app.getPersonalInfo().get("city")));
            }
        }
        if (app.getFinancialInfo() != null && app.getFinancialInfo().get("monthlyIncome") != null) {
            snap.put("monthlyIncome", str(app.getFinancialInfo().get("monthlyIncome")));
        }
        snap.put("capturedAt", Instant.now().toString());
        return snap;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    public EffectiveUnderwritingContext resolveEffective(LoanApplication app, String computedKycOutcome) {
        @SuppressWarnings("unchecked")
        Map<String, Object> fi = app.getFinancialInfo() != null ? app.getFinancialInfo() : Map.of();
        @SuppressWarnings("unchecked")
        Map<String, Object> cc = (Map<String, Object>) fi.getOrDefault(ROOT, Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> ds = (Map<String, Object>) cc.getOrDefault(DECISION_SOURCES, Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> manual = (Map<String, Object>) cc.getOrDefault(MANUAL, Map.of());

        String bureauSource = str(ds.get("bureauScoreSource"));
        if (bureauSource == null || bureauSource.isBlank()) {
            bureauSource = (app.getManualBureauScore() != null && app.getManualBureauScore() > 0)
                    ? SRC_MANUAL
                    : SRC_PROVIDER;
        }
        String incomeSource = Optional.ofNullable(str(ds.get("incomeSource"))).filter(s -> !s.isBlank()).orElse(SRC_PROVIDER);
        String kycSource = Optional.ofNullable(str(ds.get("kycSource"))).filter(s -> !s.isBlank()).orElse(SRC_PROVIDER);

        int effBureau = resolveBureau(app, manual, bureauSource);
        boolean kycPass = resolveKyc(kycSource, manual, computedKycOutcome);
        BigDecimal inc = resolveIncome(app, fi, manual, incomeSource);
        BigDecimal obl = resolveObligation(fi, manual, incomeSource);
        boolean safeDemoFallback = shouldApplySafeFallback(app, fi, cc, inc, obl);
        if (safeDemoFallback) {
            if (effBureau <= 0) {
                effBureau = DEMO_DEFAULT_BUREAU_SCORE;
                bureauSource = "DEMO_FALLBACK";
            }
            if (inc == null || inc.compareTo(BigDecimal.ZERO) <= 0) {
                inc = DEMO_DEFAULT_MONTHLY_INCOME;
                incomeSource = "DEMO_FALLBACK";
            }
            if (obl == null || obl.compareTo(BigDecimal.ZERO) < 0) {
                obl = DEMO_DEFAULT_MONTHLY_OBLIGATION;
            }
            if (!kycPass && !isHardKycFail(computedKycOutcome)) {
                kycPass = true;
                kycSource = "DEMO_FALLBACK";
            }
        }
        String st = null;
        if (manual.get("state") != null) {
            st = str(unwrapValue(manual.get("state")));
        } else if (app.getPersonalInfo() != null && app.getPersonalInfo().get("state") != null) {
            st = str(app.getPersonalInfo().get("state"));
        }
        String city = null;
        if (manual.get("city") != null) {
            city = str(unwrapValue(manual.get("city")));
        } else if (app.getPersonalInfo() != null && app.getPersonalInfo().get("city") != null) {
            city = str(app.getPersonalInfo().get("city"));
        }

        Map<String, BigDecimal> sc = new LinkedHashMap<>();
        putScorecardValue(manual, sc, "GST_INCOME", "gstIncome");
        putScorecardValue(manual, sc, "BANK_STATEMENT_INCOME", "bankStatementIncome");
        putScorecardValue(manual, sc, "AVERAGE_BANK_BALANCE", "averageBankBalance");
        putScorecardValue(manual, sc, "PROPERTY_VALUE", "propertyValue");
        putScorecardValue(manual, sc, "EBITDA_PROXY", "ebitdaProxy");
        putScorecardValue(manual, sc, "LEVERAGE_RATIO", "leverageRatio");
        putScorecardValue(manual, sc, "BUSINESS_VINTAGE_MONTHS", "businessVintageMonths");
        putScorecardValueExact(manual, sc, "avgDailyBalance3m");
        putScorecardValueExact(manual, sc, "avgMonthlyTransactions3m");
        putScorecardValueExact(manual, sc, "avgMonthlySettlements3m");
        putScorecardValueExact(manual, sc, "monthlyTransactions3m");
        putScorecardValueExact(manual, sc, "inwardChequeReturns3m");
        putScorecardValueExact(manual, sc, "avgDailySettlements3m");
        putScorecardValueExact(manual, sc, "noOfTxns60days");
        putScorecardValueExact(manual, sc, "txnMth1");
        putScorecardValueExact(manual, sc, "txnMth2");
        putScorecardValueExact(manual, sc, "txnMth3");
        putScorecardValueExact(manual, sc, "avgGmv3m");
        putScorecardValueExact(manual, sc, "active90days");
        putScorecardValue(manual, sc, "ANNUAL_GST_TURNOVER", "annualGstTurnover");
        putScorecardValue(manual, sc, "ITR_INCOME", "itrIncome");
        putScorecardValue(manual, sc, "PAT", "pat");
        putScorecardValue(manual, sc, "INTEREST_COVERAGE", "interestCoverage");
        putScorecardValue(manual, sc, "DEBT_TO_EQUITY", "debtToEquity");
        putScorecardValue(manual, sc, "EBITDA", "ebitda");
        putScorecardValue(manual, sc, "DEBT_SERVICE", "debtService");
        putScorecardValue(manual, sc, "ANNUAL_BANKING_TURNOVER", "annualBankingTurnover");
        putScorecardValue(manual, sc, "BANKING_TURNOVER_PCT_GST", "bankingTurnoverPctGst");
        putScorecardValue(manual, sc, "ABB_OBLIGATION_MULTIPLE", "abbObligationMultiple");
        putScorecardValue(manual, sc, "CC_UTILISATION_PCT", "ccUtilisationPct");
        putScorecardValue(manual, sc, "CHEQUE_BOUNCES_12M", "chequeBounces12m");
        putScorecardValue(manual, sc, "CHEQUE_BOUNCES_3M", "chequeBounces3m");
        putScorecardValue(manual, sc, "LIVE_UNSECURED_LOAN_COUNT", "liveUnsecuredLoanCount");
        putScorecardValue(manual, sc, "BUREAU_ENQUIRIES_3M", "bureauEnquiries3m");
        putScorecardValue(manual, sc, "NTC_FLAG", "ntcFlag");
        putScorecardValueExact(manual, sc, "residenceOwned");
        putScorecardValueExact(manual, sc, "residenceStability");
        putScorecardValueExact(manual, sc, "businessStability");
        putScorecardValueExact(manual, sc, "existingLoanTrackRecordAll");
        putScorecardValueExact(manual, sc, "existingLoanTrackRecord15d");
        putScorecardValueExact(manual, sc, "qrTxnEDI");
        putScorecardValueExact(manual, sc, "eligibleOnePointFiveX");
        putScorecardValue(manual, sc, "EXISTING_FB_LIMITS", "existingFbLimits");
        putScorecardValue(manual, sc, "EXISTING_NFB_LIMITS", "existingNfbLimits");
        putScorecardValue(manual, sc, "TOL_TNW", "tolTnw");
        putScorecardValueExact(manual, sc, "officeOwned");
        putScorecardValue(manual, sc, "DSCR", "dscr");
        mergeScorecardMetricsMap(manual, sc);
        // GST / ITR / bank OCR extract overrides stub manual values for extract-backed keys.
        applyDocumentExtractScorecardValues(app, sc);
        applyApplicationScorecardParameters(app, sc);
        if (inc != null) {
            sc.put("MONTHLY_INCOME", inc);
        }
        if (obl != null) {
            sc.put("EMI_OBLIGATION", obl);
            sc.put("MONTHLY_OBLIGATION", obl);
        }
        if (manual.get("emiObligation") != null) {
            BigDecimal emiO = toBd(unwrapValue(manual.get("emiObligation")));
            if (emiO != null) {
                sc.put("EMI_OBLIGATION", emiO);
            }
        }
        BigDecimal ratio = null;
        if (manual.get("obligationRatio") != null) {
            ratio = toBd(unwrapValue(manual.get("obligationRatio")));
        }
        if (ratio == null && inc != null && obl != null && inc.compareTo(BigDecimal.ZERO) > 0) {
            ratio = obl.divide(inc, 6, RoundingMode.HALF_UP);
        }
        if (ratio == null && safeDemoFallback) {
            ratio = DEMO_DEFAULT_FOIR_RATIO;
        }
        if (ratio != null) {
            sc.put("OBLIGATION_RATIO", ratio.multiply(BigDecimal.valueOf(100)));
        }
        applyMissingScorecardDefaults(app, sc, inc, obl);
        if (inc == null && sc.get("MONTHLY_INCOME") != null) {
            inc = sc.get("MONTHLY_INCOME");
        }
        if (obl == null && sc.get("EMI_OBLIGATION") != null) {
            obl = sc.get("EMI_OBLIGATION");
        }
        if (manual.get("ltv") != null) {
            putScorecardValue(manual, sc, "LTV", "ltv");
        } else if (app.getRequestedAmount() != null) {
            BigDecimal prop = sc.get("PROPERTY_VALUE");
            if (prop != null && prop.compareTo(BigDecimal.ZERO) > 0) {
                sc.put("LTV", app.getRequestedAmount()
                        .divide(prop, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)));
            }
        }
        String ind = str(unwrapValue(manual.get("industryRisk")));
        if (ind != null && !ind.isBlank()) {
            sc.put("INDUSTRY_RISK", "LOW".equalsIgnoreCase(ind) ? BigDecimal.ONE : BigDecimal.ZERO);
        }
        String rep = str(unwrapValue(manual.get("repaymentHistory")));
        if (rep != null && !rep.isBlank()) {
            sc.put("REPAYMENT_HISTORY", "CLEAN".equalsIgnoreCase(rep) ? BigDecimal.ONE : BigDecimal.ZERO);
        }
        if (kycPass) {
            sc.put("KYC_QUALITY", BigDecimal.ONE);
        } else {
            sc.put("KYC_QUALITY", BigDecimal.ZERO);
        }
        if (safeDemoFallback) {
            sc.put("DEMO_FALLBACK_ACTIVE", BigDecimal.ONE);
        }
        sc.put("BUREAU_SCORE", BigDecimal.valueOf(effBureau));
        applyProgramInputScorecardValues(app, sc);
        limitSizingService.applyComputedMetrics(app, sc);
        return new EffectiveUnderwritingContext(
                effBureau, kycPass, inc, obl, st, city, bureauSource, incomeSource, kycSource, sc);
    }

    private void applyProgramInputScorecardValues(LoanApplication app, Map<String, BigDecimal> sc) {
        invoiceDiscountingVintageService.evaluate(app).ifPresent(v -> {
            putScorecardIfPresent(sc, "DEPENDENCY_VINTAGE_PERCENT", v.getBorrowerDependencyVintagePercent());
            if (v.getBorrowerAnchorRelationshipVintageMonths() != null) {
                putScorecardIfPresent(
                        sc,
                        "ANCHOR_RELATIONSHIP_VINTAGE_MONTHS",
                        BigDecimal.valueOf(v.getBorrowerAnchorRelationshipVintageMonths()));
            }
            putScorecardIfPresent(sc, "PROGRAM_DEPENDENCY_VINTAGE_PERCENT", v.getProgramDependencyVintagePercent());
            if (v.getProgramAnchorRelationshipVintageMonths() != null) {
                putScorecardIfPresent(
                        sc,
                        "PROGRAM_ANCHOR_RELATIONSHIP_VINTAGE_MONTHS",
                        BigDecimal.valueOf(v.getProgramAnchorRelationshipVintageMonths()));
            }
        });
    }

    private static void putScorecardIfPresent(Map<String, BigDecimal> sc, String key, BigDecimal value) {
        if (value != null) {
            sc.put(key, value);
        }
    }

    private static boolean shouldApplySafeFallback(
            LoanApplication app,
            Map<String, Object> fi,
            Map<String, Object> cc,
            BigDecimal income,
            BigDecimal obligation) {
        return isExplicitDemo(fi) || isExplicitDemo(cc) || isExplicitDemo(app.getPersonalInfo());
    }

    private static boolean isExplicitDemo(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return false;
        }
        Object[] flags = new Object[] {
                data.get("demo"),
                data.get("isDemo"),
                data.get("demoMode"),
                data.get("testMode"),
                data.get("sandbox")
        };
        for (Object flag : flags) {
            if (asBoolean(flag)) {
                return true;
            }
        }
        return false;
    }

    private static boolean asBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value == null) {
            return false;
        }
        String s = String.valueOf(value).trim();
        return "true".equalsIgnoreCase(s)
                || "yes".equalsIgnoreCase(s)
                || "1".equals(s)
                || "demo".equalsIgnoreCase(s);
    }

    private static boolean isBlankValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        if (value instanceof List<?> list) {
            return list.isEmpty();
        }
        return String.valueOf(value).trim().isEmpty();
    }

    private static boolean isHardKycFail(String outcome) {
        if (outcome == null) {
            return false;
        }
        String k = outcome.trim().toUpperCase();
        return "FAIL".equals(k) || "FAILED".equals(k) || "REJECT".equals(k) || "REJECTED".equals(k);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> buildReadView(LoanApplication app) {
        Map<String, Object> view = new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        Map<String, Object> fi = app.getFinancialInfo() != null ? app.getFinancialInfo() : Map.of();
        @SuppressWarnings("unchecked")
        Map<String, Object> cc = (Map<String, Object>) fi.getOrDefault(ROOT, Map.of());
        view.put("creditControl", cc);
        view.put("providerBureauScore", app.getBureauScore());
        view.put("manualBureauScore", app.getManualBureauScore());
        String computedKyc;
        try {
            computedKyc = String.valueOf(kycOrchestrationService.computeKycOutcome(app.getId()).getOrDefault("outcome", ""));
        } catch (Exception e) {
            computedKyc = "UNKNOWN";
        }
        view.put("computedKycOutcome", computedKyc);
        var ctx = resolveEffective(app, computedKyc);
        view.put("effective", ctx.toMap());
        return view;
    }

    private static int resolveBureau(LoanApplication app, Map<String, Object> manual, String bureauSource) {
        if (SRC_MANUAL.equalsIgnoreCase(bureauSource)) {
            if (app.getManualBureauScore() != null && app.getManualBureauScore() > 0) {
                return app.getManualBureauScore();
            }
            if (manual.get("bureauScore") != null) {
                try {
                    return Integer.parseInt(str(unwrapValue(manual.get("bureauScore"))));
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
            return 0;
        }
        if (app.getBureauScore() != null && app.getBureauScore() > 0) {
            return app.getBureauScore();
        }
        if (app.getManualBureauScore() != null && app.getManualBureauScore() > 0) {
            return app.getManualBureauScore();
        }
        return 0;
    }

    private static boolean resolveKyc(String kycSource, Map<String, Object> manual, String computed) {
        if (SRC_MANUAL.equalsIgnoreCase(kycSource)) {
            Object o = manual.get("manualKycOutcome");
            if (o == null) {
                return false;
            }
            String s = str(unwrapValue(o));
            return "PASS".equalsIgnoreCase(s);
        }
        return "PASS".equalsIgnoreCase(String.valueOf(computed).trim());
    }

    private static BigDecimal resolveIncome(
            LoanApplication app, Map<String, Object> fi, Map<String, Object> manual, String incomeSource) {
        if (SRC_MANUAL.equalsIgnoreCase(incomeSource) && manual.get("monthlyIncome") != null) {
            return toBd(unwrapValue(manual.get("monthlyIncome")));
        }
        BigDecimal fromFi = toBd(fi.get("monthlyIncome"));
        if (fromFi != null && fromFi.compareTo(BigDecimal.ZERO) > 0) {
            return fromFi;
        }
        if (app.getPersonalInfo() != null) {
            BigDecimal fromProfile = toBd(app.getPersonalInfo().get("monthlyNetIncome"));
            if (fromProfile != null && fromProfile.compareTo(BigDecimal.ZERO) > 0) {
                return fromProfile;
            }
        }
        if (app.getBusinessInfo() != null) {
            BigDecimal fromBusiness = toBd(app.getBusinessInfo().get("monthlyIncome"));
            if (fromBusiness != null && fromBusiness.compareTo(BigDecimal.ZERO) > 0) {
                return fromBusiness;
            }
        }
        return null;
    }

    /**
     * Fills scorecard parameters that normally depend on bank-statement extraction when no verified value exists.
     * Never overwrites keys already present in the effective scorecard map.
     */
    private static void applyMissingScorecardDefaults(
            LoanApplication app, Map<String, BigDecimal> sc, BigDecimal income, BigDecimal obligation) {
        boolean applied = false;
        if (!sc.containsKey("MONTHLY_INCOME") || isZeroOrMissing(sc.get("MONTHLY_INCOME"))) {
            BigDecimal fallback = income;
            if (fallback == null || fallback.compareTo(BigDecimal.ZERO) <= 0) {
                fallback = incomeFromProfile(app);
            }
            if (fallback == null || fallback.compareTo(BigDecimal.ZERO) <= 0) {
                fallback = GAP_DEFAULT_MONTHLY_INCOME;
            }
            sc.put("MONTHLY_INCOME", fallback);
            applied = true;
        }
        if (!sc.containsKey("EMI_OBLIGATION") || isZeroOrMissing(sc.get("EMI_OBLIGATION"))) {
            BigDecimal fallback = obligation;
            if (fallback == null || fallback.compareTo(BigDecimal.ZERO) < 0) {
                fallback = GAP_DEFAULT_MONTHLY_OBLIGATION;
            }
            sc.put("EMI_OBLIGATION", fallback);
            sc.put("MONTHLY_OBLIGATION", fallback);
            applied = true;
        } else if (!sc.containsKey("MONTHLY_OBLIGATION") || isZeroOrMissing(sc.get("MONTHLY_OBLIGATION"))) {
            sc.put("MONTHLY_OBLIGATION", sc.get("EMI_OBLIGATION"));
            applied = true;
        }
        if (!sc.containsKey("DTI_RATIO") || isZeroOrMissing(sc.get("DTI_RATIO"))) {
            BigDecimal inc = sc.get("MONTHLY_INCOME");
            BigDecimal obl = sc.get("EMI_OBLIGATION");
            if (inc != null && inc.compareTo(BigDecimal.ZERO) > 0 && obl != null) {
                sc.put(
                        "DTI_RATIO",
                        obl.divide(inc, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)));
            } else {
                sc.put("DTI_RATIO", GAP_DEFAULT_DTI_RATIO);
            }
            applied = true;
        }
        if (!sc.containsKey("OBLIGATION_RATIO") || isZeroOrMissing(sc.get("OBLIGATION_RATIO"))) {
            BigDecimal inc = sc.get("MONTHLY_INCOME");
            BigDecimal obl = sc.get("EMI_OBLIGATION");
            if (inc != null && inc.compareTo(BigDecimal.ZERO) > 0 && obl != null) {
                sc.put(
                        "OBLIGATION_RATIO",
                        obl.divide(inc, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)));
            } else {
                sc.put("OBLIGATION_RATIO", GAP_DEFAULT_FOIR_PERCENT);
            }
            applied = true;
        }
        if (!sc.containsKey("AVERAGE_BANK_BALANCE") || isZeroOrMissing(sc.get("AVERAGE_BANK_BALANCE"))) {
            sc.put("AVERAGE_BANK_BALANCE", GAP_DEFAULT_AVERAGE_BANK_BALANCE);
            applied = true;
        }
        applied |= putBankGapDefault(sc, "avgDailyBalance3m", GAP_DEFAULT_AVERAGE_BANK_BALANCE);
        applied |= putBankGapDefault(sc, "avgMonthlyTransactions3m", GAP_DEFAULT_BANK_METRIC);
        applied |= putBankGapDefault(sc, "avgMonthlySettlements3m", GAP_DEFAULT_BANK_METRIC);
        applied |= putBankGapDefault(sc, "monthlyTransactions3m", GAP_DEFAULT_BANK_METRIC);
        applied |= putBankGapDefault(sc, "inwardChequeReturns3m", BigDecimal.ZERO);
        applied |= putBankGapDefault(sc, "avgDailySettlements3m", GAP_DEFAULT_BANK_METRIC);
        applied |= putBankGapDefault(sc, "noOfTxns60days", GAP_DEFAULT_BANK_COUNT);
        applied |= putBankGapDefault(sc, "txnMth1", GAP_DEFAULT_BANK_METRIC);
        applied |= putBankGapDefault(sc, "txnMth2", GAP_DEFAULT_BANK_METRIC);
        applied |= putBankGapDefault(sc, "txnMth3", GAP_DEFAULT_BANK_METRIC);
        // SCF / invoice-discounting friendly dummies — fill when missing or implausible stubs.
        applied |= putScfGapDefault(sc, "ANNUAL_GST_TURNOVER", SCF_GAP_ANNUAL_GST_TURNOVER, SCF_MIN_ANNUAL_GST_TURNOVER);
        applied |= putScfGapDefault(sc, "ANNUAL_BANKING_TURNOVER", SCF_GAP_ANNUAL_BANKING_TURNOVER, new BigDecimal("1000000"));
        applied |= putScfGapDefault(sc, "ITR_INCOME", SCF_GAP_ITR_INCOME, new BigDecimal("300000"));
        applied |= putScfGapDefault(sc, "PAT", SCF_GAP_PAT, new BigDecimal("1000"));
        applied |= putScfGapDefault(sc, "INTEREST_COVERAGE", SCF_GAP_INTEREST_COVERAGE, new BigDecimal("0.1"));
        applied |= putScfGapDefault(sc, "DEBT_TO_EQUITY", SCF_GAP_DEBT_TO_EQUITY, new BigDecimal("0.1"));
        applied |= putScfGapDefault(sc, "EBITDA", SCF_GAP_EBITDA, new BigDecimal("1000"));
        applied |= putScfGapDefault(sc, "DEBT_SERVICE", SCF_GAP_DEBT_SERVICE, new BigDecimal("1000"));
        applied |= putBankGapDefault(sc, "TOL", new BigDecimal("3500000"));
        applied |= putBankGapDefault(sc, "TNW", new BigDecimal("5000000"));
        applied |= putBankGapDefault(sc, "LIVE_UNSECURED_LOAN_COUNT", new BigDecimal("2"));
        applied |= putBankGapDefault(sc, "BUREAU_ENQUIRIES_3M", new BigDecimal("5"));
        applied |= putBankGapDefault(sc, "NTC_FLAG", BigDecimal.ZERO);
        applied |= putBankGapDefault(sc, "BANKING_TURNOVER_PCT_GST", new BigDecimal("80"));
        applied |= putBankGapDefault(sc, "ABB_OBLIGATION_MULTIPLE", new BigDecimal("1.2"));
        applied |= putBankGapDefault(sc, "CC_UTILISATION_PCT", new BigDecimal("70"));
        applied |= putBankGapDefault(sc, "CHEQUE_BOUNCES_12M", new BigDecimal("2"));
        applied |= putBankGapDefault(sc, "CHEQUE_BOUNCES_3M", BigDecimal.ZERO);
        applied |= putBankGapDefault(sc, "EXISTING_FB_LIMITS", new BigDecimal("1000000"));
        applied |= putBankGapDefault(sc, "EXISTING_NFB_LIMITS", new BigDecimal("500000"));
        applied |= putBankGapDefault(sc, "officeOwned", BigDecimal.ONE);
        applied |= putBankGapDefault(sc, "residenceOwned", BigDecimal.ONE);
        applied |= putBankGapDefault(sc, "businessStability", new BigDecimal("3"));
        applied |= putBankGapDefault(sc, "DSCR", new BigDecimal("1.3"));
        applied |= putBankGapDefault(sc, "TOL_TNW", new BigDecimal("5"));
        if (applied) {
            sc.put("PROVIDER_GAP_DEFAULT_ACTIVE", BigDecimal.ONE);
        }
    }

    /**
     * Pulls GST / ITR / bank OCR dummy (or real) extract into the scorecard map.
     * Prefer Karza ITR return-forms mapped metrics when the borrower pull succeeded;
     * Prefer GST analysis REPORT mapped metrics over OCR stubs;
     * otherwise fall back to OCR/document stubs. Extract-backed keys always win over manual stubs.
     */
    @SuppressWarnings("unchecked")
    private void applyDocumentExtractScorecardValues(LoanApplication app, Map<String, BigDecimal> sc) {
        if (app == null || app.getId() == null || sc == null) {
            return;
        }
        boolean seenItr = applyItrApiMappedMetrics(app.getId(), sc);
        boolean seenGst = applyGstAnalysisMappedMetrics(app.getId(), sc);

        List<Document> docs;
        try {
            docs = documentRepository.findByApplicationIdOrderByCreatedAtDesc(app.getId());
        } catch (Exception e) {
            return;
        }
        if (docs == null || docs.isEmpty()) {
            return;
        }
        boolean seenBank = false;
        for (Document doc : docs) {
            if (doc == null || doc.getDocumentType() == null) {
                continue;
            }
            String type = doc.getDocumentType().trim().toUpperCase(Locale.ROOT);
            try {
                if (!seenGst && ("GST_RETURN".equals(type) || "GST_RETURNS".equals(type) || "GST_CERTIFICATE".equals(type) || "GST_STATEMENT".equals(type))) {
                    Map<String, Object> extracted = ocrExtractionService.extractFromDocument(
                            doc.getId(), type, doc.getStorageKey());
                    applyExtractedMap(sc, extracted);
                    seenGst = true;
                } else if (!seenItr && "ITR".equals(type)) {
                    Map<String, Object> extracted = ocrExtractionService.extractFromDocument(
                            doc.getId(), type, doc.getStorageKey());
                    applyExtractedMap(sc, extracted);
                    seenItr = true;
                } else if (!seenBank && "BANK_STATEMENT".equals(type)) {
                    Map<String, Object> extracted = ocrExtractionService.extractFromDocument(
                            doc.getId(), type, doc.getStorageKey());
                    applyExtractedMap(sc, extracted);
                    seenBank = true;
                }
            } catch (Exception ignored) {
                /* keep manual / gap defaults */
            }
            if (seenGst && seenItr && seenBank) {
                break;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private boolean applyItrApiMappedMetrics(UUID applicationId, Map<String, BigDecimal> sc) {
        try {
            Optional<KycStepResult> latest = kycStepResultRepository
                    .findTopByApplicationIdAndStepTypeOrderByCreatedAtDesc(
                            applicationId, KycStepType.ITR_RETURN_FORMS);
            if (latest.isEmpty()) {
                return false;
            }
            KycStepResult result = latest.get();
            if (result.getOutcome() != StepOutcome.SUCCESS && !result.isOverridden()) {
                return false;
            }
            Map<String, Object> parsed = result.getParsedData();
            if (parsed == null) {
                return false;
            }
            Object metricsObj = parsed.get("mappedMetrics");
            if (!(metricsObj instanceof Map<?, ?> raw)) {
                return false;
            }
            Map<String, Object> metrics = (Map<String, Object>) raw;
            Map<String, Object> ocrShape = new HashMap<>();
            ocrShape.put("extractedData", metrics);
            applyExtractedMap(sc, ocrShape);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private boolean applyGstAnalysisMappedMetrics(UUID applicationId, Map<String, BigDecimal> sc) {
        try {
            List<KycStepResult> all = kycStepResultRepository.findByApplicationIdOrderByCreatedAtAsc(applicationId);
            for (int i = all.size() - 1; i >= 0; i--) {
                KycStepResult result = all.get(i);
                if (result.getStepType() != KycStepType.GST_ANALYSIS) {
                    continue;
                }
                if (result.getOutcome() != StepOutcome.SUCCESS && !result.isOverridden()) {
                    continue;
                }
                Map<String, Object> parsed = result.getParsedData();
                if (parsed == null || !"REPORT".equalsIgnoreCase(String.valueOf(parsed.get("phase")))) {
                    continue;
                }
                Object metricsObj = parsed.get("mappedMetrics");
                if (!(metricsObj instanceof Map<?, ?> raw)) {
                    continue;
                }
                Map<String, Object> metrics = (Map<String, Object>) raw;
                Map<String, Object> ocrShape = new HashMap<>();
                ocrShape.put("extractedData", metrics);
                applyExtractedMap(sc, ocrShape);
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static void applyExtractedMap(Map<String, BigDecimal> sc, Map<String, Object> ocrResult) {
        if (ocrResult == null || sc == null) {
            return;
        }
        Object data = ocrResult.get("extractedData");
        if (!(data instanceof Map<?, ?> raw)) {
            return;
        }
        Map<String, Object> extracted = (Map<String, Object>) raw;
        putExtractBd(sc, "ANNUAL_GST_TURNOVER", extracted.get("annualGstTurnover"));
        putExtractBd(sc, "avgGmv3m", extracted.get("avgGmv3m"));
        putExtractBd(sc, "active90days", extracted.get("active90days"));
        putExtractBd(sc, "GST_INCOME", extracted.get("gstIncome"));
        putExtractBd(sc, "ITR_INCOME", firstNonNull(extracted.get("itrIncome"), extracted.get("grossTotalIncome")));
        putExtractBd(sc, "PAT", extracted.get("pat"));
        putExtractBd(sc, "INTEREST_COVERAGE", extracted.get("interestCoverage"));
        putExtractBd(sc, "DEBT_TO_EQUITY", extracted.get("debtToEquity"));
        putExtractBd(sc, "EBITDA", extracted.get("ebitda"));
        putExtractBd(sc, "DEBT_SERVICE", extracted.get("debtService"));
        putExtractBd(sc, "TOL", extracted.get("tol"));
        putExtractBd(sc, "TNW", extracted.get("tnw"));
        putExtractBd(sc, "ANNUAL_BANKING_TURNOVER", firstNonNull(extracted.get("annualBankingTurnover"), extracted.get("totalCredits")));
        putExtractBd(sc, "BANKING_TURNOVER_PCT_GST", extracted.get("bankingTurnoverPctGst"));
        putExtractBd(sc, "ABB_OBLIGATION_MULTIPLE", extracted.get("abbObligationMultiple"));
        putExtractBd(sc, "CC_UTILISATION_PCT", extracted.get("ccUtilisationPct"));
        putExtractBd(sc, "CHEQUE_BOUNCES_12M", extracted.get("chequeBounces12m"));
        putExtractBd(sc, "CHEQUE_BOUNCES_3M", extracted.get("chequeBounces3m"));
        putExtractBd(sc, "AVERAGE_BANK_BALANCE", firstNonNull(
                extracted.get("averageBankBalance"), extracted.get("closingBalance"), extracted.get("openingBalance")));
        putExtractBd(sc, "avgDailyBalance3m", firstNonNull(
                extracted.get("avgDailyBalance3m"), extracted.get("closingBalance")));
    }

    private static Object firstNonNull(Object a, Object b) {
        return a != null ? a : b;
    }

    private static Object firstNonNull(Object a, Object b, Object c) {
        if (a != null) {
            return a;
        }
        if (b != null) {
            return b;
        }
        return c;
    }

    private static void putExtractBd(Map<String, BigDecimal> sc, String key, Object raw) {
        BigDecimal b = toBd(raw);
        if (b == null) {
            return;
        }
        // Extract always wins for document-backed scorecard keys.
        if (DOCUMENT_EXTRACT_SCORECARD_KEYS.contains(key) || !sc.containsKey(key) || isZeroOrMissing(sc.get(key))) {
            sc.put(key, b);
        }
    }

    private static boolean putBankGapDefault(Map<String, BigDecimal> sc, String key, BigDecimal fallback) {
        if (!sc.containsKey(key) || isZeroOrMissing(sc.get(key))) {
            sc.put(key, fallback);
            return true;
        }
        return false;
    }

    /** Gap-fill when missing/zero, or when a stub value is below the SCF policy floor. */
    private static boolean putScfGapDefault(
            Map<String, BigDecimal> sc, String key, BigDecimal fallback, BigDecimal minAcceptable) {
        BigDecimal current = sc.get(key);
        if (current == null || isZeroOrMissing(current)
                || (minAcceptable != null && current.compareTo(minAcceptable) < 0)) {
            sc.put(key, fallback);
            return true;
        }
        return false;
    }

    private static BigDecimal incomeFromProfile(LoanApplication app) {
        if (app.getPersonalInfo() != null) {
            BigDecimal fromProfile = toBd(app.getPersonalInfo().get("monthlyNetIncome"));
            if (fromProfile != null && fromProfile.compareTo(BigDecimal.ZERO) > 0) {
                return fromProfile;
            }
        }
        if (app.getBusinessInfo() != null) {
            BigDecimal fromBusiness = toBd(app.getBusinessInfo().get("monthlyIncome"));
            if (fromBusiness != null && fromBusiness.compareTo(BigDecimal.ZERO) > 0) {
                return fromBusiness;
            }
        }
        return null;
    }

    private static boolean isZeroOrMissing(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) <= 0;
    }

    private static BigDecimal resolveObligation(Map<String, Object> fi, Map<String, Object> manual, String incomeSource) {
        if (SRC_MANUAL.equalsIgnoreCase(incomeSource) && manual.get("monthlyObligation") != null) {
            return toBd(unwrapValue(manual.get("monthlyObligation")));
        }
        if (fi.get("monthlyObligation") != null) {
            return toBd(fi.get("monthlyObligation"));
        }
        if (fi.get("obligation") != null) {
            return toBd(fi.get("obligation"));
        }
        return null;
    }

    private static Object unwrapValue(Object cell) {
        if (cell instanceof Map<?, ?> m && m.get("value") != null) {
            return m.get("value");
        }
        return cell;
    }

    private void applyApplicationScorecardParameters(LoanApplication app, Map<String, BigDecimal> sc) {
        Map<String, Object> personal = app.getPersonalInfo() != null ? app.getPersonalInfo() : Map.of();
        putIfNotNull(sc, "AGE", ApplicationScorecardParameterResolver.ageYears(personal));
        if (app.getRequestedAmount() != null) {
            sc.put("REQUESTED_AMOUNT", app.getRequestedAmount());
        }
        if (app.getTenureMonths() != null) {
            sc.put("TENURE_MONTHS", BigDecimal.valueOf(app.getTenureMonths()));
        }
    }

    private static void putIfNotNull(Map<String, BigDecimal> sc, String key, BigDecimal value) {
        if (value != null) {
            sc.put(key, value);
        }
    }

    private static String normalizeSource(String s) {
        if (s == null) {
            return SRC_PROVIDER;
        }
        String t = s.trim().toUpperCase();
        if ("PROVIDER".equals(t) || "MANUAL".equals(t) || "SYSTEM".equals(t)) {
            return t;
        }
        return SRC_PROVIDER;
    }

    private static BigDecimal toBd(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof BigDecimal b) {
            return b;
        }
        if (o instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(o.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal toYesNoOrNumberBd(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o).trim();
        if ("Y".equalsIgnoreCase(s) || "YES".equalsIgnoreCase(s)) {
            return BigDecimal.ONE;
        }
        if ("N".equalsIgnoreCase(s) || "NO".equalsIgnoreCase(s)) {
            return BigDecimal.ZERO;
        }
        return toBd(o);
    }
}
