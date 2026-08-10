package com.los.core.creditintelligence.service;

import com.los.core.creditintelligence.bureau.domain.BureauMetricOutcome;
import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.core.repository.CiMetricResultRepository;
import com.los.core.creditintelligence.bureau.service.BureauMetricService;
import com.los.core.creditintelligence.banking.domain.BankingMetricOutcome;
import com.los.core.creditintelligence.banking.service.BankingMetricService;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.evaluation.EvaluationContextHolder;
import com.los.core.creditintelligence.evaluation.PinnedMetricLookup;
import com.los.core.creditintelligence.evaluation.domain.CiEvaluationContext;
import com.los.core.creditintelligence.gst.domain.GstMetricOutcome;
import com.los.core.creditintelligence.gst.service.GstMetricService;
import com.los.core.creditintelligence.tax.domain.TaxMetricOutcome;
import com.los.core.creditintelligence.tax.service.TaxMetricService;
import com.los.core.creditintelligence.domain.CiFactSnapshot;
import com.los.core.creditintelligence.domain.CiUnderwritingFact;
import com.los.core.creditintelligence.domain.FactClassification;
import com.los.core.creditintelligence.domain.SnapshotStatus;
import com.los.core.creditintelligence.repository.CiFactSnapshotRepository;
import com.los.core.creditintelligence.repository.CiUnderwritingFactRepository;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.BorrowerType;
import com.los.core.service.credit.EffectiveUnderwritingContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Reconstructs EffectiveUnderwritingContext and a detached LoanApplication stub
 * exclusively from a frozen fact snapshot — never from live mutable application fields
 * (aside from optional identity for engine matching).
 * <p>
 * When evaluation-context / config-freeze purity flags are on, metric overlays come only from
 * the pinned metric result set on {@link CiEvaluationContext} (via {@link EvaluationContextHolder}
 * or an explicit adapt overload). Live latest-by-createdAt lookup is skipped.
 */
@Service
@RequiredArgsConstructor
public class LegacyUnderwritingContextAdapter {

    private final CiFactSnapshotRepository snapshotRepository;
    private final CiUnderwritingFactRepository factRepository;
    private final CiMetricResultRepository metricResultRepository;
    private final CreditIntelligenceProperties properties;
    private final PinnedMetricLookup pinnedMetricLookup;

    public record AdapterResult(
            EffectiveUnderwritingContext context,
            Set<String> defaultedPaths,
            Map<String, Object> metadata,
            Map<String, Object> applicationView,
            String kycOutcome) {
    }

    @Transactional(readOnly = true)
    public AdapterResult adapt(UUID snapshotId) {
        return adapt(snapshotId, EvaluationContextHolder.get());
    }

