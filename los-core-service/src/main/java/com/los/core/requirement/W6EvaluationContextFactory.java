package com.los.core.requirement;

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
import com.los.core.creditintelligence.domain.CiFactSnapshot;
import com.los.core.creditintelligence.domain.CiUnderwritingFact;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;
import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalFactMaterializer;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode;
import com.los.core.creditintelligence.repository.CiFactSnapshotRepository;
import com.los.core.creditintelligence.repository.CiUnderwritingFactRepository;
import com.los.core.service.underwriting.ScorecardCanonicalFactorMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds the shared spine {@link EvaluationContext} after W6 source acquisition.
 * Wave-3: projects exact GACAT IDs, materializes collections, does not use wall-clock asOf.
 */
@Component
@RequiredArgsConstructor
public class W6EvaluationContextFactory {

    private final CiFactSnapshotRepository snapshotRepository;
    private final CiUnderwritingFactRepository factRepository;
    private final CiBureauReportRepository bureauReportRepository;
    private final CiBureauTradelineRepository tradelineRepository;
    private final CiBureauPaymentHistoryRepository paymentHistoryRepository;
    private final CiBureauInquiryRepository inquiryRepository;
    private final CiBureauReportSummaryRepository reportSummaryRepository;
    private final CiBureauScoringElementRepository scoringElementRepository;

    public EvaluationContext build(
            UUID applicationId,
            RequirementItemEntity item,
            AcquisitionDtos.ExecutorOutcome outcome) {
        return build(applicationId, item, outcome, null);
    }

    /**
     * @param evaluationAsOf explicit business date; when null, bureau report date is used if present.
     *                       Never falls back to {@code LocalDate.now()}.
     */
    public EvaluationContext build(
            UUID applicationId,
            RequirementItemEntity item,
            AcquisitionDtos.ExecutorOutcome outcome,
            LocalDate evaluationAsOf) {
        Optional<CiFactSnapshot> snap = applicationId == null
                ? Optional.empty()
                : snapshotRepository.findTopByApplicationIdOrderBySnapshotVersionDesc(applicationId);

        Map<String, Object> remapped = loadSnapshotFactsRaw(snap);
        overlayOutcome(remapped, outcome);
        overlayHints(remapped, item);

        CanonicalFactMaterializer.CollectionBundle collections = loadCollections(applicationId);
        Map<String, Object> facts = CanonicalFactMaterializer.materializeExecutionFacts(remapped, collections);

        CiBureauReport report = null;
        if (collections.provenance() != null && collections.provenance().get("bureauReportId") != null) {
            try {
                report = bureauReportRepository.findById(
                        UUID.fromString(String.valueOf(collections.provenance().get("bureauReportId"))))
                        .orElse(null);
            } catch (Exception ignored) {
                report = null;
            }
        }
        LocalDate asOf = CanonicalFactMaterializer.resolveEvaluationAsOf(evaluationAsOf, report, facts);

        EvaluationContext.Builder b = EvaluationContext.builder()
                .mode(EvaluationMode.W6_ACQUISITION)
                .applicationId(applicationId)
                .evaluationAsOf(asOf);
        facts.forEach(b::fact);
        Map<String, Object> extras = CanonicalFactMaterializer.provenanceEntityExtras(
                snap.map(CiFactSnapshot::getId).orElse(null),
                snap.map(CiFactSnapshot::getSnapshotVersion).orElse(null),
                collections);
        extras.put("acquisitionSuccessDoesNotImplyValueAvailable", true);
        if (asOf == null) {
            extras.put("evaluationAsOfMissing", true);
            extras.put("evaluationAsOfGap",
                    "No explicit asOf, reportDate, or payment_history month — asOf left null");
        } else if (evaluationAsOf == null
                && (report == null || report.getReportDate() == null)
                && CanonicalFactMaterializer.deriveAsOfFromPaymentHistory(facts) != null) {
            extras.put("evaluationAsOfDerivedFromPaymentHistory", true);
        }
        extras.forEach(b::entity);
        return b.build();
    }

    /** All acceptable snapshot facts as stored path → unwrapped value (may be remapped). */
    public Map<String, Object> loadSnapshotFacts(UUID applicationId) {
        Optional<CiFactSnapshot> snap = applicationId == null
                ? Optional.empty()
                : snapshotRepository.findTopByApplicationIdOrderBySnapshotVersionDesc(applicationId);
        Map<String, Object> remapped = loadSnapshotFactsRaw(snap);
        CanonicalFactMaterializer.CollectionBundle collections = loadCollections(applicationId);
        return CanonicalFactMaterializer.materializeExecutionFacts(remapped, collections);
    }

