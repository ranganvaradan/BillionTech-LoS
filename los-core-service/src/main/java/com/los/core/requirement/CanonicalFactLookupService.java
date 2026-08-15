package com.los.core.requirement;

import com.los.core.creditintelligence.domain.CiFactSnapshot;
import com.los.core.creditintelligence.domain.CiUnderwritingFact;
import com.los.core.creditintelligence.repository.CiFactSnapshotRepository;
import com.los.core.creditintelligence.repository.CiUnderwritingFactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * W4 — existing canonical fact lookup (read-only). Prefer facts over any new acquisition request.
 * Does not execute providers, OCR, or calculators.
 */
@Service
@RequiredArgsConstructor
public class CanonicalFactLookupService {

    private final CiFactSnapshotRepository snapshotRepository;
    private final CiUnderwritingFactRepository factRepository;

    @Transactional(readOnly = true)
    public Optional<ExistingCanonicalFact> find(
            UUID applicationId,
            String canonicalParameterId,
            Map<String, Object> inventoryHints) {

        if (canonicalParameterId == null || canonicalParameterId.isBlank()) {
            return Optional.empty();
        }
        String id = canonicalParameterId.trim();

        Optional<ExistingCanonicalFact> fromHints = fromHints(id, inventoryHints);
        if (fromHints.isPresent()) {
            return fromHints;
        }
        if (applicationId == null) {
            return Optional.empty();
        }
        return fromLatestSnapshot(applicationId, id);
    }

    @SuppressWarnings("unchecked")
    private Optional<ExistingCanonicalFact> fromHints(String id, Map<String, Object> hints) {
        if (hints == null || hints.isEmpty()) {
            return Optional.empty();
        }
        Object raw = hints.get("existingFacts");
        if (!(raw instanceof Map<?, ?> map)) {
            return Optional.empty();
        }
        Object row = map.get(id);
        if (!(row instanceof Map<?, ?> factMap)) {
            return Optional.empty();
        }
        Map<String, Object> fm = (Map<String, Object>) factMap;
        boolean ready = Boolean.TRUE.equals(fm.get("readyForPolicy"))
                || Boolean.TRUE.equals(fm.get("ready"))
                || isAcceptableQuality(String.valueOf(fm.getOrDefault("qualityStatus", "OK")));
        if (!ready && fm.containsKey("readyForPolicy") && !Boolean.TRUE.equals(fm.get("readyForPolicy"))) {
            return Optional.empty();
        }
        Map<String, Object> provenance = new LinkedHashMap<>();
        if (fm.get("provenance") instanceof Map<?, ?> p) {
            p.forEach((k, v) -> provenance.put(String.valueOf(k), v));
        }
        provenance.putIfAbsent("factSource", "INVENTORY_HINT");
        provenance.putIfAbsent("canonicalParameterId", id);
        return Optional.of(new ExistingCanonicalFact(
                id,
                ready,
                String.valueOf(fm.getOrDefault("qualityStatus", "OK")),
                String.valueOf(fm.getOrDefault("classification", "VERIFIED")),
                fm.get("sourceRef") != null ? String.valueOf(fm.get("sourceRef")) : null,
                provenance));
    }

    private Optional<ExistingCanonicalFact> fromLatestSnapshot(UUID applicationId, String id) {
        Optional<CiFactSnapshot> snap = snapshotRepository.findTopByApplicationIdOrderBySnapshotVersionDesc(applicationId);
        if (snap.isEmpty()) {
            return Optional.empty();
        }
        List<CiUnderwritingFact> facts = factRepository.findBySnapshotId(snap.get().getId());
        for (CiUnderwritingFact f : facts) {
            if (f.getCanonicalPath() == null) {
                continue;
            }
            if (!id.equals(f.getCanonicalPath().trim())) {
                continue;
            }
            if (!isAcceptableQuality(f.getQualityStatus())) {
                continue;
            }
            if ("DATA_INSUFFICIENT".equalsIgnoreCase(f.getClassification())) {
                continue;
            }
            Map<String, Object> provenance = new LinkedHashMap<>();
            provenance.put("factSource", "CI_UNDERWRITING_FACT");
            provenance.put("snapshotId", snap.get().getId().toString());
            provenance.put("snapshotVersion", snap.get().getSnapshotVersion());
            provenance.put("qualityStatus", f.getQualityStatus());
            provenance.put("classification", f.getClassification());
            if (f.getDerivationReference() != null) {
                provenance.put("derivationReference", f.getDerivationReference());
            }
            if (f.getSourceRecordIds() != null) {
                provenance.put("sourceRecordIds", f.getSourceRecordIds());
            }
            return Optional.of(new ExistingCanonicalFact(
                    id, true, f.getQualityStatus(), f.getClassification(),
                    snap.get().getId().toString(), provenance));
        }
        return Optional.empty();
    }

    static boolean isAcceptableQuality(String qualityStatus) {
        if (qualityStatus == null || qualityStatus.isBlank()) {
            return true;
        }
        String q = qualityStatus.trim().toUpperCase(Locale.ROOT);
        return switch (q) {
            case "OK", "VERIFIED", "READY", "PASSED", "PASS", "TRUE", "NULL" -> true;
            case "FAILED", "FAIL", "REJECTED", "DATA_INSUFFICIENT", "INSUFFICIENT", "STALE" -> false;
            default -> !q.contains("FAIL") && !q.contains("INSUFFICIENT");
        };
    }
}
