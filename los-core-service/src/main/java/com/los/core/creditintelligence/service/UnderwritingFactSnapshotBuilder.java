package com.los.core.creditintelligence.service;

import com.los.core.creditintelligence.bureau.domain.BureauMetricOutcome;
import com.los.core.creditintelligence.bureau.domain.CiBureauReport;
import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.bureau.repository.CiBureauReportRepository;
import com.los.core.creditintelligence.core.repository.CiMetricResultRepository;
import com.los.core.creditintelligence.bureau.service.BureauMetricService;
import com.los.core.creditintelligence.banking.domain.BankingMetricOutcome;
import com.los.core.creditintelligence.banking.domain.CiBankAccount;
import com.los.core.creditintelligence.banking.repository.CiBankAccountRepository;
import com.los.core.creditintelligence.banking.service.BankingIngestionService;
import com.los.core.creditintelligence.banking.service.BankingMetricService;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.gst.domain.CiGstRegistration;
import com.los.core.creditintelligence.gst.domain.GstMetricOutcome;
import com.los.core.creditintelligence.gst.repository.CiGstRegistrationRepository;
import com.los.core.creditintelligence.gst.service.GstIngestionService;
import com.los.core.creditintelligence.gst.service.GstMetricService;
import com.los.core.creditintelligence.tax.domain.CiItrReturn;
import com.los.core.creditintelligence.tax.domain.TaxMetricOutcome;
import com.los.core.creditintelligence.tax.repository.CiAisSummaryRepository;
import com.los.core.creditintelligence.tax.repository.CiForm26AsSummaryRepository;
import com.los.core.creditintelligence.tax.repository.CiItrReturnRepository;
import com.los.core.creditintelligence.tax.service.TaxIngestionService;
import com.los.core.creditintelligence.tax.service.TaxMetricService;
import com.los.core.creditintelligence.reconciliation.domain.CiCreditEvidenceSummary;
import com.los.core.creditintelligence.reconciliation.domain.CiReconciliationResult;
import com.los.core.creditintelligence.reconciliation.domain.ReconciliationConstants;
import com.los.core.creditintelligence.reconciliation.service.ReconciliationIngestionService;
import com.los.core.creditintelligence.reconciliation.service.ReconciliationOrchestrator;
import com.los.core.creditintelligence.domain.CiFactSnapshot;
import com.los.core.creditintelligence.domain.CiSourceRecord;
import com.los.core.creditintelligence.domain.CiUnderwritingFact;
import com.los.core.creditintelligence.domain.FactClassification;
import com.los.core.creditintelligence.domain.SnapshotStatus;
import com.los.core.creditintelligence.domain.SourceType;
import com.los.core.creditintelligence.repository.CiFactSnapshotRepository;
import com.los.core.creditintelligence.repository.CiUnderwritingFactRepository;
import com.los.core.creditintelligence.support.ContentHasher;
import com.los.core.model.entity.LoanApplication;
import com.los.core.service.audit.AuditService;
import com.los.core.service.credit.CreditControlKeys;
import com.los.core.service.credit.EffectiveUnderwritingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Builds and freezes an underwriting fact snapshot from the current effective context.
 * Does not mutate LoanApplication or change production underwriting behaviour.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnderwritingFactSnapshotBuilder {

    private static final Set<String> GAP_DEFAULTED_KEYS = Set.of(
            "LIVE_UNSECURED_LOAN_COUNT",
            "BUREAU_ENQUIRIES_3M",
            "MONTHLY_INCOME",
            "AVERAGE_BANK_BALANCE",
            "EMI_OBLIGATION",
            "MONTHLY_OBLIGATION",
            "OBLIGATION_RATIO",
            "avgDailyBalance3m",
            "avgMonthlyTransactions3m",
            "avgMonthlySettlements3m",
            "monthlyTransactions3m",
            "inwardChequeReturns3m",
            "avgDailySettlements3m",
            "noOfTxns60days",
            "txnMth1",
            "txnMth2",
            "txnMth3",
            "ANNUAL_GST_TURNOVER",
            "ANNUAL_BANKING_TURNOVER",
            "ITR_INCOME",
            "PAT",
            "INTEREST_COVERAGE",
            "DEBT_TO_EQUITY",
            "EBITDA",
            "DEBT_SERVICE",
            "NTC_FLAG",
            "BANKING_TURNOVER_PCT_GST",
            "ABB_OBLIGATION_MULTIPLE",
            "CC_UTILISATION_PCT",
            "CHEQUE_BOUNCES_12M",
            "CHEQUE_BOUNCES_3M",
            "EXISTING_FB_LIMITS",
            "EXISTING_NFB_LIMITS",
            "officeOwned",
            "residenceOwned",
            "businessStability",
            "DSCR",
            "TOL_TNW",
            "TOL",
            "TNW"
    );

    private static final Set<String> DERIVED_KEYS = Set.of(
            "OBLIGATION_RATIO",
            "FOIR",
            "LTV",
            "SCF_ELIGIBLE_AMOUNT",
            "ELIGIBLE_AMOUNT",
            "SANCTION_CAP"
    );

    private final CiFactSnapshotRepository snapshotRepository;
    private final CiUnderwritingFactRepository factRepository;
    private final SourceRegistryService sourceRegistryService;
    private final ContentHasher contentHasher;
    private final CreditIntelligenceProperties properties;
    private final AuditService auditService;
    private final CiBureauReportRepository bureauReportRepository;
    private final CiMetricResultRepository metricResultRepository;
    private final CiGstRegistrationRepository gstRegistrationRepository;
    private final GstIngestionService gstIngestionService;
    private final CiBankAccountRepository bankAccountRepository;
    private final BankingIngestionService bankingIngestionService;
    private final CiItrReturnRepository itrReturnRepository;
    private final CiAisSummaryRepository aisSummaryRepository;
    private final CiForm26AsSummaryRepository form26AsSummaryRepository;
    private final TaxIngestionService taxIngestionService;
    private final ReconciliationIngestionService reconciliationIngestionService;

    public record FoundationPrep(CiFactSnapshot snapshot, List<CiUnderwritingFact> facts) {
    }

    @Transactional
    public FoundationPrep buildAndFreeze(
            LoanApplication app,
            EffectiveUnderwritingContext ctx,
            String kycOutcome,
            String createdBy) {
        UUID tenantId = properties.getDefaultTenantId();
        UUID applicationId = app.getId();

        int nextVersion = 1;
        UUID previousId = null;
        var locked = snapshotRepository.findForUpdateMaxVersion(applicationId);
        if (locked.isPresent()) {
            nextVersion = locked.get().getSnapshotVersion() + 1;
            previousId = locked.get().getId();
        }

        Map<String, Object> applicationView = buildApplicationView(app);
        Map<String, Object> snapshotMeta = new LinkedHashMap<>();
        snapshotMeta.put("applicationView", applicationView);
        snapshotMeta.put("effectiveContextMap", ctx.toMap());
        snapshotMeta.put("kycOutcome", kycOutcome);
        snapshotMeta.put("bureauSource", ctx.bureauSource());
        snapshotMeta.put("incomeSource", ctx.incomeSource());
        snapshotMeta.put("kycSource", ctx.kycSource());
        snapshotMeta.put("effectiveState", ctx.effectiveState());
        snapshotMeta.put("effectiveCity", ctx.effectiveCity());

        CiFactSnapshot snapshot = CiFactSnapshot.builder()
                .tenantId(tenantId)
                .applicationId(applicationId)
                .snapshotVersion(nextVersion)
                .status(SnapshotStatus.BUILDING.name())
                .createdReason("UNDERWRITE")
                .previousSnapshotId(previousId)
                .createdBy(createdBy)
                .schemaVersion("F1")
                .metadata(snapshotMeta)
                .build();
        snapshot = snapshotRepository.save(snapshot);

        Map<String, UUID> sourceIds = createSourceRecords(tenantId, applicationId, ctx, createdBy);

        // Ingestion is intentionally NOT invoked here — snapshot build is side-effect free.
        // Call Gst/Banking/Tax ensureIngested from foundation prepare / ingestion hooks only.

        List<PendingFact> pending = new ArrayList<>();
        CanonicalBureauFacts canonicalBureau = loadCanonicalBureauFacts(applicationId);
        CanonicalGstFacts canonicalGst = loadCanonicalGstFacts(applicationId);
        CanonicalBankingFacts canonicalBanking = loadCanonicalBankingFacts(applicationId);
        CanonicalTaxFacts canonicalTax = loadCanonicalTaxFacts(applicationId);
        emitCanonicalFacts(app, ctx, kycOutcome, sourceIds, pending, canonicalBureau, canonicalGst);
        emitCanonicalBankingFacts(pending, sourceIds, canonicalBanking);
        emitCanonicalTaxFacts(pending, sourceIds, canonicalTax);
        emitCompatFacts(ctx, sourceIds, pending, canonicalBureau, canonicalGst, canonicalTax);
        emitBankingCompatMetadata(pending, canonicalBanking);
        emitTaxCompatMetadata(pending, canonicalTax);

        // Optional C5 reconciliation facts (flag-gated; safe when metrics missing)
        try {
            emitReconciliationFacts(app, snapshot.getId(), pending, sourceIds);
        } catch (Exception e) {
            log.warn("Reconciliation facts skipped for {}: {}", applicationId, e.getMessage());
        }

        List<CiUnderwritingFact> saved = new ArrayList<>();
        List<ContentHasher.FactHashInput> hashInputs = new ArrayList<>();
        for (PendingFact p : pending) {
            CiUnderwritingFact fact = CiUnderwritingFact.builder()
                    .tenantId(tenantId)
                    .snapshotId(snapshot.getId())
                    .canonicalPath(p.path())
                    .valueType(p.valueType())
                    .value(wrapValue(p.value()))
                    .classification(p.classification())
                    .qualityStatus("OK")
                    .sourceRecordIds(p.sourceRecordIds())
                    .normalizerVersion("F1")
                    .asOf(Instant.now())
                    .metadata(p.metadata())
                    .build();
            saved.add(factRepository.save(fact));
            hashInputs.add(new ContentHasher.FactHashInput(p.path(), wrapValue(p.value()), p.classification()));
        }

        String factsHash = contentHasher.hashFacts(hashInputs);
        Map<String, Object> sourceContext = new LinkedHashMap<>();
        sourceContext.put("bureauSource", ctx.bureauSource());
        sourceContext.put("incomeSource", ctx.incomeSource());
        sourceContext.put("kycSource", ctx.kycSource());
        sourceContext.put("sourceRecordIds", sourceIds);
        String sourceContextHash = contentHasher.hashMap(sourceContext);

        snapshot.setStatus(SnapshotStatus.FROZEN.name());
        snapshot.setFactsHash(factsHash);
        snapshot.setSourceContextHash(sourceContextHash);
        snapshot = snapshotRepository.save(snapshot);

        auditService.logEvent(
                applicationId,
                "CREDIT_INTELLIGENCE",
                "fact.snapshot.created",
                null,
                null,
                Map.of(
                        "snapshotId", snapshot.getId().toString(),
                        "snapshotVersion", snapshot.getSnapshotVersion(),
                        "factsHash", factsHash,
                        "factCount", saved.size()),
                "Fact snapshot frozen for credit intelligence");

        return new FoundationPrep(snapshot, saved);
    }

    /**
     * Rejects fact inserts against a frozen snapshot (immutability guard).
     */
    @Transactional
    public CiUnderwritingFact addFact(UUID snapshotId, CiUnderwritingFact fact) {
        CiFactSnapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new IllegalArgumentException("Snapshot not found: " + snapshotId));
        if (SnapshotStatus.FROZEN.name().equals(snapshot.getStatus())) {
            throw new IllegalStateException("Cannot add facts to FROZEN snapshot: " + snapshotId);
        }
        if (SnapshotStatus.INVALID.name().equals(snapshot.getStatus())) {
            throw new IllegalStateException("Cannot add facts to INVALID snapshot: " + snapshotId);
        }
        fact.setSnapshotId(snapshotId);
        if (fact.getTenantId() == null) {
            fact.setTenantId(snapshot.getTenantId());
        }
        return factRepository.save(fact);
    }

    private Map<String, UUID> createSourceRecords(
            UUID tenantId, UUID applicationId, EffectiveUnderwritingContext ctx, String createdBy) {
        Map<String, UUID> ids = new LinkedHashMap<>();

        CiSourceRecord legacy = sourceRegistryService.createOrGet(
                tenantId, applicationId, SourceType.LEGACY_CONTEXT.name(), "CreditControlService",
                "UNDERWRITING", "legacy-ctx-" + applicationId,
                Map.of("originService", "CreditControlService", "originTable", "loan_applications.financial_info"),
                createdBy);
        ids.put("LEGACY_CONTEXT", legacy.getId());

        CiSourceRecord application = sourceRegistryService.createOrGet(
                tenantId, applicationId, SourceType.APPLICATION.name(), "LoanApplication",
                "UNDERWRITING", "application-" + applicationId,
                Map.of("originService", "LoanApplication", "originTable", "loan_applications"),
                createdBy);
        ids.put("APPLICATION", application.getId());

        String bureauSource = ctx.bureauSource() != null ? ctx.bureauSource() : "PROVIDER";
        String bureauType = bureauSource.toUpperCase(Locale.ROOT).contains("MANUAL")
                ? SourceType.MANUAL_DECLARATION.name()
                : SourceType.CONSUMER_BUREAU.name();
        CiSourceRecord bureau = sourceRegistryService.createOrGet(
                tenantId, applicationId, bureauType, bureauSource,
                "UNDERWRITING", "bureau-" + applicationId + "-" + bureauSource,
                Map.of("originService", "CreditControlService", "originField", "bureauScoreSource",
                        "referenceOnly", true),
                createdBy);
        ids.put("BUREAU", bureau.getId());

        String incomeSource = ctx.incomeSource() != null ? ctx.incomeSource() : "PROVIDER";
        String incomeType = incomeSource.toUpperCase(Locale.ROOT).contains("MANUAL")
                || "DEMO_FALLBACK".equalsIgnoreCase(incomeSource)
                ? SourceType.MANUAL_DECLARATION.name()
                : SourceType.BANK_STATEMENT.name();
        CiSourceRecord income = sourceRegistryService.createOrGet(
                tenantId, applicationId, incomeType, incomeSource,
                "UNDERWRITING", "income-" + applicationId + "-" + incomeSource,
                Map.of("originService", "CreditControlService", "originField", "incomeSource",
                        "referenceOnly", true),
                createdBy);
        ids.put("INCOME", income.getId());

        String kycSource = ctx.kycSource() != null ? ctx.kycSource() : "PROVIDER";
        CiSourceRecord kyc = sourceRegistryService.createOrGet(
                tenantId, applicationId, SourceType.KYC.name(), kycSource,
                "UNDERWRITING", "kyc-" + applicationId + "-" + kycSource,
                Map.of("originService", "KycOrchestrationService", "originField", "kycSource",
                        "referenceOnly", true),
                createdBy);
        ids.put("KYC", kyc.getId());

        CiSourceRecord gst = sourceRegistryService.createOrGet(
                tenantId, applicationId, SourceType.GST.name(), "KARZA",
                "UNDERWRITING", "gst-" + applicationId,
                Map.of("originService", "GstAnalysisService", "referenceOnly", true),
                createdBy);
        ids.put("GST", gst.getId());

        CiSourceRecord aa = sourceRegistryService.createOrGet(
                tenantId, applicationId, SourceType.ACCOUNT_AGGREGATOR.name(), "AA",
                "UNDERWRITING", "aa-" + applicationId,
                Map.of("originService", "AccountAggregatorService", "referenceOnly", true),
                createdBy);
        ids.put("ACCOUNT_AGGREGATOR", aa.getId());

        CiSourceRecord itr = sourceRegistryService.createOrGet(
                tenantId, applicationId, SourceType.ITR.name(), "KARZA",
                "UNDERWRITING", "itr-" + applicationId,
                Map.of("originService", "ItrReturnFormsService", "referenceOnly", true),
                createdBy);
        ids.put("ITR", itr.getId());

        return ids;
    }

    private void emitCanonicalFacts(
            LoanApplication app,
            EffectiveUnderwritingContext ctx,
            String kycOutcome,
            Map<String, UUID> sourceIds,
            List<PendingFact> pending,
            CanonicalBureauFacts canonicalBureau,
            CanonicalGstFacts canonicalGst) {
        Map<String, BigDecimal> sc = ctx.scorecard() != null ? ctx.scorecard() : Map.of();
        boolean gapActive = sc.containsKey("PROVIDER_GAP_DEFAULT_ACTIVE") || sc.containsKey("DEMO_FALLBACK_ACTIVE");
        boolean demoActive = sc.containsKey("DEMO_FALLBACK_ACTIVE");
        Set<String> manualKeys = extractManualKeys(app);

        if (app.getRequestedAmount() != null) {
            pending.add(fact("application.requested_amount", "DECIMAL", app.getRequestedAmount(),
                    FactClassification.DECLARED.name(),
                    List.of(sourceIds.get("APPLICATION")),
                    meta("LoanApplication", "requestedAmount", false, false)));
        }
        if (app.getTenureMonths() != null) {
            pending.add(fact("application.requested_tenure_months", "INTEGER", app.getTenureMonths(),
                    FactClassification.DECLARED.name(),
                    List.of(sourceIds.get("APPLICATION")),
                    meta("LoanApplication", "tenureMonths", false, false)));
        }
        Object purpose = app.getPersonalInfo() != null ? app.getPersonalInfo().get("loanPurpose") : null;
        if (purpose == null && app.getPersonalInfo() != null) {
            purpose = app.getPersonalInfo().get("purpose");
        }
        if (purpose != null) {
            pending.add(fact("application.loan_purpose", "STRING", String.valueOf(purpose),
                    FactClassification.DECLARED.name(),
                    List.of(sourceIds.get("APPLICATION")),
                    meta("LoanApplication", "personalInfo.loanPurpose", false, false)));
        }

        if (sc.get("BUSINESS_VINTAGE_MONTHS") != null) {
            pending.add(fact("applicant.business_vintage_months", "INTEGER", sc.get("BUSINESS_VINTAGE_MONTHS"),
                    classifyScorecardKey("BUSINESS_VINTAGE_MONTHS", ctx, gapActive, demoActive, manualKeys),
                    List.of(sourceIds.get("LEGACY_CONTEXT")),
                    meta("CreditControlService", "BUSINESS_VINTAGE_MONTHS",
                            isDefaulted("BUSINESS_VINTAGE_MONTHS", ctx, gapActive, demoActive, manualKeys),
                            false)));
        }

        BigDecimal declaredAnnual = extractDeclaredAnnualIncome(app);
        if (declaredAnnual != null) {
            pending.add(fact("applicant.declared_annual_income", "DECIMAL", declaredAnnual,
                    FactClassification.DECLARED.name(),
                    List.of(sourceIds.get("APPLICATION")),
                    meta("LoanApplication", "financialInfo.annualIncome", false, false)));
        }
        if (ctx.effectiveIncome() != null) {
            BigDecimal verifiedAnnual = ctx.effectiveIncome().multiply(BigDecimal.valueOf(12));
            boolean incomeDefaulted = isIncomeDefaulted(ctx);
            pending.add(fact("applicant.verified_annual_income", "DECIMAL", verifiedAnnual,
                    incomeDefaulted ? FactClassification.DEFAULTED.name()
                            : classifySource(ctx.incomeSource()),
                    List.of(sourceIds.get("INCOME")),
                    meta("CreditControlService", "effectiveIncome", incomeDefaulted,
                            incomeDefaulted)));
        }

        boolean kycDefaulted = "DEMO_FALLBACK".equalsIgnoreCase(ctx.kycSource())
                || "PROVIDER_GAP".equalsIgnoreCase(ctx.kycSource());
        String kycClass = kycDefaulted ? FactClassification.DEFAULTED.name()
                : classifySource(ctx.kycSource());
        pending.add(fact("kyc.pan_verified", "BOOLEAN", ctx.kycPassEffective(),
                kycClass, List.of(sourceIds.get("KYC")),
                meta("CreditControlService", "kycPassEffective", kycDefaulted, false)));
        pending.add(fact("kyc.identity_verified", "BOOLEAN", ctx.kycPassEffective(),
                kycClass, List.of(sourceIds.get("KYC")),
                meta("CreditControlService", "kycPassEffective", kycDefaulted, false)));

        boolean gstAvailable = false;
        if (sc.containsKey("GST_AVAILABLE")) {
            gstAvailable = sc.get("GST_AVAILABLE").compareTo(BigDecimal.ZERO) > 0;
        } else if (sc.containsKey("gst") || sc.containsKey("GST")) {
            BigDecimal g = sc.getOrDefault("GST_AVAILABLE", sc.getOrDefault("gst", sc.get("GST")));
            gstAvailable = g != null && g.compareTo(BigDecimal.ZERO) > 0;
        }
        if (sc.containsKey("GST_AVAILABLE") || sc.containsKey("ANNUAL_GST_TURNOVER")) {
            pending.add(fact("kyc.gst_available", "BOOLEAN",
                    sc.containsKey("ANNUAL_GST_TURNOVER") || gstAvailable,
                    classifyScorecardKey("GST_AVAILABLE", ctx, gapActive, demoActive, manualKeys),
                    List.of(sourceIds.get("KYC")),
                    meta("CreditControlService", "GST_AVAILABLE",
                            isDefaulted("GST_AVAILABLE", ctx, gapActive, demoActive, manualKeys), false)));
        }

        boolean bureauDefaulted = isBureauDefaulted(ctx);
        String bureauClass = bureauDefaulted ? FactClassification.DEFAULTED.name()
                : classifySource(ctx.bureauSource());
        pending.add(fact("bureau.consumer.score", "INTEGER", ctx.effectiveBureauScore(),
                bureauClass, List.of(sourceIds.get("BUREAU")),
                meta("CreditControlService", "effectiveBureauScore", bureauDefaulted, bureauDefaulted)));
        pending.add(fact("bureau.source_available", "BOOLEAN",
                !"DEMO_FALLBACK".equalsIgnoreCase(ctx.bureauSource())
                        && !"PROVIDER_GAP".equalsIgnoreCase(ctx.bureauSource()),
                FactClassification.DERIVED.name(),
                List.of(sourceIds.get("BUREAU")),
                meta("UnderwritingFactSnapshotBuilder", "bureauSource", false, false)));

        emitCanonicalBureauMetricFacts(pending, sourceIds, canonicalBureau);
        emitCanonicalGstFacts(pending, sourceIds, canonicalGst);

        if (sc.get("LIVE_UNSECURED_LOAN_COUNT") != null && !canonicalBureau.hasAvailableLiveUnsecured()) {
            pending.add(fact("bureau.live_unsecured_loan_count", "INTEGER", sc.get("LIVE_UNSECURED_LOAN_COUNT"),
                    classifyScorecardKey("LIVE_UNSECURED_LOAN_COUNT", ctx, gapActive, demoActive, manualKeys),
                    List.of(sourceIds.get("BUREAU")),
                    meta("CreditControlService", "LIVE_UNSECURED_LOAN_COUNT",
                            isDefaulted("LIVE_UNSECURED_LOAN_COUNT", ctx, gapActive, demoActive, manualKeys),
                            isDefaulted("LIVE_UNSECURED_LOAN_COUNT", ctx, gapActive, demoActive, manualKeys))));
        }
        if (sc.get("TOTAL_LIVE_EXPOSURE") != null && !canonicalBureau.hasMetric(BureauMetricService.TOTAL_LIVE_EXPOSURE)) {
            pending.add(fact("bureau.total_live_exposure", "DECIMAL", sc.get("TOTAL_LIVE_EXPOSURE"),
                    classifyScorecardKey("TOTAL_LIVE_EXPOSURE", ctx, gapActive, demoActive, manualKeys),
                    List.of(sourceIds.get("BUREAU")),
                    meta("CreditControlService", "TOTAL_LIVE_EXPOSURE", false, false)));
        }
        if (ctx.effectiveObligation() != null) {
            pending.add(fact("bureau.total_monthly_obligation", "DECIMAL", ctx.effectiveObligation(),
                    isIncomeDefaulted(ctx) || "DEMO_FALLBACK".equalsIgnoreCase(ctx.incomeSource())
                            ? FactClassification.DEFAULTED.name()
                            : classifySource(ctx.incomeSource()),
                    List.of(sourceIds.get("INCOME")),
                    meta("CreditControlService", "effectiveObligation",
                            isIncomeDefaulted(ctx), isIncomeDefaulted(ctx))));
            pending.add(fact("obligations.monthly_emi_total", "DECIMAL", ctx.effectiveObligation(),
                    isIncomeDefaulted(ctx) ? FactClassification.DEFAULTED.name()
                            : FactClassification.DERIVED.name(),
                    List.of(sourceIds.get("INCOME")),
                    meta("CreditControlService", "effectiveObligation", isIncomeDefaulted(ctx), false)));
        }
        if (sc.get("MAX_DPD") != null) {
            pending.add(fact("bureau.max_dpd", "INTEGER", sc.get("MAX_DPD"),
                    classifyScorecardKey("MAX_DPD", ctx, gapActive, demoActive, manualKeys),
                    List.of(sourceIds.get("BUREAU")),
                    meta("CreditControlService", "MAX_DPD", false, false)));
        }

        if (sc.get("BANK_STATEMENT_INCOME") != null || sc.get("MONTHLY_INCOME") != null) {
            BigDecimal monthlyCredit = sc.get("BANK_STATEMENT_INCOME") != null
                    ? sc.get("BANK_STATEMENT_INCOME") : sc.get("MONTHLY_INCOME");
            pending.add(fact("banking.monthly_credit", "DECIMAL", monthlyCredit,
                    classifyScorecardKey("MONTHLY_INCOME", ctx, gapActive, demoActive, manualKeys),
                    List.of(sourceIds.get("INCOME")),
                    meta("CreditControlService", "MONTHLY_INCOME",
                            isDefaulted("MONTHLY_INCOME", ctx, gapActive, demoActive, manualKeys), false)));
        }
        if (sc.get("AVERAGE_BANK_BALANCE") != null) {
            pending.add(fact("banking.average_balance", "DECIMAL", sc.get("AVERAGE_BANK_BALANCE"),
                    classifyScorecardKey("AVERAGE_BANK_BALANCE", ctx, gapActive, demoActive, manualKeys),
                    List.of(sourceIds.get("INCOME")),
                    meta("CreditControlService", "AVERAGE_BANK_BALANCE",
                            isDefaulted("AVERAGE_BANK_BALANCE", ctx, gapActive, demoActive, manualKeys),
                            isDefaulted("AVERAGE_BANK_BALANCE", ctx, gapActive, demoActive, manualKeys))));
        }
        if (sc.get("EMI_OBLIGATION") != null) {
            pending.add(fact("banking.existing_emi", "DECIMAL", sc.get("EMI_OBLIGATION"),
                    classifyScorecardKey("EMI_OBLIGATION", ctx, gapActive, demoActive, manualKeys),
                    List.of(sourceIds.get("INCOME")),
                    meta("CreditControlService", "EMI_OBLIGATION",
                            isDefaulted("EMI_OBLIGATION", ctx, gapActive, demoActive, manualKeys), false)));
        }

        if (sc.get("PROPERTY_VALUE") != null) {
            pending.add(fact("collateral.market_value", "DECIMAL", sc.get("PROPERTY_VALUE"),
                    manualKeys.contains("propertyValue") || manualKeys.contains("PROPERTY_VALUE")
                            ? FactClassification.MANUAL.name()
                            : FactClassification.DECLARED.name(),
                    List.of(sourceIds.get("LEGACY_CONTEXT")),
                    meta("CreditControlService", "PROPERTY_VALUE", false, false)));
        }

        if (sc.get("OBLIGATION_RATIO") != null) {
            boolean foirDefaulted = isDefaulted("OBLIGATION_RATIO", ctx, gapActive, demoActive, manualKeys)
                    || demoActive;
            pending.add(fact("metrics.foir", "DECIMAL", sc.get("OBLIGATION_RATIO"),
                    foirDefaulted ? FactClassification.DEFAULTED.name() : FactClassification.DERIVED.name(),
                    List.of(sourceIds.get("LEGACY_CONTEXT")),
                    meta("CreditControlService", "OBLIGATION_RATIO", foirDefaulted, foirDefaulted)));
        }
        if (sc.get("LTV") != null) {
            pending.add(fact("metrics.ltv", "DECIMAL", sc.get("LTV"),
                    FactClassification.DERIVED.name(),
                    List.of(sourceIds.get("LEGACY_CONTEXT")),
                    meta("CreditControlService", "LTV", false, false)));
        }

        if (app.getRequestedAmount() != null) {
            pending.add(fact("limit.requested_amount", "DECIMAL", app.getRequestedAmount(),
                    FactClassification.DECLARED.name(),
                    List.of(sourceIds.get("APPLICATION")),
                    meta("LoanApplication", "requestedAmount", false, false)));
        }
        BigDecimal eligible = firstPresent(sc, "SCF_ELIGIBLE_AMOUNT", "ELIGIBLE_AMOUNT");
        if (eligible != null) {
            pending.add(fact("limit.computed_eligible_amount", "DECIMAL", eligible,
                    FactClassification.DERIVED.name(),
                    List.of(sourceIds.get("LEGACY_CONTEXT")),
                    meta("LimitSizingService", "ELIGIBLE_AMOUNT", false, false)));
        }
        if (sc.get("SANCTION_CAP") != null) {
            pending.add(fact("limit.sanction_cap", "DECIMAL", sc.get("SANCTION_CAP"),
                    FactClassification.DERIVED.name(),
                    List.of(sourceIds.get("LEGACY_CONTEXT")),
                    meta("LimitSizingService", "SANCTION_CAP", false, false)));
        }
    }

    private void emitCompatFacts(
            EffectiveUnderwritingContext ctx,
            Map<String, UUID> sourceIds,
            List<PendingFact> pending,
            CanonicalBureauFacts canonicalBureau,
            CanonicalGstFacts canonicalGst,
            CanonicalTaxFacts canonicalTax) {
        Map<String, BigDecimal> sc = ctx.scorecard() != null ? ctx.scorecard() : Map.of();
        boolean gapActive = sc.containsKey("PROVIDER_GAP_DEFAULT_ACTIVE") || sc.containsKey("DEMO_FALLBACK_ACTIVE");
        boolean demoActive = sc.containsKey("DEMO_FALLBACK_ACTIVE");
        Set<String> manualKeys = Set.of(); // already classified via scorecard helpers using ctx sources

        // Named engine keys
        putCompat(pending, "BUREAU_SCORE", BigDecimal.valueOf(ctx.effectiveBureauScore()),
                isBureauDefaulted(ctx) ? FactClassification.DEFAULTED.name() : classifySource(ctx.bureauSource()),
                sourceIds.get("BUREAU"), "effectiveBureauScore", isBureauDefaulted(ctx), Map.of());
        if (ctx.effectiveIncome() != null) {
            putCompat(pending, "MONTHLY_INCOME", ctx.effectiveIncome(),
                    isIncomeDefaulted(ctx) ? FactClassification.DEFAULTED.name() : classifySource(ctx.incomeSource()),
                    sourceIds.get("INCOME"), "effectiveIncome", isIncomeDefaulted(ctx), Map.of());
        }
        if (ctx.effectiveObligation() != null) {
            putCompat(pending, "MONTHLY_OBLIGATION", ctx.effectiveObligation(),
                    isIncomeDefaulted(ctx) ? FactClassification.DEFAULTED.name() : classifySource(ctx.incomeSource()),
                    sourceIds.get("INCOME"), "effectiveObligation", isIncomeDefaulted(ctx), Map.of());
        }
        putCompat(pending, "KYC_SUCCESS", ctx.kycPassEffective() ? BigDecimal.ONE : BigDecimal.ZERO,
                "DEMO_FALLBACK".equalsIgnoreCase(ctx.kycSource())
                        ? FactClassification.DEFAULTED.name() : classifySource(ctx.kycSource()),
                sourceIds.get("KYC"), "kycPassEffective",
                "DEMO_FALLBACK".equalsIgnoreCase(ctx.kycSource()), Map.of());

        if (sc.get("OBLIGATION_RATIO") != null) {
            putCompat(pending, "FOIR", sc.get("OBLIGATION_RATIO"),
                    FactClassification.DERIVED.name(), sourceIds.get("LEGACY_CONTEXT"), "OBLIGATION_RATIO", false, Map.of());
            putCompat(pending, "OBLIGATION_RATIO", sc.get("OBLIGATION_RATIO"),
                    FactClassification.DERIVED.name(), sourceIds.get("LEGACY_CONTEXT"), "OBLIGATION_RATIO", false, Map.of());
        }
        if (sc.get("LTV") != null) {
            putCompat(pending, "LTV", sc.get("LTV"),
                    FactClassification.DERIVED.name(), sourceIds.get("LEGACY_CONTEXT"), "LTV", false, Map.of());
        }

        // Store ALL scorecard entries under compat.*
        for (Map.Entry<String, BigDecimal> e : sc.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            String key = e.getKey();
            // Skip duplicates already emitted with special classification above
            if ("BUREAU_SCORE".equals(key) || "MONTHLY_INCOME".equals(key)
                    || "MONTHLY_OBLIGATION".equals(key) || "KYC_SUCCESS".equals(key)
                    || "FOIR".equals(key)) {
                continue;
            }
            String classification = classifyScorecardKey(key, ctx, gapActive, demoActive, manualKeys);
            boolean defaulted = isDefaulted(key, ctx, gapActive, demoActive, manualKeys);
            Map<String, Object> extra = Map.of();
            if ("LIVE_UNSECURED_LOAN_COUNT".equals(key)) {
                extra = liveUnsecuredCompatMeta(classification, defaulted, canonicalBureau);
            } else if ("ANNUAL_GST_TURNOVER".equals(key)) {
                extra = annualGstCompatMeta(classification, defaulted, canonicalGst);
            } else if ("ITR_INCOME".equals(key) || "PAT".equals(key) || "EBITDA".equals(key)
                    || "TOL".equals(key) || "TNW".equals(key)) {
                extra = itrCompatMeta(key, classification, defaulted, canonicalTax);
            }
            putCompat(pending, key, e.getValue(), classification,
                    sourceIds.getOrDefault("LEGACY_CONTEXT", sourceIds.get("APPLICATION")),
                    key, defaulted, extra);
        }
    }

    private void emitCanonicalBureauMetricFacts(
            List<PendingFact> pending, Map<String, UUID> sourceIds, CanonicalBureauFacts canonical) {
        if (canonical == null || !canonical.available()) {
            return;
        }
        UUID bureauSourceId = sourceIds.get("BUREAU");
        for (CiMetricResult m : canonical.metrics()) {
            String path = metricToFactPath(m.getMetricCode());
            if (path == null) {
                continue;
            }
            boolean insufficient = BureauMetricOutcome.DATA_INSUFFICIENT.name().equals(m.getOutcome());
            Object value = unwrapMetricValue(m.getValue());
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("originService", "BureauMetricService");
            meta.put("originField", m.getMetricCode());
            meta.put("defaulted", false);
            meta.put("originalMissing", insufficient);
            meta.put("canonicalMetricVersion", m.getMetricVersion());
            meta.put("metricOutcome", m.getOutcome());
            meta.put("qualityStatus", m.getDataQualityStatus());
            if (canonical.report() != null) {
                meta.put("bureauReportId", canonical.report().getId().toString());
                meta.put("parserVersion", canonical.report().getParserVersion());
            }
            if (insufficient) {
                meta.put("qualityStatus", "DATA_INSUFFICIENT");
            }
            String valueType = value instanceof BigDecimal ? "DECIMAL"
                    : value instanceof Integer ? "INTEGER" : "DECIMAL";
            // Prefer emit with null value when DATA_INSUFFICIENT
            pending.add(fact(path, valueType, insufficient ? null : value,
                    FactClassification.DERIVED.name(),
                    bureauSourceId != null ? List.of(bureauSourceId) : List.of(),
                    meta));
        }
        if (canonical.report() != null) {
            Map<String, Object> availMeta = meta("BureauNormalizationService", "report", false, false);
            availMeta.put("bureauReportId", canonical.report().getId().toString());
            pending.add(fact("bureau.report.available", "BOOLEAN", true,
                    FactClassification.DERIVED.name(),
                    bureauSourceId != null ? List.of(bureauSourceId) : List.of(),
                    availMeta));
            if (canonical.report().getReportDate() != null) {
                pending.add(fact("bureau.report.report_date", "DATE",
                        canonical.report().getReportDate().toString(),
                        FactClassification.EXTRACTED.name(),
                        bureauSourceId != null ? List.of(bureauSourceId) : List.of(),
                        availMeta));
            }
            pending.add(fact("bureau.report.quality_status", "STRING",
                    canonical.report().getQualityStatus(),
                    FactClassification.DERIVED.name(),
                    bureauSourceId != null ? List.of(bureauSourceId) : List.of(),
                    availMeta));
        }
    }

    private void emitCanonicalGstFacts(
            List<PendingFact> pending, Map<String, UUID> sourceIds, CanonicalGstFacts canonical) {
        if (canonical == null || !canonical.available()) {
            return;
        }
        UUID gstSourceId = sourceIds.get("GST");
        for (CiMetricResult m : canonical.metrics()) {
            String path = gstMetricToFactPath(m.getMetricCode());
            if (path == null) {
                continue;
            }
            boolean insufficient = GstMetricOutcome.DATA_INSUFFICIENT.name().equals(m.getOutcome());
            Object value = unwrapMetricValue(m.getValue());
            // Never emit turnover 0 for DATA_INSUFFICIENT
            if (insufficient && path.startsWith("gst.turnover.")) {
                value = null;
            }
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("originService", "GstMetricService");
            meta.put("originField", m.getMetricCode());
            meta.put("defaulted", false);
            meta.put("originalMissing", insufficient);
            meta.put("canonicalMetricVersion", m.getMetricVersion());
            meta.put("metricOutcome", m.getOutcome());
            meta.put("qualityStatus", insufficient ? "DATA_INSUFFICIENT" : m.getDataQualityStatus());
            if (!canonical.registrations().isEmpty()) {
                meta.put("gstRegistrationId", canonical.registrations().get(0).getId().toString());
                meta.put("parserVersion", canonical.registrations().get(0).getParserVersion());
            }
            String valueType = value instanceof Integer ? "INTEGER" : "DECIMAL";
            pending.add(fact(path, valueType, insufficient ? null : value,
                    FactClassification.DERIVED.name(),
                    gstSourceId != null ? List.of(gstSourceId) : List.of(),
                    meta));
        }
        if (!canonical.registrations().isEmpty()) {
            CiGstRegistration reg = canonical.registrations().get(0);
            Map<String, Object> availMeta = meta("GstNormalizationService", "registration", false, false);
            availMeta.put("gstRegistrationId", reg.getId().toString());
            pending.add(fact("gst.registration.available", "BOOLEAN", true,
                    FactClassification.DERIVED.name(),
                    gstSourceId != null ? List.of(gstSourceId) : List.of(),
                    availMeta));
            pending.add(fact("gst.registration.status", "STRING", reg.getRegistrationStatus(),
                    FactClassification.EXTRACTED.name(),
                    gstSourceId != null ? List.of(gstSourceId) : List.of(),
                    availMeta));
            pending.add(fact("gst.registration.active", "BOOLEAN",
                    "ACTIVE".equalsIgnoreCase(reg.getRegistrationStatus()),
                    FactClassification.DERIVED.name(),
                    gstSourceId != null ? List.of(gstSourceId) : List.of(),
                    availMeta));
            pending.add(fact("gst.registration.gstin_count", "INTEGER", canonical.registrations().size(),
                    FactClassification.DERIVED.name(),
                    gstSourceId != null ? List.of(gstSourceId) : List.of(),
                    availMeta));
        }
    }

    private CanonicalGstFacts loadCanonicalGstFacts(UUID applicationId) {
        List<CiGstRegistration> regs =
                gstRegistrationRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId);
        if (regs == null || regs.isEmpty()) {
            return CanonicalGstFacts.none();
        }
        List<CiMetricResult> metrics = metricResultRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId)
                .stream()
                .filter(m -> m.getMetricCode() != null && m.getMetricCode().startsWith("gst."))
                .toList();
        // Keep latest per metric code
        Map<String, CiMetricResult> latest = new LinkedHashMap<>();
        for (CiMetricResult m : metrics) {
            latest.putIfAbsent(m.getMetricCode(), m);
        }
        return new CanonicalGstFacts(regs, new ArrayList<>(latest.values()));
    }

    private static Map<String, Object> annualGstCompatMeta(
            String classification, boolean defaulted, CanonicalGstFacts canonical) {
        Map<String, Object> m = new LinkedHashMap<>();
        String compatSource;
        if (FactClassification.MANUAL.name().equals(classification)) {
            compatSource = "MANUAL";
        } else if (defaulted || FactClassification.DEFAULTED.name().equals(classification)) {
            compatSource = "DEFAULTED";
        } else if (canonical.hasAvailableTrailing12m()) {
            compatSource = "CANONICAL";
        } else {
            compatSource = "PROVIDER";
        }
        m.put("compatibilitySource", compatSource);
        m.put("canonicalSourceAvailable", canonical.available());
        m.put("canonicalMetricVersion", "V1");
        if (canonical.trailing12m() != null) {
            m.put("canonicalOutcome", canonical.trailing12m().getOutcome());
            m.put("canonicalDataInsufficient",
                    GstMetricOutcome.DATA_INSUFFICIENT.name().equals(canonical.trailing12m().getOutcome()));
        }
        // Do NOT overwrite production scorecard — compat fact keeps legacy value;
        // canonical trailing_12m is emitted separately as gst.turnover.trailing_12m
        m.put("productionScorecardUnchanged", true);
        return m;
    }

    private static String gstMetricToFactPath(String metricCode) {
        if (metricCode == null) {
            return null;
        }
        // Metric codes already align with fact paths for GST
        return metricCode;
    }

    private CanonicalBureauFacts loadCanonicalBureauFacts(UUID applicationId) {
        Optional<CiBureauReport> reportOpt =
                bureauReportRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId);
        if (reportOpt.isEmpty()) {
            return CanonicalBureauFacts.none();
        }
        CiBureauReport report = reportOpt.get();
        List<CiMetricResult> metrics = metricResultRepository.findByBureauReportId(report.getId());
        return new CanonicalBureauFacts(report, metrics);
    }

    private static Map<String, Object> liveUnsecuredCompatMeta(
            String classification, boolean defaulted, CanonicalBureauFacts canonical) {
        Map<String, Object> m = new LinkedHashMap<>();
        String compatSource = FactClassification.MANUAL.name().equals(classification) ? "MANUAL"
                : (defaulted || FactClassification.DEFAULTED.name().equals(classification) ? "DEFAULTED"
                : (canonical.hasAvailableLiveUnsecured() ? "CANONICAL" : "LEGACY"));
        m.put("compatibilitySource", compatSource);
        m.put("canonicalSourceAvailable", canonical.available());
        m.put("canonicalMetricVersion", "V1");
        if (canonical.liveUnsecured() != null) {
            m.put("canonicalOutcome", canonical.liveUnsecured().getOutcome());
            m.put("canonicalDataInsufficient",
                    BureauMetricOutcome.DATA_INSUFFICIENT.name().equals(canonical.liveUnsecured().getOutcome()));
        }
        return m;
    }

    private static String metricToFactPath(String metricCode) {
        if (metricCode == null) {
            return null;
        }
        return switch (metricCode) {
            case BureauMetricService.LIVE_UNSECURED -> "bureau.live_unsecured_loan_count";
            case BureauMetricService.TOTAL_LIVE_EXPOSURE -> "bureau.total_live_exposure";
            case BureauMetricService.SECURED_LIVE_EXPOSURE -> "bureau.exposure.secured_live";
            case BureauMetricService.UNSECURED_LIVE_EXPOSURE -> "bureau.exposure.unsecured_live";
            case BureauMetricService.TOTAL_MONTHLY_OBLIGATION -> "bureau.total_monthly_obligation";
            case BureauMetricService.MAX_DPD_12M -> "bureau.dpd.max_12m";
            case BureauMetricService.MAX_DPD_24M -> "bureau.dpd.max_24m";
            case BureauMetricService.RECENT_INQUIRIES_90D -> "bureau.inquiries.count_90d";
            case BureauMetricService.SETTLED_ACCOUNT_COUNT -> "bureau.accounts.settled_count";
            case BureauMetricService.WRITTEN_OFF_ACCOUNT_COUNT -> "bureau.accounts.written_off_count";
            case BureauMetricService.WRITEOFF_NON_CC -> "bureau.accounts.writeoff_non_cc";
            case BureauMetricService.WRITEOFF_CC -> "bureau.accounts.cc_writeoff";
            default -> metricCode;
        };
    }

    private static Object unwrapMetricValue(Map<String, Object> value) {
        if (value == null || !value.containsKey("v")) {
            return null;
        }
        Object raw = value.get("v");
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            if (raw instanceof Integer || raw instanceof Long) {
                return n.intValue();
            }
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            String s = String.valueOf(raw);
            if (s.contains(".")) {
                return new BigDecimal(s);
            }
            return Integer.valueOf(s);
        } catch (Exception e) {
            return raw;
        }
    }

    private void putCompat(
            List<PendingFact> pending,
            String key,
            Object value,
            String classification,
            UUID sourceId,
            String originField,
            boolean defaulted,
            Map<String, Object> extraMeta) {
        String valueType = value instanceof Boolean ? "BOOLEAN"
                : value instanceof Integer ? "INTEGER"
                : value instanceof BigDecimal ? "DECIMAL" : "DECIMAL";
        Map<String, Object> m = meta("CreditControlService", originField, defaulted, defaulted);
        if (extraMeta != null && !extraMeta.isEmpty()) {
            m.putAll(extraMeta);
        }
        pending.add(fact("compat." + key, valueType, value, classification,
                sourceId != null ? List.of(sourceId) : List.of(),
                m));
    }

    private void emitCanonicalBankingFacts(
            List<PendingFact> pending, Map<String, UUID> sourceIds, CanonicalBankingFacts canonical) {
        if (canonical == null || !canonical.available()) {
            return;
        }
        UUID bankSourceId = sourceIds.get("ACCOUNT_AGGREGATOR");
        if (bankSourceId == null) {
            bankSourceId = sourceIds.get("INCOME");
        }
        for (CiMetricResult m : canonical.metrics()) {
            String path = bankingMetricToFactPath(m.getMetricCode());
            if (path == null) {
                continue;
            }
            boolean insufficient = BankingMetricOutcome.DATA_INSUFFICIENT.name().equals(m.getOutcome());
            Object value = unwrapMetricValue(m.getValue());
            if (insufficient) {
                value = null;
            }
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("originService", "BankingMetricService");
            meta.put("originField", m.getMetricCode());
            meta.put("defaulted", false);
            meta.put("originalMissing", insufficient);
            meta.put("canonicalMetricVersion", m.getMetricVersion());
            meta.put("metricOutcome", m.getOutcome());
            meta.put("qualityStatus", insufficient ? "DATA_INSUFFICIENT" : m.getDataQualityStatus());
            if (!canonical.accounts().isEmpty()) {
                meta.put("bankAccountId", canonical.accounts().get(0).getId().toString());
            }
            // Compat metadata for legacy keys — never overwrite production scorecard facts
            if (BankingMetricService.ADB_3M.equals(m.getMetricCode())) {
                meta.put("compatLegacyKey", "AVERAGE_BANK_BALANCE");
            } else if (BankingMetricService.ADJ_12M.equals(m.getMetricCode())) {
                meta.put("compatLegacyKey", "ANNUAL_BANKING_TURNOVER");
            } else if (BankingMetricService.MONTHLY_OBL.equals(m.getMetricCode())) {
                meta.put("compatLegacyKey", "EMI_OBLIGATION");
            } else if (BankingMetricService.CHEQUE_3M.equals(m.getMetricCode())
                    || BankingMetricService.CHEQUE_6M.equals(m.getMetricCode())) {
                meta.put("compatLegacyKey", "CHEQUE_BOUNCES_*");
            }
            String valueType = value instanceof Integer ? "INTEGER" : "DECIMAL";
            pending.add(fact(path, valueType, value,
                    FactClassification.DERIVED.name(),
                    bankSourceId != null ? List.of(bankSourceId) : List.of(),
                    meta));
        }
        Map<String, Object> availMeta = meta("BankingNormalizationService", "accounts", false, false);
        pending.add(fact("banking.account.count", "INTEGER", canonical.accounts().size(),
                FactClassification.DERIVED.name(),
                bankSourceId != null ? List.of(bankSourceId) : List.of(),
                availMeta));
    }

    /**
     * Annotate existing legacy banking compat facts with canonical availability — do not change values.
     */
    private void emitBankingCompatMetadata(List<PendingFact> pending, CanonicalBankingFacts canonical) {
        // Compatibility is encoded on canonical metric metadata (compatLegacyKey).
        // Production scorecard values already emitted above remain unchanged.
        if (canonical == null || !canonical.available()) {
            return;
        }
        // Optional marker fact for admin compare
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("originService", "BankingMetricService");
        meta.put("canonicalSourceAvailable", true);
        meta.put("note", "Legacy AVERAGE_BANK_BALANCE / ANNUAL_BANKING_TURNOVER / EMI_OBLIGATION unchanged");
        pending.add(fact("banking.canonical.available", "BOOLEAN", true,
                FactClassification.DERIVED.name(), List.of(), meta));
    }

    private CanonicalBankingFacts loadCanonicalBankingFacts(UUID applicationId) {
        List<CiBankAccount> accounts =
                bankAccountRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId);
        if (accounts == null || accounts.isEmpty()) {
            return CanonicalBankingFacts.none();
        }
        List<CiMetricResult> metrics = metricResultRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId)
                .stream()
                .filter(m -> m.getMetricCode() != null && m.getMetricCode().startsWith("banking."))
                .toList();
        Map<String, CiMetricResult> latest = new LinkedHashMap<>();
        for (CiMetricResult m : metrics) {
            latest.putIfAbsent(m.getMetricCode(), m);
        }
        return new CanonicalBankingFacts(accounts, new ArrayList<>(latest.values()));
    }

    private static String bankingMetricToFactPath(String metricCode) {
        if (metricCode == null) {
            return null;
        }
        return switch (metricCode) {
            case BankingMetricService.ADB_3M -> "banking.balance.average_3m";
            case BankingMetricService.ADB_6M -> "banking.balance.average_6m";
            case BankingMetricService.ADB_12M -> "banking.balance.average_12m";
            case BankingMetricService.ADJ_3M -> "banking.credit.total_3m";
            case BankingMetricService.ADJ_6M -> "banking.credit.total_6m";
            case BankingMetricService.ADJ_12M -> "banking.credit.adjusted_business_12m";
            case BankingMetricService.AVG_M_6M -> "banking.credit.average_monthly_6m";
            case BankingMetricService.AVG_M_12M -> "banking.credit.average_monthly_12m";
            case BankingMetricService.MIN_BAL_3M -> "banking.balance.minimum_3m";
            case BankingMetricService.NEG_DAYS_6M -> "banking.balance.negative_days_6m";
            case BankingMetricService.CASH_RATIO_12M -> "banking.cash.deposit_ratio_12m";
            case BankingMetricService.CHEQUE_3M -> "banking.bounce.cheque_count_3m";
            case BankingMetricService.CHEQUE_6M -> "banking.bounce.cheque_count_6m";
            case BankingMetricService.NACH_3M -> "banking.bounce.nach_count_3m";
            case BankingMetricService.NACH_6M -> "banking.bounce.nach_count_6m";
            case BankingMetricService.MONTHLY_OBL -> "banking.obligation.emi_total_monthly";
            case BankingMetricService.OD_AVG_6M -> "banking.od_cc.average_utilisation_6m";
            case BankingMetricService.OD_PEAK_6M -> "banking.od_cc.peak_utilisation_6m";
            case BankingMetricService.OD_DAYS_90 -> "banking.od_cc.days_above_90pct_6m";
            case BankingMetricService.COMPLETENESS -> "banking.statement.completeness_ratio";
            case BankingMetricService.EMI_BOUNCE_3M -> "banking.bounce.emi_count_3m";
            default -> null;
        };
    }

    private record CanonicalBankingFacts(List<CiBankAccount> accounts, List<CiMetricResult> metrics) {
        static CanonicalBankingFacts none() {
            return new CanonicalBankingFacts(List.of(), List.of());
        }

        boolean available() {
            return accounts != null && !accounts.isEmpty();
        }
    }

    private void emitCanonicalTaxFacts(
            List<PendingFact> pending, Map<String, UUID> sourceIds, CanonicalTaxFacts canonical) {
        if (canonical == null || !canonical.available()) {
            return;
        }
        UUID itrSourceId = sourceIds.get("ITR");
        for (CiMetricResult m : canonical.metrics()) {
            String path = taxMetricToFactPath(m.getMetricCode());
            if (path == null) {
                continue;
            }
            boolean insufficient = TaxMetricOutcome.DATA_INSUFFICIENT.name().equals(m.getOutcome())
                    || TaxMetricOutcome.NOT_APPLICABLE.name().equals(m.getOutcome());
            Object value = unwrapMetricValue(m.getValue());
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("originService", "TaxMetricService");
            meta.put("originField", m.getMetricCode());
            meta.put("defaulted", false);
            meta.put("originalMissing", insufficient);
            meta.put("canonicalMetricVersion", m.getMetricVersion());
            meta.put("metricOutcome", m.getOutcome());
            if (!canonical.returns().isEmpty()) {
                meta.put("itrReturnId", canonical.returns().get(0).getId().toString());
                meta.put("parserVersion", canonical.returns().get(0).getParserVersion());
                meta.put("compatLegacyKey", compatLegacyForTaxMetric(m.getMetricCode()));
            }
            String valueType = value instanceof Integer || value instanceof Long ? "INTEGER" : "DECIMAL";
            pending.add(fact(path, valueType, insufficient ? null : value,
                    FactClassification.DERIVED.name(),
                    itrSourceId != null ? List.of(itrSourceId) : List.of(),
                    meta));
        }
        CiItrReturn latest = canonical.returns().get(0);
        Map<String, Object> availMeta = meta("TaxNormalizationService", "itrReturn", false, false);
        availMeta.put("itrReturnId", latest.getId().toString());
        pending.add(fact("itr.return.available", "BOOLEAN", true,
                FactClassification.DERIVED.name(),
                itrSourceId != null ? List.of(itrSourceId) : List.of(),
                availMeta));
        pending.add(fact("itr.return.latest_assessment_year", "STRING", latest.getAssessmentYear(),
                FactClassification.EXTRACTED.name(),
                itrSourceId != null ? List.of(itrSourceId) : List.of(),
                availMeta));
        if (latest.getFinancialYear() != null) {
            pending.add(fact("itr.return.latest_financial_year", "STRING", latest.getFinancialYear(),
                    FactClassification.EXTRACTED.name(),
                    itrSourceId != null ? List.of(itrSourceId) : List.of(),
                    availMeta));
        }
        pending.add(fact("itr.return.form", "STRING", latest.getItrForm(),
                FactClassification.EXTRACTED.name(),
                itrSourceId != null ? List.of(itrSourceId) : List.of(),
                availMeta));
        pending.add(fact("ais.available", "BOOLEAN", canonical.aisAvailable(),
                FactClassification.DERIVED.name(),
                itrSourceId != null ? List.of(itrSourceId) : List.of(),
                availMeta));
        pending.add(fact("form26as.available", "BOOLEAN", canonical.form26AsAvailable(),
                FactClassification.DERIVED.name(),
                itrSourceId != null ? List.of(itrSourceId) : List.of(),
                availMeta));
    }

    private void emitTaxCompatMetadata(List<PendingFact> pending, CanonicalTaxFacts canonical) {
        // Compatibility is encoded on canonical metric / compat fact metadata.
    }

    private CanonicalTaxFacts loadCanonicalTaxFacts(UUID applicationId) {
        List<CiItrReturn> returns =
                itrReturnRepository.findByApplicationIdAndEffectiveTrueOrderByAssessmentYearDesc(applicationId);
        if (returns == null || returns.isEmpty()) {
            return CanonicalTaxFacts.none();
        }
        List<CiMetricResult> metrics = metricResultRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId)
                .stream()
                .filter(m -> m.getMetricCode() != null
                        && (m.getMetricCode().startsWith("itr.") || m.getMetricCode().startsWith("xsrc.itr_")))
                .toList();
        Map<String, CiMetricResult> latest = new LinkedHashMap<>();
        for (CiMetricResult m : metrics) {
            latest.putIfAbsent(m.getMetricCode(), m);
        }
        boolean ais = !aisSummaryRepository.findByApplicationIdOrderByFinancialYearDesc(applicationId).isEmpty();
        boolean f26 = !form26AsSummaryRepository.findByApplicationIdOrderByFinancialYearDesc(applicationId).isEmpty();
        return new CanonicalTaxFacts(returns, new ArrayList<>(latest.values()), ais, f26);
    }

    private static String taxMetricToFactPath(String metricCode) {
        if (metricCode == null) {
            return null;
        }
        return switch (metricCode) {
            case TaxMetricService.TURNOVER_LATEST -> "itr.business.turnover";
            case TaxMetricService.TOTAL_INCOME_LATEST -> "itr.income.total";
            case TaxMetricService.BUSINESS_INCOME_LATEST -> "itr.income.business_profession";
            case TaxMetricService.EBITDA_MARGIN -> "itr.business.ebitda";
            case TaxMetricService.PAT_MARGIN -> "itr.business.pat";
            case TaxMetricService.TOL_TNW -> "itr.balance.net_worth";
            case TaxMetricService.DEBT_EQUITY -> "itr.balance.total_borrowings";
            default -> metricCode.startsWith("itr.") || metricCode.startsWith("xsrc.") ? metricCode : null;
        };
    }

    private static String compatLegacyForTaxMetric(String metricCode) {
        if (TaxMetricService.TURNOVER_LATEST.equals(metricCode)
                || TaxMetricService.TOTAL_INCOME_LATEST.equals(metricCode)) {
            return "ITR_INCOME";
        }
        if (TaxMetricService.PAT_MARGIN.equals(metricCode)) {
            return "PAT";
        }
        if (TaxMetricService.EBITDA_MARGIN.equals(metricCode)) {
            return "EBITDA";
        }
        if (TaxMetricService.TOL_TNW.equals(metricCode)) {
            return "TOL/TNW";
        }
        return null;
    }

    private static Map<String, Object> itrCompatMeta(
            String key, String classification, boolean defaulted, CanonicalTaxFacts canonical) {
        Map<String, Object> m = new LinkedHashMap<>();
        String compatSource;
        if (FactClassification.MANUAL.name().equals(classification)) {
            compatSource = "MANUAL";
        } else if (defaulted) {
            compatSource = "DEFAULTED";
        } else if (canonical != null && canonical.available()) {
            compatSource = "CANONICAL";
        } else {
            compatSource = "PROVIDER";
        }
        m.put("compatibilitySource", compatSource);
        m.put("compatLegacyKey", key);
        m.put("note", "Production CreditControl / SCF_GAP_ITR_INCOME unchanged");
        if (canonical != null && canonical.available() && !canonical.returns().isEmpty()) {
            m.put("canonicalReturnId", canonical.returns().get(0).getId().toString());
        }
        return m;
    }

    private record CanonicalTaxFacts(
            List<CiItrReturn> returns,
            List<CiMetricResult> metrics,
            boolean aisAvailable,
            boolean form26AsAvailable) {
        static CanonicalTaxFacts none() {
            return new CanonicalTaxFacts(List.of(), List.of(), false, false);
        }

        boolean available() {
            return returns != null && !returns.isEmpty();
        }
    }

    private record CanonicalBureauFacts(CiBureauReport report, List<CiMetricResult> metrics) {
        static CanonicalBureauFacts none() {
            return new CanonicalBureauFacts(null, List.of());
        }

        boolean available() {
            return report != null;
        }

        boolean hasMetric(String code) {
            return metrics != null && metrics.stream().anyMatch(m -> code.equals(m.getMetricCode())
                    && !BureauMetricOutcome.DATA_INSUFFICIENT.name().equals(m.getOutcome()));
        }

        boolean hasAvailableLiveUnsecured() {
            CiMetricResult m = liveUnsecured();
            return m != null && !BureauMetricOutcome.DATA_INSUFFICIENT.name().equals(m.getOutcome())
                    && m.getValue() != null;
        }

        CiMetricResult liveUnsecured() {
            if (metrics == null) {
                return null;
            }
            return metrics.stream()
                    .filter(m -> BureauMetricService.LIVE_UNSECURED.equals(m.getMetricCode()))
                    .findFirst().orElse(null);
        }
    }

    private record CanonicalGstFacts(List<CiGstRegistration> registrations, List<CiMetricResult> metrics) {
        static CanonicalGstFacts none() {
            return new CanonicalGstFacts(List.of(), List.of());
        }

        boolean available() {
            return registrations != null && !registrations.isEmpty();
        }

        CiMetricResult trailing12m() {
            if (metrics == null) {
                return null;
            }
            return metrics.stream()
                    .filter(m -> GstMetricService.TRAILING_12M.equals(m.getMetricCode()))
                    .findFirst().orElse(null);
        }

        boolean hasAvailableTrailing12m() {
            CiMetricResult m = trailing12m();
            return m != null
                    && !GstMetricOutcome.DATA_INSUFFICIENT.name().equals(m.getOutcome())
                    && m.getValue() != null;
        }
    }

    private static Map<String, Object> buildApplicationView(LoanApplication app) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("applicationId", app.getId() != null ? app.getId().toString() : null);
        view.put("applicationNumber", app.getApplicationNumber());
        view.put("requestedAmount", app.getRequestedAmount() != null ? app.getRequestedAmount().toPlainString() : null);
        view.put("tenureMonths", app.getTenureMonths());
        view.put("borrowerType", app.getBorrowerType() != null ? app.getBorrowerType().name() : null);
        view.put("loanProduct", app.getLoanProduct());
        Map<String, Object> personal = new LinkedHashMap<>();
        if (app.getPersonalInfo() != null) {
            if (app.getPersonalInfo().get("state") != null) {
                personal.put("state", app.getPersonalInfo().get("state"));
            }
            if (app.getPersonalInfo().get("city") != null) {
                personal.put("city", app.getPersonalInfo().get("city"));
            }
        }
        view.put("personalInfo", personal);
        return view;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> extractManualKeys(LoanApplication app) {
        Set<String> keys = new HashSet<>();
        if (app.getFinancialInfo() == null) {
            return keys;
        }
        Object ccObj = app.getFinancialInfo().get(CreditControlKeys.ROOT);
        if (!(ccObj instanceof Map<?, ?> cc)) {
            return keys;
        }
        Object manualObj = ((Map<String, Object>) cc).get(CreditControlKeys.MANUAL);
        if (manualObj instanceof Map<?, ?> manual) {
            for (Object k : manual.keySet()) {
                if (k != null) {
                    keys.add(String.valueOf(k));
                }
            }
        }
        return keys;
    }

    @SuppressWarnings("unchecked")
    private static BigDecimal extractDeclaredAnnualIncome(LoanApplication app) {
        if (app.getFinancialInfo() == null) {
            return null;
        }
        Map<String, Object> fi = app.getFinancialInfo();
        Object annual = fi.get("annualIncome");
        if (annual == null) {
            annual = fi.get("declaredAnnualIncome");
        }
        if (annual == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(annual));
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isBureauDefaulted(EffectiveUnderwritingContext ctx) {
        String s = ctx.bureauSource();
        return "DEMO_FALLBACK".equalsIgnoreCase(s) || "PROVIDER_GAP".equalsIgnoreCase(s);
    }

    private static boolean isIncomeDefaulted(EffectiveUnderwritingContext ctx) {
        String s = ctx.incomeSource();
        return "DEMO_FALLBACK".equalsIgnoreCase(s) || "PROVIDER_GAP".equalsIgnoreCase(s);
    }

    private static String classifySource(String source) {
        if (source == null) {
            return FactClassification.VERIFIED.name();
        }
        String s = source.toUpperCase(Locale.ROOT);
        if (s.contains("DEMO_FALLBACK") || s.contains("PROVIDER_GAP")) {
            return FactClassification.DEFAULTED.name();
        }
        if (s.contains("MANUAL")) {
            return FactClassification.MANUAL.name();
        }
        return FactClassification.VERIFIED.name();
    }

    private static String classifyScorecardKey(
            String key,
            EffectiveUnderwritingContext ctx,
            boolean gapActive,
            boolean demoActive,
            Set<String> manualKeys) {
        if (DERIVED_KEYS.contains(key)) {
            if ((demoActive || gapActive) && GAP_DEFAULTED_KEYS.contains(key)) {
                return FactClassification.DEFAULTED.name();
            }
            return FactClassification.DERIVED.name();
        }
        if (manualKeysContains(manualKeys, key)) {
            return FactClassification.MANUAL.name();
        }
        if ("BUREAU_SCORE".equals(key)) {
            return classifySource(ctx.bureauSource());
        }
        if ("MONTHLY_INCOME".equals(key) || "EMI_OBLIGATION".equals(key) || "MONTHLY_OBLIGATION".equals(key)) {
            return classifySource(ctx.incomeSource());
        }
        if (demoActive && ("MONTHLY_INCOME".equals(key) || GAP_DEFAULTED_KEYS.contains(key))) {
            return FactClassification.DEFAULTED.name();
        }
        if (gapActive && GAP_DEFAULTED_KEYS.contains(key)) {
            return FactClassification.DEFAULTED.name();
        }
        if ("DEMO_FALLBACK_ACTIVE".equals(key) || "PROVIDER_GAP_DEFAULT_ACTIVE".equals(key)) {
            return FactClassification.DERIVED.name();
        }
        return FactClassification.VERIFIED.name();
    }

    private static boolean isDefaulted(
            String key,
            EffectiveUnderwritingContext ctx,
            boolean gapActive,
            boolean demoActive,
            Set<String> manualKeys) {
        if (manualKeysContains(manualKeys, key)) {
            return false;
        }
        if ("BUREAU_SCORE".equals(key)) {
            return isBureauDefaulted(ctx);
        }
        if ("MONTHLY_INCOME".equals(key) || "EMI_OBLIGATION".equals(key) || "MONTHLY_OBLIGATION".equals(key)) {
            return isIncomeDefaulted(ctx);
        }
        return (gapActive || demoActive) && GAP_DEFAULTED_KEYS.contains(key);
    }

    private static boolean manualKeysContains(Set<String> manualKeys, String key) {
        if (manualKeys == null || manualKeys.isEmpty() || key == null) {
            return false;
        }
        if (manualKeys.contains(key)) {
            return true;
        }
        // camelCase variants used in creditControl.manual
        String camel = toCamel(key);
        return manualKeys.contains(camel);
    }

    private static String toCamel(String key) {
        if (key == null || !key.contains("_")) {
            return key;
        }
        String[] parts = key.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)));
                if (parts[i].length() > 1) {
                    sb.append(parts[i].substring(1));
                }
            }
        }
        return sb.toString();
    }

    private static BigDecimal firstPresent(Map<String, BigDecimal> sc, String... keys) {
        for (String k : keys) {
            if (sc.get(k) != null) {
                return sc.get(k);
            }
        }
        return null;
    }

    private static Map<String, Object> meta(
            String originService, String originField, boolean defaulted, boolean originalMissing) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("originService", originService);
        m.put("originField", originField);
        m.put("defaulted", defaulted);
        m.put("originalMissing", originalMissing);
        return m;
    }

    private void emitReconciliationFacts(
            LoanApplication app,
            UUID snapshotId,
            List<PendingFact> pending,
            Map<String, UUID> sourceIds) {
        if (app == null || !reconciliationIngestionService.isEnabledFor(app)) {
            return;
        }
        var orchOpt = reconciliationIngestionService.runForShadow(app.getId(), snapshotId, null);
        if (orchOpt.isEmpty()) {
            return;
        }
        ReconciliationOrchestrator.OrchestrationResult orch = orchOpt.get();
        List<CiReconciliationResult> results = orch.results() != null ? orch.results() : List.of();
        Map<String, CiReconciliationResult> byCode = new LinkedHashMap<>();
        for (CiReconciliationResult r : results) {
            byCode.putIfAbsent(r.getReconciliationCode(), r);
        }

        UUID reconSource = sourceIds.values().stream().findFirst().orElse(null);
        List<UUID> src = reconSource != null ? List.of(reconSource) : List.of();

        emitReconPair(pending, src, byCode.get(ReconciliationConstants.XSRC_GST_ITR_TURNOVER),
                "reconciliation.gst_itr_turnover");
        emitReconPair(pending, src, byCode.get(ReconciliationConstants.XSRC_GST_BANK_TURNOVER),
                "reconciliation.gst_bank_turnover");
        emitReconPair(pending, src, byCode.get(ReconciliationConstants.XSRC_ITR_BANK_TURNOVER),
                "reconciliation.itr_bank_turnover");
        emitReconPair(pending, src, byCode.get(ReconciliationConstants.XSRC_BUREAU_BANK_OBLIGATION),
                "reconciliation.bureau_bank_obligation");

        CiReconciliationResult declaredBureau = byCode.get(ReconciliationConstants.XSRC_DECLARED_BUREAU_OBLIGATION);
        if (declaredBureau != null) {
            Map<String, Object> meta = meta("ReconciliationOrchestrator", declaredBureau.getReconciliationCode(), false, false);
            meta.put("reconciliationResultId", declaredBureau.getId() != null ? declaredBureau.getId().toString() : null);
            pending.add(fact("reconciliation.declared_bureau_obligation.outcome", "STRING",
                    declaredBureau.getOutcome(), FactClassification.RECONCILED.name(), src, meta));
        }
        CiReconciliationResult declaredBank = byCode.get(ReconciliationConstants.XSRC_DECLARED_BANK_OBLIGATION);
        if (declaredBank != null) {
            Map<String, Object> meta = meta("ReconciliationOrchestrator", declaredBank.getReconciliationCode(), false, false);
            meta.put("reconciliationResultId", declaredBank.getId() != null ? declaredBank.getId().toString() : null);
            pending.add(fact("reconciliation.declared_bank_obligation.outcome", "STRING",
                    declaredBank.getOutcome(), FactClassification.RECONCILED.name(), src, meta));
        }

        CiReconciliationResult tri = byCode.get(ReconciliationConstants.TURNOVER_TRIANGULATION);
        if (tri != null) {
            Map<String, Object> meta = meta("TurnoverTriangulationService",
                    ReconciliationConstants.TURNOVER_TRIANGULATION, false, false);
            meta.put("reconciliationResultId", tri.getId() != null ? tri.getId().toString() : null);
            Object status = tri.getMetadata() != null ? tri.getMetadata().get("status") : tri.getOutcome();
            pending.add(fact("reconciliation.turnover_triangulation.status", "STRING",
                    status, FactClassification.RECONCILED.name(), src, meta));
            pending.add(fact("reconciliation.turnover_triangulation.confidence", "DECIMAL",
                    tri.getConfidence(), FactClassification.RECONCILED.name(), src, meta));
        }

        CiCreditEvidenceSummary summary = orch.evidenceSummary();
        if (summary != null && properties.getReconciliation().getEvidenceStrength() != null
                && properties.getReconciliation().getEvidenceStrength().isEnabled()) {
            Map<String, Object> meta = meta("EvidenceStrengthScorer",
                    ReconciliationConstants.METRIC_EVIDENCE_STRENGTH, false, false);
            meta.put("notCreditRiskScore", true);
            pending.add(fact("credit.evidence_strength_score", "DECIMAL",
                    summary.getEvidenceStrengthScore(), FactClassification.RECONCILED.name(), src, meta));
            pending.add(fact("credit.evidence_strength_grade", "STRING",
                    summary.getEvidenceStrengthGrade(), FactClassification.RECONCILED.name(), src, meta));
        } else if (summary != null) {
            // Still emit when summary exists even if strength flag off — score is informational in summary
            Map<String, Object> meta = meta("EvidenceStrengthScorer",
                    ReconciliationConstants.METRIC_EVIDENCE_STRENGTH, false, false);
            meta.put("notCreditRiskScore", true);
            meta.put("evidenceStrengthFlag", false);
            pending.add(fact("credit.evidence_strength_score", "DECIMAL",
                    summary.getEvidenceStrengthScore(), FactClassification.RECONCILED.name(), src, meta));
            pending.add(fact("credit.evidence_strength_grade", "STRING",
                    summary.getEvidenceStrengthGrade(), FactClassification.RECONCILED.name(), src, meta));
        }
    }

    private void emitReconPair(
            List<PendingFact> pending,
            List<UUID> src,
            CiReconciliationResult r,
            String pathPrefix) {
        if (r == null) {
            return;
        }
        Map<String, Object> meta = meta("ReconciliationOrchestrator", r.getReconciliationCode(), false, false);
        meta.put("reconciliationResultId", r.getId() != null ? r.getId().toString() : null);
        meta.put("dataStatus", r.getDataStatus());
        pending.add(fact(pathPrefix + ".outcome", "STRING", r.getOutcome(),
                FactClassification.RECONCILED.name(), src, meta));
        pending.add(fact(pathPrefix + ".variance_pct", "DECIMAL", r.getPercentageVariance(),
                FactClassification.RECONCILED.name(), src, meta));
    }

    private static PendingFact fact(
            String path, String valueType, Object value, String classification,
            List<UUID> sourceIds, Map<String, Object> metadata) {
        return new PendingFact(path, valueType, value, classification, sourceIds, metadata);
    }

    /**
     * Persist fact values as {@code Map} JSON envelopes so Hibernate can serialize them.
     * Scalars become {@code {"v": ...}}; existing maps are preserved (copied).
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> wrapValue(Object value) {
        if (value == null) {
            Map<String, Object> wrapped = new HashMap<>();
            wrapped.put("v", null);
            return wrapped;
        }
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> copy = new HashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() != null) {
                    copy.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
            return copy;
        }
        Map<String, Object> wrapped = new HashMap<>();
        if (value instanceof BigDecimal bd) {
            wrapped.put("v", bd.toPlainString());
        } else {
            wrapped.put("v", value);
        }
        return wrapped;
    }

    private record PendingFact(
            String path,
            String valueType,
            Object value,
            String classification,
            List<UUID> sourceRecordIds,
            Map<String, Object> metadata) {
    }
}