    private Map<String, Object> loadSnapshotFactsRaw(Optional<CiFactSnapshot> snap) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (snap.isEmpty()) {
            return out;
        }
        List<CiUnderwritingFact> rows = factRepository.findBySnapshotId(snap.get().getId());
        for (CiUnderwritingFact f : rows) {
            if (f.getCanonicalPath() == null || f.getCanonicalPath().isBlank()) {
                continue;
            }
            if (!isAcceptable(f)) {
                continue;
            }
            Object v = unwrap(f.getValue());
            if (v != null) {
                out.put(f.getCanonicalPath().trim(), v);
            }
        }
        return out;
    }

    private CanonicalFactMaterializer.CollectionBundle loadCollections(UUID applicationId) {
        if (applicationId == null) {
            return CanonicalFactMaterializer.fromBureauEntities(null, List.of(), List.of(), List.of());
        }
        Optional<CiBureauReport> reportOpt =
                bureauReportRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId);
        if (reportOpt.isEmpty()) {
            // Try any latest report without application linkage helpers
            return CanonicalFactMaterializer.fromBureauEntities(null, List.of(), List.of(), List.of());
        }
        CiBureauReport report = reportOpt.get();
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

    /**
     * Resolve requirement item parameter to a GACAT canonical ID, or empty if not in catalogue
     * (legacy fixture keys remain on the boolean factReadiness path).
     */
    public static Optional<String> resolveGacatId(String parameterOrLegacyKey) {
        if (parameterOrLegacyKey == null || parameterOrLegacyKey.isBlank()) {
            return Optional.empty();
        }
        String raw = parameterOrLegacyKey.trim();
        CanonicalParameterRegistry registry = PolicyStudioConvergencePresenter.registry();
        Optional<com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition> byId =
                registry.findById(raw);
        if (byId.isPresent() && raw.equals(byId.get().id())) {
            return Optional.of(byId.get().id());
        }
        ScorecardCanonicalFactorMapper.Binding bind = ScorecardCanonicalFactorMapper.resolve(raw);
        if (bind.canonicalParameterId() != null
                && (ScorecardCanonicalFactorMapper.EXACT.equals(bind.mappingStatus())
                || ScorecardCanonicalFactorMapper.SAFE_ALIAS.equals(bind.mappingStatus()))) {
            return Optional.of(bind.canonicalParameterId());
        }
        return Optional.empty();
    }

    /**
     * Acquisition adapters must not mark GACAT parameters ready from source success alone.
     * Legacy fixture keys may still advertise boolean readiness for W6 goldens.
     */
    public static Map<String, Boolean> acquisitionClaim(String parameterOrLegacyKey, boolean sourceMaterialPresent) {
        if (parameterOrLegacyKey == null || parameterOrLegacyKey.isBlank()) {
            return Map.of();
        }
        if (resolveGacatId(parameterOrLegacyKey).isPresent()) {
            return Map.of(parameterOrLegacyKey, false);
        }
        return Map.of(parameterOrLegacyKey, sourceMaterialPresent);
    }

    private static void overlayOutcome(Map<String, Object> facts, AcquisitionDtos.ExecutorOutcome outcome) {
        if (outcome == null || outcome.resultSummary() == null) {
            return;
        }
        Map<String, Object> s = outcome.resultSummary();
        Object score = s.get("creditScore");
        if (score == null) {
            score = s.get("bureau.score");
        }
        if (score != null && !Boolean.FALSE.equals(s.get("scorePresent"))) {
            facts.put("bureau.score", score);
        }
        Object metrics = s.get("canonicalMetrics");
        if (metrics instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    String k = String.valueOf(e.getKey()).trim();
                    if (k.contains(".")) {
                        facts.put(k, e.getValue());
                    }
                }
            }
        }
        Object derived = s.get("derivedValue");
        Object target = s.get("canonicalParameterId");
        if (derived != null && target != null) {
            facts.put(String.valueOf(target), derived);
        }
    }

    @SuppressWarnings("unchecked")
    private static void overlayHints(Map<String, Object> facts, RequirementItemEntity item) {
        if (item == null || item.getSourceHints() == null) {
            return;
        }
        Map<String, Object> hints = item.getSourceHints();
        if (hints.get("inputs") instanceof Map<?, ?> inputs) {
            for (Map.Entry<?, ?> e : inputs.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    String k = String.valueOf(e.getKey()).trim();
                    if (k.contains(".")) {
                        facts.put(k, e.getValue());
                    }
                }
            }
        }
        if (hints.get("extractedParameters") instanceof Map<?, ?> extracted) {
            for (Map.Entry<?, ?> e : extracted.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    String k = String.valueOf(e.getKey()).trim();
                    if (k.contains(".")) {
                        facts.put(k, e.getValue());
                    }
                }
            }
        }
        Object derived = hints.get("derivedValue");
        if (derived != null && item.getCanonicalParameterId() != null
                && item.getCanonicalParameterId().contains(".")) {
            facts.put(item.getCanonicalParameterId(), derived);
        }
    }

    private static boolean isAcceptable(CiUnderwritingFact f) {
        String q = f.getQualityStatus() == null ? "" : f.getQualityStatus().toUpperCase();
        if ("FAILED".equals(q) || "REJECTED".equals(q) || "INVALID".equals(q)) {
            return false;
        }
        return !"DATA_INSUFFICIENT".equalsIgnoreCase(f.getClassification());
    }

    private static Object unwrap(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        if (value.containsKey("v")) {
            return value.get("v");
        }
        if (value.size() == 1) {
            return value.values().iterator().next();
        }
        return value;
    }
}
