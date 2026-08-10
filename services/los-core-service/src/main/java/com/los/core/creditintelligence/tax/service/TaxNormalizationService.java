package com.los.core.creditintelligence.tax.service;

import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.domain.SourceType;
import com.los.core.creditintelligence.service.SourceRegistryService;
import com.los.core.creditintelligence.support.ContentHasher;
import com.los.core.creditintelligence.tax.domain.CiAisInformation;
import com.los.core.creditintelligence.tax.domain.CiAisSummary;
import com.los.core.creditintelligence.tax.domain.CiForm26AsEntry;
import com.los.core.creditintelligence.tax.domain.CiForm26AsSummary;
import com.los.core.creditintelligence.tax.domain.CiItrBusinessFinancials;
import com.los.core.creditintelligence.tax.domain.CiItrIncome;
import com.los.core.creditintelligence.tax.domain.CiItrPresumptiveIncome;
import com.los.core.creditintelligence.tax.domain.CiItrReturn;
import com.los.core.creditintelligence.tax.domain.CiItrTaxSummary;
import com.los.core.creditintelligence.tax.domain.CiTaxReturnRevision;
import com.los.core.creditintelligence.tax.domain.SubjectScope;
import com.los.core.creditintelligence.tax.domain.TaxConstants;
import com.los.core.creditintelligence.tax.provider.KarzaItrCanonicalExtractor;
import com.los.core.creditintelligence.tax.repository.CiAisInformationRepository;
import com.los.core.creditintelligence.tax.repository.CiAisSummaryRepository;
import com.los.core.creditintelligence.tax.repository.CiForm26AsEntryRepository;
import com.los.core.creditintelligence.tax.repository.CiForm26AsSummaryRepository;
import com.los.core.creditintelligence.tax.repository.CiItrBusinessFinancialsRepository;
import com.los.core.creditintelligence.tax.repository.CiItrIncomeRepository;
import com.los.core.creditintelligence.tax.repository.CiItrPresumptiveIncomeRepository;
import com.los.core.creditintelligence.tax.repository.CiItrReturnRepository;
import com.los.core.creditintelligence.tax.repository.CiItrTaxSummaryRepository;
import com.los.core.creditintelligence.tax.repository.CiTaxReturnRevisionRepository;
import com.los.core.creditintelligence.tax.util.ItrEffectiveReturnSelector;
import com.los.core.creditintelligence.tax.util.TaxYearUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Normalizes Karza ITR return-forms into CiItr* / AIS / 26AS tables.
 * Idempotent on tenant|app|pan|ay|requestId|checksum. Never stores raw passwords/payloads.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaxNormalizationService {

    public static final String NORMALIZER_VERSION = TaxConstants.TAX_NORMALIZER_V1;

    private final CiItrReturnRepository returnRepository;
    private final CiItrIncomeRepository incomeRepository;
    private final CiItrBusinessFinancialsRepository businessRepository;
    private final CiItrPresumptiveIncomeRepository presumptiveRepository;
    private final CiItrTaxSummaryRepository taxSummaryRepository;
    private final CiTaxReturnRevisionRepository revisionRepository;
    private final CiAisSummaryRepository aisSummaryRepository;
    private final CiAisInformationRepository aisInformationRepository;
    private final CiForm26AsSummaryRepository form26AsSummaryRepository;
    private final CiForm26AsEntryRepository form26AsEntryRepository;
    private final SourceRegistryService sourceRegistryService;
    private final TaxMetricService metricService;
    private final ContentHasher contentHasher;
    private final CreditIntelligenceProperties properties;

    public record NormalizationResult(
            List<CiItrReturn> returns,
            List<CiMetricResult> metrics,
            boolean alreadyExisted) {
    }

    @Transactional
    public NormalizationResult normalize(
            UUID applicationId,
            UUID tenantId,
            Map<String, Object> parsedData,
            String requestId,
            UUID kycStepResultId) {

        UUID tid = tenantId != null ? tenantId : properties.getDefaultTenantId();
        Map<String, Object> data = parsedData != null ? parsedData : Map.of();

        KarzaItrCanonicalExtractor.ExtractionResult extracted = KarzaItrCanonicalExtractor.extract(data);
        String panKey = extracted.returns().stream()
                .map(KarzaItrCanonicalExtractor.ExtractedReturn::panLast4)
                .filter(p -> p != null && !p.isBlank())
                .findFirst()
                .orElse("UNKNOWN");

        String checksum = contentHasher.hashMap(checksumPayload(data, requestId));
        String pullIdem = String.join("|",
                tid.toString(),
                applicationId.toString(),
                panKey,
                requestId != null ? requestId : "",
                checksum);

        Optional<CiItrReturn> existingPull = returnRepository
                .findByTenantIdAndApplicationIdAndIdempotencyKey(tid, applicationId, pullIdem + "|PULL");
        if (existingPull.isPresent()) {
            List<CiItrReturn> returns =
                    returnRepository.findByApplicationIdOrderByAssessmentYearDescCreatedAtDesc(applicationId);
            return new NormalizationResult(returns, metricService.findForApplication(applicationId), true);
        }

        var source = sourceRegistryService.createOrGet(
                tid, applicationId, SourceType.ITR.name(), "KARZA",
                "UNDERWRITING",
                "itr-canon-" + pullIdem,
                Map.of(
                        "requestId", requestId != null ? requestId : "",
                        "parserVersion", KarzaItrCanonicalExtractor.PARSER_VERSION,
                        "referenceOnly", true,
                        "panLast4", panKey),
                "TaxNormalizationService");

        String contentRef = kycStepResultId != null
                ? "kyc_step_result:" + kycStepResultId
                : "itr_request:" + (requestId != null ? requestId : "unknown");
        sourceRegistryService.createArtifact(source.getId(), contentRef, "application/json", checksum);

        boolean persistDetail = properties.getCanonicalization().getTax() == null
                || properties.getCanonicalization().getTax().isPersistDetail();

        // Group extracted returns by AY for effective selection
        Map<String, List<KarzaItrCanonicalExtractor.ExtractedReturn>> byAy = extracted.returns().stream()
                .filter(r -> r.assessmentYear() != null)
                .collect(Collectors.groupingBy(
                        r -> TaxYearUtils.normalizeYearLabel(r.assessmentYear()).orElse(r.assessmentYear()),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<CiItrReturn> savedReturns = new ArrayList<>();
        List<TaxMetricService.ReturnBundle> bundles = new ArrayList<>();
        Map<String, UUID> effectiveByAy = new HashMap<>();

        for (Map.Entry<String, List<KarzaItrCanonicalExtractor.ExtractedReturn>> entry : byAy.entrySet()) {
            String ay = entry.getKey();
            List<KarzaItrCanonicalExtractor.ExtractedReturn> group = entry.getValue();

            List<ItrEffectiveReturnSelector.Candidate> candidates = new ArrayList<>();
            for (int i = 0; i < group.size(); i++) {
                var er = group.get(i);
                candidates.add(new ItrEffectiveReturnSelector.Candidate(
                        ay, er.returnVersionType(), er.filingDate(),
                        "idx-" + i, er.metadata() != null ? er.metadata() : Map.of()));
            }
            var selection = ItrEffectiveReturnSelector.select(candidates);
            int effectiveIdx = 0;
            if (selection.effective() != null && selection.effective().identityKey() != null) {
                String key = selection.effective().identityKey();
                if (key.startsWith("idx-")) {
                    try {
                        effectiveIdx = Integer.parseInt(key.substring(4));
                    } catch (Exception ignored) {
                        effectiveIdx = 0;
                    }
                }
            }

            // Clear prior effective for this AY/scope if re-ingesting different pull
            returnRepository.findByApplicationIdAndSubjectScopeAndAssessmentYearAndEffectiveTrue(
                            applicationId, SubjectScope.BORROWER_ENTITY.name(), ay)
                    .ifPresent(prev -> {
                        prev.setEffective(false);
                        returnRepository.save(prev);
                    });

            List<CiItrReturn> aySaved = new ArrayList<>();
            for (int i = 0; i < group.size(); i++) {
                var er = group.get(i);
                String returnIdem = String.join("|", pullIdem, ay, String.valueOf(i));
                Optional<CiItrReturn> already = returnRepository
                        .findByTenantIdAndApplicationIdAndIdempotencyKey(tid, applicationId, returnIdem);
                if (already.isPresent()) {
                    aySaved.add(already.get());
                    continue;
                }

                Map<String, Object> meta = new LinkedHashMap<>();
                if (er.metadata() != null) {
                    meta.putAll(er.metadata());
                }
                meta.put("checksum", checksum);
                meta.put("requestId", requestId);

                CiItrReturn saved = returnRepository.save(CiItrReturn.builder()
                        .tenantId(tid)
                        .applicationId(applicationId)
                        .subjectScope(SubjectScope.BORROWER_ENTITY.name())
                        .sourceRecordId(source.getId())
                        .panHash(er.panHash())
                        .panLast4(er.panLast4())
                        .assessmentYear(ay)
                        .financialYear(er.financialYear())
                        .itrForm(er.itrForm() != null ? er.itrForm().name() : "UNKNOWN")
                        .filingDate(er.filingDate())
                        .filingSection(er.filingSection())
                        .ackReferenceMasked(er.ackMasked())
                        .filingStatus(er.filingStatus() != null ? er.filingStatus() : "UNKNOWN")
                        .returnVersionType(er.returnVersionType() != null
                                ? er.returnVersionType().name() : "ORIGINAL")
                        .effective(i == effectiveIdx)
                        .qualityStatus("OK")
                        .parserVersion(KarzaItrCanonicalExtractor.PARSER_VERSION)
                        .normalizerVersion(NORMALIZER_VERSION)
                        .idempotencyKey(returnIdem)
                        .sourceReference(contentRef)
                        .metadata(meta)
                        .build());
                aySaved.add(saved);

                if (persistDetail) {
                    TaxMetricService.ReturnBundle bundle = persistDetail(saved, er, contentRef);
                    if (saved.isEffective()) {
                        bundles.add(bundle);
                        effectiveByAy.put(ay, saved.getId());
                    }
                } else if (saved.isEffective()) {
                    bundles.add(new TaxMetricService.ReturnBundle(saved, null, null, List.of(), null));
                    effectiveByAy.put(ay, saved.getId());
                }
            }
            savedReturns.addAll(aySaved);

            if (!aySaved.isEmpty() && selection.effective() != null) {
                UUID effectiveId = effectiveByAy.getOrDefault(ay, aySaved.get(Math.min(effectiveIdx, aySaved.size() - 1)).getId());
                UUID originalId = aySaved.get(aySaved.size() - 1).getId();
                revisionRepository.save(CiTaxReturnRevision.builder()
                        .tenantId(tid)
                        .applicationId(applicationId)
                        .assessmentYear(ay)
                        .originalReturnId(originalId)
                        .effectiveReturnId(effectiveId)
                        .selectionBasis(selection.selectionBasis())
                        .selectionVersion(TaxConstants.ITR_EFFECTIVE_RETURN_SELECTION_V1)
                        .sourceReferences(List.of(contentRef))
                        .metadata(selection.evidence())
                        .build());
            }
        }

        // Marker row for pull-level idempotency when no AY returns
        if (savedReturns.isEmpty()) {
            CiItrReturn marker = returnRepository.save(CiItrReturn.builder()
                    .tenantId(tid)
                    .applicationId(applicationId)
                    .subjectScope(SubjectScope.BORROWER_ENTITY.name())
                    .sourceRecordId(source.getId())
                    .panLast4(panKey)
                    .assessmentYear("UNKNOWN")
                    .itrForm("UNKNOWN")
                    .filingStatus("UNKNOWN")
                    .returnVersionType("UNKNOWN")
                    .effective(false)
                    .qualityStatus("EMPTY")
                    .parserVersion(KarzaItrCanonicalExtractor.PARSER_VERSION)
                    .normalizerVersion(NORMALIZER_VERSION)
                    .idempotencyKey(pullIdem + "|PULL")
                    .sourceReference(contentRef)
                    .metadata(Map.of("emptyExtraction", true, "checksum", checksum))
                    .build());
            savedReturns.add(marker);
        } else {
            // Store pull marker without conflicting unique effective index
            returnRepository.save(CiItrReturn.builder()
                    .tenantId(tid)
                    .applicationId(applicationId)
                    .subjectScope(SubjectScope.BORROWER_ENTITY.name())
                    .sourceRecordId(source.getId())
                    .panLast4(panKey)
                    .assessmentYear("PULL")
                    .itrForm("UNKNOWN")
                    .filingStatus("MARKER")
                    .returnVersionType("UNKNOWN")
                    .effective(false)
                    .qualityStatus("OK")
                    .parserVersion(KarzaItrCanonicalExtractor.PARSER_VERSION)
                    .normalizerVersion(NORMALIZER_VERSION)
                    .idempotencyKey(pullIdem + "|PULL")
                    .sourceReference(contentRef)
                    .metadata(Map.of("checksum", checksum, "returnCount", savedReturns.size()))
                    .build());
        }

        List<CiAisSummary> aisSummaries = new ArrayList<>();
        List<CiForm26AsSummary> form26Summaries = new ArrayList<>();
        if (persistDetail && extracted.aisAvailable()) {
            aisSummaries.addAll(persistAis(tid, applicationId, extracted, pullIdem, contentRef, checksum, requestId));
        }
        if (persistDetail && extracted.form26AsAvailable()) {
            form26Summaries.addAll(persistForm26As(tid, applicationId, extracted, pullIdem, contentRef, checksum, requestId));
        }

        List<CiMetricResult> metrics = metricService.computeAndPersist(
                tid, applicationId, source.getId(), bundles,
                new TaxMetricService.CrossSourceContext(aisSummaries, form26Summaries),
                LocalDate.now());

        log.info("Tax canonicalization normalized applicationId={} returns={} ais={} form26as={}",
                applicationId, savedReturns.size(), aisSummaries.size(), form26Summaries.size());
        return new NormalizationResult(savedReturns, metrics, false);
    }

    private TaxMetricService.ReturnBundle persistDetail(
            CiItrReturn saved, KarzaItrCanonicalExtractor.ExtractedReturn er, String contentRef) {
        CiItrIncome income = null;
        if (er.income() != null) {
            income = incomeRepository.save(CiItrIncome.builder()
                    .itrReturnId(saved.getId())
                    .salaryIncome(er.income().salaryIncome())
                    .housePropertyIncome(er.income().housePropertyIncome())
                    .businessProfessionIncome(er.income().businessProfessionIncome())
                    .capitalGains(er.income().capitalGains())
                    .otherSources(er.income().otherSources())
                    .grossTotalIncome(er.income().grossTotalIncome())
                    .totalIncome(er.income().totalIncome())
                    .extractionQuality("OK")
                    .sourceReference(contentRef)
                    .metadata(er.income().metadata() != null ? er.income().metadata() : Map.of())
                    .build());
        }

        CiItrBusinessFinancials business = null;
        if (er.business() != null) {
            business = businessRepository.save(CiItrBusinessFinancials.builder()
                    .itrReturnId(saved.getId())
                    .grossReceipts(er.business().grossReceipts())
                    .salesTurnover(er.business().salesTurnover())
                    .grossProfit(er.business().grossProfit())
                    .ebitda(er.business().ebitda())
                    .depreciation(er.business().depreciation())
                    .financeCost(er.business().financeCost())
                    .profitBeforeTax(er.business().profitBeforeTax())
                    .profitAfterTax(er.business().profitAfterTax())
                    .totalLiabilities(er.business().totalLiabilities())
                    .netWorth(er.business().netWorth())
                    .totalAssets(er.business().totalAssets())
                    .totalBorrowings(er.business().totalBorrowings())
                    .extractionQuality(er.business().balanceSheetPresent() ? "OK" : "PARTIAL")
                    .sourceReference(contentRef)
                    .metadata(er.business().metadata() != null ? er.business().metadata() : Map.of())
                    .build());
        }

        List<CiItrPresumptiveIncome> presumptive = new ArrayList<>();
        if (er.presumptiveIncome() != null) {
            presumptive.add(presumptiveRepository.save(CiItrPresumptiveIncome.builder()
                    .itrReturnId(saved.getId())
                    .applicableSection(er.presumptiveIncome().applicableSection())
                    .grossReceipts(er.presumptiveIncome().grossReceipts())
                    .declaredPresumptiveIncome(er.presumptiveIncome().declaredPresumptiveIncome())
                    .declaredMargin(er.presumptiveIncome().declaredMargin())
                    .sourceReference(contentRef)
                    .metadata(er.presumptiveIncome().metadata() != null ? er.presumptiveIncome().metadata() : Map.of())
                    .build()));
        }

        CiItrTaxSummary taxSummary = null;
        if (er.taxSummary() != null) {
            taxSummary = taxSummaryRepository.save(CiItrTaxSummary.builder()
                    .itrReturnId(saved.getId())
                    .taxLiability(er.taxSummary().taxLiability())
                    .taxPayable(er.taxSummary().taxPayable())
                    .taxPaid(er.taxSummary().taxPaid())
                    .tds(er.taxSummary().tds())
                    .tcs(er.taxSummary().tcs())
                    .advanceTax(er.taxSummary().advanceTax())
                    .selfAssessmentTax(er.taxSummary().selfAssessmentTax())
                    .refundClaimed(er.taxSummary().refundClaimed())
                    .outstandingDemand(er.taxSummary().outstandingDemand())
                    .sourceReference(contentRef)
                    .metadata(er.taxSummary().metadata() != null ? er.taxSummary().metadata() : Map.of())
                    .build());
        }

        return new TaxMetricService.ReturnBundle(saved, income, business, presumptive, taxSummary);
    }

    private List<CiAisSummary> persistAis(
            UUID tid, UUID applicationId, KarzaItrCanonicalExtractor.ExtractionResult extracted,
            String pullIdem, String contentRef, String checksum, String requestId) {
        List<CiAisSummary> saved = new ArrayList<>();
        var aisSource = sourceRegistryService.createOrGet(
                tid, applicationId, SourceType.AIS.name(), "KARZA",
                "UNDERWRITING",
                "ais-canon-" + pullIdem,
                Map.of("requestId", requestId != null ? requestId : "", "linkedFrom", "ITR"),
                "TaxNormalizationService");
        sourceRegistryService.createArtifact(aisSource.getId(), contentRef, "application/json", checksum);

        int i = 0;
        for (var ea : extracted.aisSummaries()) {
            String idem = pullIdem + "|AIS|" + i++;
            Optional<CiAisSummary> existing = aisSummaryRepository
                    .findByTenantIdAndApplicationIdAndIdempotencyKey(tid, applicationId, idem);
            if (existing.isPresent()) {
                saved.add(existing.get());
                continue;
            }
            CiAisSummary sum = aisSummaryRepository.save(CiAisSummary.builder()
                    .tenantId(tid)
                    .applicationId(applicationId)
                    .sourceRecordId(aisSource.getId())
                    .financialYear(ea.financialYear() != null ? ea.financialYear() : "UNKNOWN")
                    .totalReportedValue(ea.totalReportedValue())
                    .informationSourceCount(ea.information() != null ? ea.information().size() : 0)
                    .qualityStatus("OK")
                    .parserVersion(KarzaItrCanonicalExtractor.PARSER_VERSION)
                    .normalizerVersion(NORMALIZER_VERSION)
                    .idempotencyKey(idem)
                    .metadata(ea.metadata() != null ? ea.metadata() : Map.of())
                    .build());
            if (ea.information() != null) {
                for (var info : ea.information()) {
                    aisInformationRepository.save(CiAisInformation.builder()
                            .aisSummaryId(sum.getId())
                            .category(info.category())
                            .informationCode(info.informationCode())
                            .reportingEntity(info.reportingEntity())
                            .amount(info.amount())
                            .acceptedValue(info.acceptedValue())
                            .sourceReference(contentRef)
                            .metadata(info.metadata() != null ? info.metadata() : Map.of())
                            .build());
                }
            }
            saved.add(sum);
        }
        return saved;
    }

    private List<CiForm26AsSummary> persistForm26As(
            UUID tid, UUID applicationId, KarzaItrCanonicalExtractor.ExtractionResult extracted,
            String pullIdem, String contentRef, String checksum, String requestId) {
        List<CiForm26AsSummary> saved = new ArrayList<>();
        var src = sourceRegistryService.createOrGet(
                tid, applicationId, SourceType.FORM_26AS.name(), "KARZA",
                "UNDERWRITING",
                "26as-canon-" + pullIdem,
                Map.of("requestId", requestId != null ? requestId : "", "linkedFrom", "ITR"),
                "TaxNormalizationService");
        sourceRegistryService.createArtifact(src.getId(), contentRef, "application/json", checksum);

        int i = 0;
        for (var ef : extracted.form26AsSummaries()) {
            String idem = pullIdem + "|26AS|" + i++;
            Optional<CiForm26AsSummary> existing = form26AsSummaryRepository
                    .findByTenantIdAndApplicationIdAndIdempotencyKey(tid, applicationId, idem);
            if (existing.isPresent()) {
                saved.add(existing.get());
                continue;
            }
            CiForm26AsSummary sum = form26AsSummaryRepository.save(CiForm26AsSummary.builder()
                    .tenantId(tid)
                    .applicationId(applicationId)
                    .sourceRecordId(src.getId())
                    .financialYear(ef.financialYear() != null ? ef.financialYear() : "UNKNOWN")
                    .totalTds(ef.totalTds())
                    .totalTcs(ef.totalTcs())
                    .advanceTax(ef.advanceTax())
                    .selfAssessmentTax(ef.selfAssessmentTax())
                    .refund(ef.refund())
                    .qualityStatus("OK")
                    .parserVersion(KarzaItrCanonicalExtractor.PARSER_VERSION)
                    .normalizerVersion(NORMALIZER_VERSION)
                    .idempotencyKey(idem)
                    .metadata(ef.metadata() != null ? ef.metadata() : Map.of())
                    .build());
            if (ef.entries() != null) {
                for (var e : ef.entries()) {
                    form26AsEntryRepository.save(CiForm26AsEntry.builder()
                            .form26asSummaryId(sum.getId())
                            .sectionCode(e.sectionCode())
                            .deductorName(e.deductorName())
                            .amountPaidCredited(e.amountPaidCredited())
                            .taxDeducted(e.taxDeducted())
                            .taxDeposited(e.taxDeposited())
                            .bookingDate(e.bookingDate())
                            .sourceReference(contentRef)
                            .metadata(e.metadata() != null ? e.metadata() : Map.of())
                            .build());
                }
            }
            saved.add(sum);
        }
        return saved;
    }

    private Map<String, Object> checksumPayload(Map<String, Object> data, String requestId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("requestId", requestId);
        Object result = data.get("result");
        if (result instanceof Map<?, ?> rm) {
            Map<String, Object> safe = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : rm.entrySet()) {
                String k = String.valueOf(e.getKey());
                if ("password".equalsIgnoreCase(k)) {
                    continue;
                }
                safe.put(k, e.getValue());
            }
            m.put("resultKeys", safe.keySet());
            if (safe.get("financialInformation") instanceof List<?> fin) {
                m.put("financialYearCount", fin.size());
            }
            if (safe.get("formDetails") != null) {
                m.put("formDetails", safe.get("formDetails"));
            }
        } else {
            m.put("hasResult", result != null);
        }
        m.put("mappedMetricKeys", data.get("mappedMetrics") instanceof Map<?, ?> mm ? mm.keySet() : List.of());
        return m;
    }
}
