package com.los.core.creditintelligence.policystudio.runtime.canonicalshadow;

import com.los.core.creditintelligence.bureau.domain.CiBureauInquiry;
import com.los.core.creditintelligence.bureau.domain.CiBureauPaymentHistory;
import com.los.core.creditintelligence.bureau.domain.CiBureauReport;
import com.los.core.creditintelligence.bureau.domain.CiBureauTradeline;
import com.los.core.creditintelligence.bureau.repository.CiBureauInquiryRepository;
import com.los.core.creditintelligence.bureau.repository.CiBureauPaymentHistoryRepository;
import com.los.core.creditintelligence.bureau.repository.CiBureauReportRepository;
import com.los.core.creditintelligence.bureau.repository.CiBureauReportSummaryRepository;
import com.los.core.creditintelligence.bureau.repository.CiBureauScoringElementRepository;
import com.los.core.creditintelligence.bureau.repository.CiBureauTradelineRepository;
import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalFactMaterializer;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode;
import com.los.core.creditintelligence.policystudio.parameters.execution.PersistedDerivedMetricSpine;
import com.los.core.creditintelligence.policystudio.parameters.manualoverride.ManualParameterOverrideSpine;
import com.los.core.creditintelligence.policystudio.runtime.canonicalconfig.CanonicalApplicationConfiguration;
import com.los.core.creditintelligence.policystudio.runtime.canonicalconfig.CanonicalCalculationPin;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds CPES EvaluationContext exclusively from a frozen CanonicalApplicationConfiguration.
 * Never looks up latest bureau report / workflow / policy / calculation definition.
 */
@Component
@RequiredArgsConstructor
public class CanonicalShadowContextFactory {

    private final CiBureauReportRepository bureauReportRepository;
    private final CiBureauTradelineRepository tradelineRepository;
    private final CiBureauPaymentHistoryRepository paymentHistoryRepository;
    private final CiBureauInquiryRepository inquiryRepository;
    private final CiBureauReportSummaryRepository reportSummaryRepository;
    private final CiBureauScoringElementRepository scoringElementRepository;
    private final PersistedDerivedMetricSpine persistedDerivedMetricSpine;
    private final ManualParameterOverrideSpine manualParameterOverrideSpine;

    public EvaluationContext build(CanonicalApplicationConfiguration freeze, UUID tenantId) {
        if (freeze == null || freeze.evaluationAsOf() == null) {
            throw new IllegalArgumentException("Frozen package requires explicit evaluationAsOf");
        }
        CanonicalFactMaterializer.CollectionBundle collections = loadExactBureau(freeze.bureauReportId());
        Map<String, Object> facts = CanonicalFactMaterializer.materializeExecutionFacts(Map.of(), collections);

        EvaluationContext.Builder b = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING)
                .applicationId(freeze.applicationId())
                .documentId(freeze.policyDocumentId())
                .tenantId(tenantId)
                .evaluationAsOf(freeze.evaluationAsOf());
        facts.forEach(b::fact);

        Map<String, Object> extras = CanonicalFactMaterializer.provenanceEntityExtras(
                null, null, collections);
        extras.put("forbidLatestFor", true);
        extras.put("canonicalShadow", true);
        extras.put("liveDecisionAuthorityUnchanged", true);
        extras.put(CanonicalFactMaterializer.ENTITY_BUREAU_REPORT_ID,
                freeze.bureauReportId() == null ? null : freeze.bureauReportId().toString());
        Map<String, String> pinnedCalcs = new LinkedHashMap<>();
        Map<String, String> pinnedCalcIds = new LinkedHashMap<>();
        for (CanonicalCalculationPin pin : freeze.calculationDefinitionPins()) {
            if (pin.parameterId() == null) {
                continue;
            }
            if (pin.calculationDefinitionVersion() != null) {
                pinnedCalcs.put(pin.parameterId(), String.valueOf(pin.calculationDefinitionVersion()));
            }
            if (pin.calculationDefinitionId() != null) {
                pinnedCalcIds.put(pin.parameterId(), pin.calculationDefinitionId().toString());
            }
        }
        extras.put("pinnedCalculationDefinitions", pinnedCalcs);
        extras.put("pinnedCalculationDefinitionIds", pinnedCalcIds);
        extras.forEach((k, v) -> {
            if (v != null) {
                b.entity(k, v);
            }
        });
        // Bind last so provenance extras cannot overwrite persisted derived maps.
        persistedDerivedMetricSpine.bindExactReport(b, freeze.bureauReportId());
        manualParameterOverrideSpine.bindExactApplication(b, freeze.applicationId());
        return b.build();
    }

    private CanonicalFactMaterializer.CollectionBundle loadExactBureau(UUID bureauReportId) {
        if (bureauReportId == null) {
            return CanonicalFactMaterializer.fromBureauEntities(null, List.of(), List.of(), List.of());
        }
        CiBureauReport report = bureauReportRepository.findById(bureauReportId).orElse(null);
        if (report == null) {
            return CanonicalFactMaterializer.fromBureauEntities(null, List.of(), List.of(), List.of());
        }
        List<CiBureauTradeline> tradelines = tradelineRepository.findByBureauReportId(report.getId());
        List<CiBureauPaymentHistory> histories = new ArrayList<>();
        for (CiBureauTradeline t : tradelines) {
            if (t.getId() != null) {
                histories.addAll(paymentHistoryRepository.findByTradelineIdOrderByMonthDesc(t.getId()));
            }
        }
        List<CiBureauInquiry> inquiries = inquiryRepository.findByBureauReportId(report.getId());
        var summary = reportSummaryRepository.findById(report.getId()).orElse(null);
        var scoring = scoringElementRepository.findByBureauReportIdOrderBySeqNoAsc(report.getId());
        return CanonicalFactMaterializer.fromBureauEntities(
                report, tradelines, histories, inquiries, summary, scoring);
    }
}
