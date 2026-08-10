package com.los.core.creditintelligence.gst.service;

import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.domain.SourceType;
import com.los.core.creditintelligence.gst.domain.CiGstPeriodFinancials;
import com.los.core.creditintelligence.gst.domain.CiGstRegistration;
import com.los.core.creditintelligence.gst.domain.CiGstReturnPeriod;
import com.los.core.creditintelligence.gst.domain.CiGstReturnRevision;
import com.los.core.creditintelligence.gst.provider.KarzaGstCanonicalExtractor;
import com.los.core.creditintelligence.gst.repository.CiGstPeriodFinancialsRepository;
import com.los.core.creditintelligence.gst.repository.CiGstRegistrationRepository;
import com.los.core.creditintelligence.gst.repository.CiGstReturnPeriodRepository;
import com.los.core.creditintelligence.gst.repository.CiGstReturnRevisionRepository;
import com.los.core.creditintelligence.service.SourceRegistryService;
import com.los.core.creditintelligence.support.ContentHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Normalizes Karza GST analysis into CiGst* tables. Idempotent on
 * tenant|app|gstin|requestId|checksum. Never stores raw Karza JSON in fact tables.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GstNormalizationService {

    public static final String NORMALIZER_VERSION = "GST_NORMALIZER_V1";

    private final CiGstRegistrationRepository registrationRepository;
    private final CiGstReturnPeriodRepository periodRepository;
    private final CiGstPeriodFinancialsRepository financialsRepository;
    private final CiGstReturnRevisionRepository revisionRepository;
    private final SourceRegistryService sourceRegistryService;
    private final GstMetricService metricService;
    private final ContentHasher contentHasher;
    private final CreditIntelligenceProperties properties;

    public record NormalizationResult(
            List<CiGstRegistration> registrations,
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

        KarzaGstCanonicalExtractor.ExtractionResult extracted = KarzaGstCanonicalExtractor.extract(data);
        String primaryGstin = extracted.registrations().isEmpty()
                ? str(data.get("gstin"))
                : extracted.registrations().get(0).gstin();
        if (primaryGstin == null || primaryGstin.isBlank()) {
            primaryGstin = "UNKNOWN";
        } else {
            primaryGstin = primaryGstin.toUpperCase(Locale.ROOT);
        }

        String checksum = contentHasher.hashMap(checksumPayload(data, requestId));
        String idempotencyKey = String.join("|",
                tid.toString(),
                applicationId.toString(),
                primaryGstin,
                requestId != null ? requestId : "",
                checksum);

        Optional<CiGstRegistration> existing = registrationRepository
                .findByTenantIdAndApplicationIdAndIdempotencyKey(tid, applicationId, idempotencyKey);
        if (existing.isPresent()) {
            List<CiGstRegistration> regs = registrationRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId);
            List<CiMetricResult> metrics = metricService.findForApplication(applicationId);
            return new NormalizationResult(regs, metrics, true);
        }

        var source = sourceRegistryService.createOrGet(
                tid, applicationId, SourceType.GST.name(), "KARZA",
                "UNDERWRITING",
                "gst-canon-" + idempotencyKey,
                Map.of(
                        "requestId", requestId != null ? requestId : "",
                        "parserVersion", KarzaGstCanonicalExtractor.PARSER_VERSION,
                        "referenceOnly", true),
                "GstNormalizationService");

        String contentRef = kycStepResultId != null
                ? "kyc_step_result:" + kycStepResultId
                : "gst_request:" + (requestId != null ? requestId : "unknown");
        sourceRegistryService.createArtifact(source.getId(), contentRef, "application/json", checksum);

        boolean persistPeriods = properties.getCanonicalization().getGst() == null
                || properties.getCanonicalization().getGst().isPersistPeriods();

        List<CiGstRegistration> savedRegs = new ArrayList<>();
        List<GstMetricService.PeriodBundle> bundles = new ArrayList<>();

        List<KarzaGstCanonicalExtractor.ExtractedRegistration> regs = extracted.registrations();
        if (regs.isEmpty()) {
            regs = List.of(new KarzaGstCanonicalExtractor.ExtractedRegistration(
                    primaryGstin, null, null, "UNKNOWN", null, null, null, Map.of("synthetic", true)));
        }

        for (KarzaGstCanonicalExtractor.ExtractedRegistration er : regs) {
            String gstinKey = er.gstin() != null ? er.gstin().toUpperCase(Locale.ROOT) : primaryGstin;
            String regIdem = String.join("|", tid.toString(), applicationId.toString(), gstinKey,
                    requestId != null ? requestId : "", checksum);

            Optional<CiGstRegistration> already = registrationRepository
                    .findByTenantIdAndApplicationIdAndIdempotencyKey(tid, applicationId, regIdem);
            if (already.isPresent()) {
                CiGstRegistration reg = already.get();
                savedRegs.add(reg);
                bundles.add(loadBundle(reg));
                continue;
            }

            Map<String, Object> meta = new LinkedHashMap<>();
            if (er.metadata() != null) {
                meta.putAll(er.metadata());
            }
            meta.put("checksum", checksum);
            meta.put("requestId", requestId);

            CiGstRegistration reg = registrationRepository.save(CiGstRegistration.builder()
                    .tenantId(tid)
                    .applicationId(applicationId)
                    .sourceRecordId(source.getId())
                    .gstin(gstinKey)
                    .legalName(er.legalName())
                    .tradeName(er.tradeName())
                    .registrationStatus(er.registrationStatus() != null ? er.registrationStatus() : "UNKNOWN")
                    .registrationDate(er.registrationDate())
                    .taxpayerType(er.taxpayerType())
                    .stateCode(er.stateCode())
                    .qualityStatus("OK")
                    .parserVersion(KarzaGstCanonicalExtractor.PARSER_VERSION)
                    .normalizerVersion(NORMALIZER_VERSION)
                    .idempotencyKey(regIdem)
                    .sourceReference(contentRef)
                    .metadata(meta)
                    .build());
            savedRegs.add(reg);

            List<CiGstReturnPeriod> periods = new ArrayList<>();
            Map<UUID, CiGstPeriodFinancials> fins = new HashMap<>();

            if (persistPeriods) {
                for (KarzaGstCanonicalExtractor.ExtractedPeriod ep : extracted.periods()) {
                    persistPeriod(tid, reg, ep, periods, fins);
                }
            }

            bundles.add(new GstMetricService.PeriodBundle(reg, periods, fins));
        }

        List<CiMetricResult> metrics = metricService.computeAndPersist(
                tid, applicationId, source.getId(), bundles, LocalDate.now());

        log.info("GST canonicalization normalized applicationId={} gstins={} periodsPersisted={}",
                applicationId, savedRegs.size(), persistPeriods);
        return new NormalizationResult(savedRegs, metrics, false);
    }

    private void persistPeriod(
            UUID tid,
            CiGstRegistration reg,
            KarzaGstCanonicalExtractor.ExtractedPeriod ep,
            List<CiGstReturnPeriod> periodsOut,
            Map<UUID, CiGstPeriodFinancials> finsOut) {

        Optional<CiGstReturnPeriod> existingEffective = periodRepository
                .findByGstRegistrationIdAndReturnTypeAndPeriodYyyyMmAndEffectiveTrue(
                        reg.getId(), ep.returnType(), ep.periodYyyyMm());

        CiGstReturnPeriod period = CiGstReturnPeriod.builder()
                .tenantId(tid)
                .gstRegistrationId(reg.getId())
                .returnType(ep.returnType())
                .financialYear(ep.financialYear())
                .periodYyyyMm(ep.periodYyyyMm())
                .filingStatus(ep.filingStatus() != null ? ep.filingStatus() : "UNKNOWN")
                .filingDelayDays(ep.filingDelayDays())
                .effective(true)
                .qualityStatus("OK")
                .sourceReference(reg.getSourceReference())
                .metadata(ep.metadata() != null ? ep.metadata() : Map.of())
                .build();

        if (existingEffective.isPresent()) {
            CiGstReturnPeriod old = existingEffective.get();
            old.setEffective(false);
            periodRepository.save(old);
            period = periodRepository.save(period);
            old.setSupersededByPeriodId(period.getId());
            periodRepository.save(old);

            revisionRepository.save(CiGstReturnRevision.builder()
                    .tenantId(tid)
                    .gstRegistrationId(reg.getId())
                    .returnType(ep.returnType())
                    .periodYyyyMm(ep.periodYyyyMm())
                    .originalPeriodId(old.getId())
                    .effectivePeriodId(period.getId())
                    .selectionBasis("LATEST_EFFECTIVE")
                    .sourceReferences(List.of(
                            Map.of("original", old.getId().toString(), "effective", period.getId().toString())))
                    .metadata(Map.of("reason", "DUPLICATE_OR_AMENDMENT"))
                    .build());
        } else {
            period = periodRepository.save(period);
        }
        periodsOut.add(period);

        if (ep.turnoverPresent()) {
            CiGstPeriodFinancials fin = financialsRepository.save(CiGstPeriodFinancials.builder()
                    .tenantId(tid)
                    .returnPeriodId(period.getId())
                    .returnType(ep.returnType())
                    .taxableTurnover(ep.taxableTurnover())
                    .extractionQuality("OK")
                    .sourceReference(reg.getSourceReference())
                    .metadata(Map.of("turnoverPresent", true))
                    .build());
            finsOut.put(period.getId(), fin);
        }
    }

    private GstMetricService.PeriodBundle loadBundle(CiGstRegistration reg) {
        List<CiGstReturnPeriod> periods = periodRepository.findByGstRegistrationIdAndEffectiveTrue(reg.getId());
        Map<UUID, CiGstPeriodFinancials> fins = new HashMap<>();
        for (CiGstReturnPeriod p : periods) {
            financialsRepository.findByReturnPeriodId(p.getId())
                    .ifPresent(f -> fins.put(p.getId(), f));
        }
        return new GstMetricService.PeriodBundle(reg, periods, fins);
    }

    private static Map<String, Object> checksumPayload(Map<String, Object> data, String requestId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("requestId", requestId);
        m.put("gstin", data.get("gstin"));
        if (data.get("mappedMetrics") instanceof Map<?, ?> mm) {
            m.put("mappedMetricsKeys", mm.keySet().toString());
            m.put("annualGstTurnover", mm.get("annualGstTurnover"));
        }
        // Prefer structural fingerprint without dumping full Karza tree into hash input logs
        Object full = data.get("fullResponse");
        if (full instanceof Map<?, ?> fr) {
            m.put("fullResponsePresent", true);
            Object result = fr.get("result");
            if (result instanceof Map<?, ?> rm) {
                Object current = rm.get("current");
                if (current instanceof Map<?, ?> cur) {
                    Object mws = cur.get("monthWiseSummary");
                    m.put("monthWiseCount", mws instanceof List<?> l ? l.size() : 0);
                    Object fs = cur.get("filingStatus");
                    m.put("filingStatusCount", fs instanceof List<?> l ? l.size() : 0);
                }
            }
        }
        return m;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