    @Transactional(readOnly = true)
    public AdapterResult adapt(UUID snapshotId, CiEvaluationContext evaluationContext) {
        CiFactSnapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new IllegalArgumentException("Snapshot not found: " + snapshotId));
        if (!SnapshotStatus.FROZEN.name().equals(snapshot.getStatus())) {
            throw new IllegalStateException("Snapshot is not FROZEN: " + snapshotId);
        }
        List<CiUnderwritingFact> facts = factRepository.findBySnapshotIdOrderByCanonicalPathAsc(snapshotId);
        return adaptFromFacts(snapshot, facts, evaluationContext);
    }

    public AdapterResult adaptFromFacts(CiFactSnapshot snapshot, List<CiUnderwritingFact> facts) {
        return adaptFromFacts(snapshot, facts, EvaluationContextHolder.get());
    }

    public AdapterResult adaptFromFacts(
            CiFactSnapshot snapshot, List<CiUnderwritingFact> facts, CiEvaluationContext evaluationContext) {
        Map<String, CiUnderwritingFact> byPath = new LinkedHashMap<>();
        Set<String> defaultedPaths = new HashSet<>();
        for (CiUnderwritingFact f : facts) {
            if (f.getCanonicalPath() != null) {
                byPath.put(f.getCanonicalPath(), f);
                if (FactClassification.DEFAULTED.name().equals(f.getClassification())) {
                    defaultedPaths.add(f.getCanonicalPath());
                }
            }
        }

        Map<String, Object> meta = snapshot.getMetadata() != null
                ? new LinkedHashMap<>(snapshot.getMetadata())
                : new LinkedHashMap<>();

        int bureauScore = intFrom(byPath, "bureau.consumer.score");
        if (bureauScore <= 0) {
            bureauScore = intFrom(byPath, "compat.BUREAU_SCORE");
        }

        boolean kycPass = boolFrom(byPath, "kyc.identity_verified");
        if (!byPath.containsKey("kyc.identity_verified")) {
            BigDecimal kycSuccess = decimalFrom(byPath, "compat.KYC_SUCCESS");
            kycPass = kycSuccess != null && kycSuccess.compareTo(BigDecimal.ZERO) > 0;
        }

        BigDecimal income = decimalFrom(byPath, "compat.MONTHLY_INCOME");
        if (income == null) {
            BigDecimal annual = decimalFrom(byPath, "applicant.verified_annual_income");
            if (annual != null) {
                income = annual.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            }
        }

        BigDecimal obligation = decimalFrom(byPath, "compat.MONTHLY_OBLIGATION");
        if (obligation == null) {
            obligation = decimalFrom(byPath, "obligations.monthly_emi_total");
        }

        String state = str(meta.get("effectiveState"));
        String city = str(meta.get("effectiveCity"));
        @SuppressWarnings("unchecked")
        Map<String, Object> appView = meta.get("applicationView") instanceof Map<?, ?> av
                ? (Map<String, Object>) av
                : Map.of();
        if ((state == null || state.isBlank()) && appView.get("personalInfo") instanceof Map<?, ?> pi) {
            state = str(pi.get("state"));
        }
        if ((city == null || city.isBlank()) && appView.get("personalInfo") instanceof Map<?, ?> pi) {
            city = str(pi.get("city"));
        }

        String bureauSource = str(meta.get("bureauSource"));
        String incomeSource = str(meta.get("incomeSource"));
        String kycSource = str(meta.get("kycSource"));
        String kycOutcome = str(meta.get("kycOutcome"));
        if (kycOutcome == null) {
            kycOutcome = "PASS";
        }

        Map<String, BigDecimal> scorecard = new LinkedHashMap<>();
        for (CiUnderwritingFact f : facts) {
            String path = f.getCanonicalPath();
            if (path == null || !path.startsWith("compat.")) {
                continue;
            }
            String key = path.substring("compat.".length());
            BigDecimal v = unwrapDecimal(f.getValue());
            if (v != null) {
                scorecard.put(key, v);
            }
        }
        // Ensure core keys present even if only on canonical paths
        if (!scorecard.containsKey("BUREAU_SCORE") && bureauScore > 0) {
            scorecard.put("BUREAU_SCORE", BigDecimal.valueOf(bureauScore));
        }
        if (!scorecard.containsKey("MONTHLY_INCOME") && income != null) {
            scorecard.put("MONTHLY_INCOME", income);
        }
        if (!scorecard.containsKey("MONTHLY_OBLIGATION") && obligation != null) {
            scorecard.put("MONTHLY_OBLIGATION", obligation);
            scorecard.putIfAbsent("EMI_OBLIGATION", obligation);
        }
        if (!scorecard.containsKey("KYC_SUCCESS")) {
            scorecard.put("KYC_SUCCESS", kycPass ? BigDecimal.ONE : BigDecimal.ZERO);
        }

        Map<String, Object> adapterFlags = new LinkedHashMap<>();
        applyCanonicalLiveUnsecuredForShadow(snapshot.getApplicationId(), scorecard, adapterFlags, evaluationContext);
        Map<String, Object> gstFlags = new LinkedHashMap<>();
        applyCanonicalGstTurnoverForShadow(snapshot.getApplicationId(), scorecard, gstFlags, evaluationContext);
        adapterFlags.put("gst", gstFlags);
        Map<String, Object> bankingFlags = new LinkedHashMap<>();
        applyCanonicalBankingForShadow(snapshot.getApplicationId(), scorecard, bankingFlags, evaluationContext);
        adapterFlags.put("banking", bankingFlags);
        Map<String, Object> taxFlags = new LinkedHashMap<>();
        applyCanonicalTaxForShadow(snapshot.getApplicationId(), scorecard, taxFlags, evaluationContext);
        adapterFlags.put("tax", taxFlags);
        if (isPurityMode()) {
            adapterFlags.put("purityMode", true);
            adapterFlags.put("pinnedMetricSetOnly", evaluationContext != null
                    && evaluationContext.getMetricResultSetId() != null);
        }
        meta.put("adapterFlags", adapterFlags);

        EffectiveUnderwritingContext ctx = new EffectiveUnderwritingContext(
                bureauScore,
                kycPass,
                income,
                obligation,
                state,
                city,
                bureauSource != null ? bureauSource : "PROVIDER",
                incomeSource != null ? incomeSource : "PROVIDER",
                kycSource != null ? kycSource : "PROVIDER",
                scorecard);

        return new AdapterResult(ctx, defaultedPaths, meta, appView, kycOutcome);
    }

    /**
     * When useForShadowRules: overlay canonical live_unsecured when available.
     * When DATA_INSUFFICIENT: keep legacy compat value but flag fallback.
     * Purity mode: only pinned metric set (no live latest lookup).
     */
    private void applyCanonicalLiveUnsecuredForShadow(
            UUID applicationId,
            Map<String, BigDecimal> scorecard,
            Map<String, Object> adapterFlags,
            CiEvaluationContext evaluationContext) {
        if (!properties.getCanonicalization().getBureau().isUseForShadowRules()) {
            adapterFlags.put("useForShadowRules", false);
            return;
        }
        adapterFlags.put("useForShadowRules", true);
        Optional<CiMetricResult> metricOpt = findMetric(applicationId, BureauMetricService.LIVE_UNSECURED, evaluationContext);
        if (metricOpt.isEmpty()) {
            adapterFlags.put("canonicalSourceAvailable", false);
            return;
        }
        CiMetricResult metric = metricOpt.get();
        adapterFlags.put("canonicalSourceAvailable", true);
        adapterFlags.put("canonicalMetricVersion", metric.getMetricVersion());
        adapterFlags.put("canonicalOutcome", metric.getOutcome());
        if (BureauMetricOutcome.DATA_INSUFFICIENT.name().equals(metric.getOutcome())) {
            adapterFlags.put("canonicalDataInsufficient", true);
            adapterFlags.put("fallbackUsed", true);
            return;
        }
        Object raw = metric.getValue() != null ? metric.getValue().get("v") : null;
        if (raw == null) {
            adapterFlags.put("canonicalDataInsufficient", true);
            adapterFlags.put("fallbackUsed", true);
            return;
        }
        try {
            BigDecimal canonical = new BigDecimal(String.valueOf(raw));
            scorecard.put("LIVE_UNSECURED_LOAN_COUNT", canonical);
            adapterFlags.put("canonicalDataInsufficient", false);
            adapterFlags.put("fallbackUsed", false);
            adapterFlags.put("canonicalValueApplied", true);
        } catch (Exception e) {
            adapterFlags.put("canonicalDataInsufficient", true);
            adapterFlags.put("fallbackUsed", true);
        }
    }

    /**
     * When useForShadowRules and canonical trailing_12m PASS: overlay ANNUAL_GST_TURNOVER
     * for shadow path only. Production still uses CreditControl (incl. SCF 52M gap default).
     * When DATA_INSUFFICIENT: keep legacy and flag fallback.
     */
    private void applyCanonicalGstTurnoverForShadow(
            UUID applicationId,
            Map<String, BigDecimal> scorecard,
            Map<String, Object> gstFlags,
            CiEvaluationContext evaluationContext) {
        if (!properties.getCanonicalization().getGst().isUseForShadowRules()) {
            gstFlags.put("useForShadowRules", false);
            gstFlags.put("note", "Production ANNUAL_GST_TURNOVER still from CreditControl");
            return;
        }
        gstFlags.put("useForShadowRules", true);
        gstFlags.put("note", "Shadow overlay only — production CreditControl unchanged");
        Optional<CiMetricResult> metricOpt = findMetric(applicationId, GstMetricService.TRAILING_12M, evaluationContext);
        if (metricOpt.isEmpty()) {
            gstFlags.put("canonicalSourceAvailable", false);
            gstFlags.put("fallbackUsed", true);
            return;
        }
        CiMetricResult metric = metricOpt.get();
        gstFlags.put("canonicalSourceAvailable", true);
        gstFlags.put("canonicalMetricVersion", metric.getMetricVersion());
        gstFlags.put("canonicalOutcome", metric.getOutcome());
        if (GstMetricOutcome.DATA_INSUFFICIENT.name().equals(metric.getOutcome())
                || !GstMetricOutcome.PASS.name().equals(metric.getOutcome())) {
            gstFlags.put("canonicalDataInsufficient", true);
            gstFlags.put("fallbackUsed", true);
            return;
        }
        Object raw = metric.getValue() != null ? metric.getValue().get("v") : null;
        if (raw == null) {
            gstFlags.put("canonicalDataInsufficient", true);
            gstFlags.put("fallbackUsed", true);
            return;
        }
        try {
            BigDecimal canonical = new BigDecimal(String.valueOf(raw));
            scorecard.put("ANNUAL_GST_TURNOVER", canonical);
            gstFlags.put("canonicalDataInsufficient", false);
            gstFlags.put("fallbackUsed", false);
            gstFlags.put("canonicalValueApplied", true);
        } catch (Exception e) {
            gstFlags.put("canonicalDataInsufficient", true);
            gstFlags.put("fallbackUsed", true);
        }
    }

    /**
     * When useForShadowRules: overlay AVERAGE_BANK_BALANCE from avg_daily_balance_3m if PASS;
     * EMI_OBLIGATION from banking.monthly_obligation if PASS;
     * ANNUAL_BANKING_TURNOVER from adjusted_business_credits_12m if PASS.
     * When DI: keep legacy and flag fallback.
     */
    private void applyCanonicalBankingForShadow(
            UUID applicationId,
            Map<String, BigDecimal> scorecard,
            Map<String, Object> bankingFlags,
            CiEvaluationContext evaluationContext) {
        if (!properties.getCanonicalization().getBanking().isUseForShadowRules()) {
            bankingFlags.put("useForShadowRules", false);
            bankingFlags.put("note", "Production banking scorecard keys still from CreditControl");
            return;
        }
        bankingFlags.put("useForShadowRules", true);
        bankingFlags.put("note", "Shadow overlay only — production CreditControl unchanged");
        overlayBankingMetric(applicationId, scorecard, bankingFlags,
                BankingMetricService.ADB_3M, "AVERAGE_BANK_BALANCE", "avgDailyBalance3m", evaluationContext);
        overlayBankingMetric(applicationId, scorecard, bankingFlags,
                BankingMetricService.MONTHLY_OBL, "EMI_OBLIGATION", "monthlyObligation", evaluationContext);
        // Also mirror MONTHLY_OBLIGATION when EMI overlay applied
        if (Boolean.TRUE.equals(bankingFlags.get("EMI_OBLIGATION_applied"))) {
            BigDecimal emi = scorecard.get("EMI_OBLIGATION");
            if (emi != null) {
                scorecard.put("MONTHLY_OBLIGATION", emi);
            }
        }
        overlayBankingMetric(applicationId, scorecard, bankingFlags,
                BankingMetricService.ADJ_12M, "ANNUAL_BANKING_TURNOVER", "adjustedCredits12m", evaluationContext);
    }

    /**
     * When useForShadowRules: overlay ITR_INCOME from turnover/total_income, PAT / TOL / TNW from
     * absolute business metrics if PASS. DI keeps legacy + flag.
     */
    private void applyCanonicalTaxForShadow(
            UUID applicationId,
            Map<String, BigDecimal> scorecard,
            Map<String, Object> taxFlags,
            CiEvaluationContext evaluationContext) {
        if (!properties.getCanonicalization().getTax().isUseForShadowRules()) {
            taxFlags.put("useForShadowRules", false);
            taxFlags.put("note", "Production ITR_INCOME / TNW / TOL still from CreditControl");
            return;
        }
        taxFlags.put("useForShadowRules", true);
        taxFlags.put("note", "Shadow overlay only — production CreditControl / SCF_GAP_ITR_INCOME unchanged");

        Optional<CiMetricResult> turnover = findMetric(applicationId, TaxMetricService.TURNOVER_LATEST, evaluationContext);
        Optional<CiMetricResult> totalIncome = findMetric(applicationId, TaxMetricService.TOTAL_INCOME_LATEST, evaluationContext);
        overlayTaxAbsolute(scorecard, taxFlags, "ITR_INCOME", "ITR_INCOME",
                preferPass(turnover, totalIncome));

        overlayTaxAbsolute(scorecard, taxFlags, "PAT", "pat",
                findMetric(applicationId, TaxMetricService.PAT_ABS, evaluationContext));
        overlayTaxAbsolute(scorecard, taxFlags, "TOL", "tol",
                findMetric(applicationId, TaxMetricService.TOL_ABS, evaluationContext));
        overlayTaxAbsolute(scorecard, taxFlags, "TNW", "tnw",
                findMetric(applicationId, TaxMetricService.TNW_ABS, evaluationContext));
    }

    /**
     * Purity mode: resolve via pinned set only (skip live latest).
     * Flag-off: transitional live latest-by-createdAt.
     */
    private Optional<CiMetricResult> findMetric(
            UUID applicationId, String metricCode, CiEvaluationContext evaluationContext) {
        if (isPurityMode()) {
            if (evaluationContext == null || evaluationContext.getMetricResultSetId() == null) {
                return Optional.empty();
            }
            return pinnedMetricLookup.find(applicationId, metricCode, evaluationContext);
        }
        return metricResultRepository
                .findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(applicationId, metricCode);
    }

    private boolean isPurityMode() {
        return properties.getEvaluationContext().isEnabled()
                || properties.getConfigFreeze().isEnabled();
    }

    private static Optional<CiMetricResult> preferPass(
            Optional<CiMetricResult> primary, Optional<CiMetricResult> fallback) {
        if (primary.isPresent() && TaxMetricOutcome.PASS.name().equals(primary.get().getOutcome())
                && primary.get().getValue() != null && primary.get().getValue().get("v") != null) {
            return primary;
        }
        if (fallback.isPresent() && TaxMetricOutcome.PASS.name().equals(fallback.get().getOutcome())
                && fallback.get().getValue() != null && fallback.get().getValue().get("v") != null) {
            return fallback;
        }
        return primary.isPresent() ? primary : fallback;
    }

    private void overlayTaxAbsolute(
            Map<String, BigDecimal> scorecard,
            Map<String, Object> taxFlags,
            String scorecardKey,
            String flagPrefix,
            Optional<CiMetricResult> metricOpt) {
        Map<String, Object> detail = new LinkedHashMap<>();
        if (metricOpt == null || metricOpt.isEmpty()) {
            detail.put("canonicalSourceAvailable", false);
            detail.put("fallbackUsed", true);
            taxFlags.put(flagPrefix, detail);
            return;
        }
        CiMetricResult metric = metricOpt.get();
        detail.put("canonicalSourceAvailable", true);
        detail.put("canonicalOutcome", metric.getOutcome());
        if (!TaxMetricOutcome.PASS.name().equals(metric.getOutcome())) {
            detail.put("canonicalDataInsufficient",
                    TaxMetricOutcome.DATA_INSUFFICIENT.name().equals(metric.getOutcome())
                            || TaxMetricOutcome.NOT_APPLICABLE.name().equals(metric.getOutcome()));
            detail.put("fallbackUsed", true);
            taxFlags.put(flagPrefix, detail);
            return;
        }
        Object raw = metric.getValue() != null ? metric.getValue().get("v") : null;
        if (raw == null) {
            detail.put("canonicalDataInsufficient", true);
            detail.put("fallbackUsed", true);
            taxFlags.put(flagPrefix, detail);
            return;
        }
        try {
            BigDecimal canonical = new BigDecimal(String.valueOf(raw));
            scorecard.put(scorecardKey, canonical);
            detail.put("canonicalValueApplied", true);
            detail.put("fallbackUsed", false);
            taxFlags.put(flagPrefix, detail);
            taxFlags.put(scorecardKey + "_applied", true);
        } catch (Exception e) {
            detail.put("fallbackUsed", true);
            taxFlags.put(flagPrefix, detail);
        }
    }

    private void overlayBankingMetric(
            UUID applicationId,
            Map<String, BigDecimal> scorecard,
            Map<String, Object> bankingFlags,
            String metricCode,
            String scorecardKey,
            String flagPrefix,
            CiEvaluationContext evaluationContext) {
        Optional<CiMetricResult> metricOpt = findMetric(applicationId, metricCode, evaluationContext);
        Map<String, Object> detail = new LinkedHashMap<>();
        if (metricOpt.isEmpty()) {
            detail.put("canonicalSourceAvailable", false);
            detail.put("fallbackUsed", true);
            bankingFlags.put(flagPrefix, detail);
            return;
        }
        CiMetricResult metric = metricOpt.get();
        detail.put("canonicalSourceAvailable", true);
        detail.put("canonicalOutcome", metric.getOutcome());
        if (!BankingMetricOutcome.PASS.name().equals(metric.getOutcome())) {
            detail.put("canonicalDataInsufficient", true);
            detail.put("fallbackUsed", true);
            bankingFlags.put(flagPrefix, detail);
            bankingFlags.put(scorecardKey + "_diFallback", true);
            return;
        }
        Object raw = metric.getValue() != null ? metric.getValue().get("v") : null;
        if (raw == null) {
            detail.put("canonicalDataInsufficient", true);
            detail.put("fallbackUsed", true);
            bankingFlags.put(flagPrefix, detail);
            return;
        }
        try {
            BigDecimal canonical = new BigDecimal(String.valueOf(raw));
            scorecard.put(scorecardKey, canonical);
            detail.put("canonicalValueApplied", true);
            detail.put("fallbackUsed", false);
            bankingFlags.put(flagPrefix, detail);
            bankingFlags.put(scorecardKey + "_applied", true);
        } catch (Exception e) {
            detail.put("fallbackUsed", true);
            bankingFlags.put(flagPrefix, detail);
        }
    }

    /**
     * Builds an unsaved LoanApplication populated ONLY from frozen applicationView + identity.
     * NEVER persist this stub.
     */
    public LoanApplication toLoanApplicationStub(UUID applicationId, AdapterResult adapted) {
        Map<String, Object> view = adapted.applicationView() != null
                ? adapted.applicationView()
                : Map.of();
        LoanApplication stub = new LoanApplication();
        stub.setId(applicationId);
        stub.setApplicationNumber(str(view.get("applicationNumber")));
        stub.setRequestedAmount(toBd(view.get("requestedAmount")));
        if (view.get("tenureMonths") != null) {
            try {
                stub.setTenureMonths(Integer.valueOf(String.valueOf(view.get("tenureMonths"))));
            } catch (Exception ignored) {
                /* leave null */
            }
        }
        String bt = str(view.get("borrowerType"));
        if (bt != null) {
            try {
                stub.setBorrowerType(BorrowerType.valueOf(bt));
            } catch (Exception ignored) {
                /* leave null */
            }
        }
        stub.setLoanProduct(str(view.get("loanProduct")));
        Map<String, Object> personal = new HashMap<>();
        if (view.get("personalInfo") instanceof Map<?, ?> pi) {
            for (Map.Entry<?, ?> e : pi.entrySet()) {
                if (e.getKey() != null) {
                    personal.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
        }
        if (adapted.context().effectiveState() != null) {
            personal.putIfAbsent("state", adapted.context().effectiveState());
        }
        if (adapted.context().effectiveCity() != null) {
            personal.putIfAbsent("city", adapted.context().effectiveCity());
        }
        stub.setPersonalInfo(personal);
        stub.setFinancialInfo(Map.of());
        return stub;
    }

    private static int intFrom(Map<String, CiUnderwritingFact> byPath, String path) {
        BigDecimal d = decimalFrom(byPath, path);
        return d != null ? d.intValue() : 0;
    }

    private static boolean boolFrom(Map<String, CiUnderwritingFact> byPath, String path) {
        CiUnderwritingFact f = byPath.get(path);
        if (f == null) {
            return false;
        }
        Object raw = unwrapRaw(f.getValue());
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw == null) {
            return false;
        }
        String s = String.valueOf(raw);
        return "true".equalsIgnoreCase(s) || "1".equals(s);
    }

    private static BigDecimal decimalFrom(Map<String, CiUnderwritingFact> byPath, String path) {
        CiUnderwritingFact f = byPath.get(path);
        if (f == null) {
            return null;
        }
        return unwrapDecimal(f.getValue());
    }

    @SuppressWarnings("unchecked")
    static Object unwrapRaw(Object value) {
        if (value instanceof Map<?, ?> m) {
            if (m.containsKey("v")) {
                return m.get("v");
            }
        }
        return value;
    }

    static BigDecimal unwrapDecimal(Object value) {
        Object raw = unwrapRaw(value);
        if (raw == null) {
            return null;
        }
        if (raw instanceof BigDecimal bd) {
            return bd;
        }
        if (raw instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(raw));
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal toBd(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
