package com.los.core.creditintelligence.cutover.store;

import com.los.core.creditintelligence.cutover.domain.CiCutoverCohort;
import com.los.core.creditintelligence.cutover.domain.CiCutoverComparison;
import com.los.core.creditintelligence.cutover.domain.CiCutoverControl;
import com.los.core.creditintelligence.cutover.domain.CiCutoverDimension;
import com.los.core.creditintelligence.cutover.domain.CiCutoverDrill;
import com.los.core.creditintelligence.cutover.domain.CiCutoverException;
import com.los.core.creditintelligence.cutover.domain.CiCutoverOperationalEvent;
import com.los.core.creditintelligence.cutover.domain.CiCutoverReadinessSnapshot;
import com.los.core.creditintelligence.cutover.domain.CiCutoverReview;
import com.los.core.creditintelligence.cutover.domain.CiDecisionCertification;
import com.los.core.creditintelligence.cutover.domain.CiLegacyDefaultDefinition;
import com.los.core.creditintelligence.cutover.domain.CiLimitedPilotCertification;
import com.los.core.creditintelligence.cutover.domain.CiPilotCandidateScore;
import com.los.core.creditintelligence.cutover.domain.CiPilotDataGap;
import com.los.core.creditintelligence.cutover.domain.CiPolicyCertification;
import com.los.core.creditintelligence.validation.domain.CiPolicyBinding;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory store for G0 cutover (unit tests and when JPA unavailable).
 */
@Component
public class CutoverStore {

    private final ConcurrentHashMap<UUID, CiLegacyDefaultDefinition> defaults = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CiCutoverCohort> cohorts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CiCutoverDimension> dimensions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CiCutoverComparison> comparisons = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CiCutoverReadinessSnapshot> readiness = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CiPolicyCertification> policyCerts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CiDecisionCertification> decisionCerts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CiCutoverControl> controls = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CiCutoverReview> reviews = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CiPolicyBinding> bindings = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CiLimitedPilotCertification> pilotCerts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CiPilotDataGap> dataGaps = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CiCutoverOperationalEvent> opsEvents = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CiCutoverDrill> drills = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CiCutoverException> exceptions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CiPilotCandidateScore> candidateScores = new ConcurrentHashMap<>();

    public CiLegacyDefaultDefinition saveDefault(CiLegacyDefaultDefinition d) {
        if (d.getId() == null) d.setId(UUID.randomUUID());
        if (d.getCreatedAt() == null) d.setCreatedAt(Instant.now());
        defaults.put(d.getId(), d);
        return d;
    }

    public List<CiLegacyDefaultDefinition> listDefaults() {
        return new ArrayList<>(defaults.values());
    }

    public Optional<CiLegacyDefaultDefinition> findDefault(String legacyKey, String component) {
        return defaults.values().stream()
                .filter(d -> legacyKey.equals(d.getLegacyKey())
                        && (component == null || component.equals(d.getComponent())))
                .findFirst();
    }

    public CiCutoverCohort saveCohort(CiCutoverCohort c) {
        if (c.getId() == null) c.setId(UUID.randomUUID());
        if (c.getCreatedAt() == null) c.setCreatedAt(Instant.now());
        cohorts.put(c.getId(), c);
        return c;
    }

    public Optional<CiCutoverCohort> findCohort(UUID id) {
        return Optional.ofNullable(cohorts.get(id));
    }

    public List<CiCutoverCohort> listCohorts() {
        return new ArrayList<>(cohorts.values());
    }

    public List<CiCutoverCohort> listCohortsByTenant(UUID tenantId) {
        return cohorts.values().stream()
                .filter(c -> tenantId.equals(c.getTenantId()))
                .collect(Collectors.toList());
    }

    public CiCutoverDimension saveDimension(CiCutoverDimension d) {
        if (d.getId() == null) d.setId(UUID.randomUUID());
        dimensions.put(d.getId(), d);
        return d;
    }

    public List<CiCutoverDimension> listDimensions(UUID cohortId) {
        return dimensions.values().stream()
                .filter(d -> cohortId.equals(d.getCohortId()))
                .collect(Collectors.toList());
    }

    public CiCutoverComparison saveComparison(CiCutoverComparison c) {
        if (c.getId() == null) c.setId(UUID.randomUUID());
        if (c.getCreatedAt() == null) c.setCreatedAt(Instant.now());
        comparisons.put(c.getId(), c);
        return c;
    }

    public Optional<CiCutoverComparison> findComparison(UUID id) {
        return Optional.ofNullable(comparisons.get(id));
    }

    public List<CiCutoverComparison> listComparisons(UUID cohortId) {
        return comparisons.values().stream()
                .filter(c -> cohortId.equals(c.getCohortId()))
                .collect(Collectors.toList());
    }

    public Optional<CiCutoverComparison> findComparisonByApplication(UUID applicationId) {
        return comparisons.values().stream()
                .filter(c -> applicationId.equals(c.getApplicationId()))
                .findFirst();
    }

    public CiCutoverReadinessSnapshot saveReadiness(CiCutoverReadinessSnapshot s) {
        if (s.getId() == null) s.setId(UUID.randomUUID());
        if (s.getCreatedAt() == null) s.setCreatedAt(Instant.now());
        readiness.put(s.getId(), s);
        return s;
    }

    public Optional<CiCutoverReadinessSnapshot> latestReadiness(UUID cohortId) {
        return readiness.values().stream()
                .filter(s -> cohortId == null || cohortId.equals(s.getCohortId()))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .findFirst();
    }

    public CiPolicyCertification savePolicyCert(CiPolicyCertification c) {
        if (c.getId() == null) c.setId(UUID.randomUUID());
        if (c.getCreatedAt() == null) c.setCreatedAt(Instant.now());
        policyCerts.put(c.getId(), c);
        return c;
    }

    public List<CiPolicyCertification> listPolicyCerts(UUID tenantId) {
        return policyCerts.values().stream()
                .filter(c -> tenantId.equals(c.getTenantId()))
                .collect(Collectors.toList());
    }

    public CiDecisionCertification saveDecisionCert(CiDecisionCertification c) {
        if (c.getId() == null) c.setId(UUID.randomUUID());
        if (c.getCreatedAt() == null) c.setCreatedAt(Instant.now());
        decisionCerts.put(c.getId(), c);
        return c;
    }

    public List<CiDecisionCertification> listDecisionCerts(UUID tenantId) {
        return decisionCerts.values().stream()
                .filter(c -> tenantId.equals(c.getTenantId()))
                .collect(Collectors.toList());
    }

    public CiCutoverControl saveControl(CiCutoverControl c) {
        if (c.getId() == null) c.setId(UUID.randomUUID());
        if (c.getCreatedAt() == null) c.setCreatedAt(Instant.now());
        if (c.getEffectiveAt() == null) c.setEffectiveAt(Instant.now());
        controls.put(c.getId(), c);
        return c;
    }

    public Optional<CiCutoverControl> latestControl(UUID cohortId) {
        return controls.values().stream()
                .filter(c -> cohortId.equals(c.getCohortId()))
                .sorted((a, b) -> {
                    int cmp = b.getEffectiveAt().compareTo(a.getEffectiveAt());
                    if (cmp != 0) return cmp;
                    Instant ac = a.getCreatedAt() == null ? Instant.EPOCH : a.getCreatedAt();
                    Instant bc = b.getCreatedAt() == null ? Instant.EPOCH : b.getCreatedAt();
                    cmp = bc.compareTo(ac);
                    if (cmp != 0) return cmp;
                    return String.valueOf(b.getId()).compareTo(String.valueOf(a.getId()));
                })
                .findFirst();
    }

    public List<CiCutoverControl> listControls(UUID cohortId) {
        return controls.values().stream()
                .filter(c -> cohortId.equals(c.getCohortId()))
                .collect(Collectors.toList());
    }

    public CiCutoverReview saveReview(CiCutoverReview r) {
        if (r.getId() == null) r.setId(UUID.randomUUID());
        if (r.getCreatedAt() == null) r.setCreatedAt(Instant.now());
        reviews.put(r.getId(), r);
        return r;
    }

    public List<CiCutoverReview> listReviews(UUID comparisonId) {
        return reviews.values().stream()
                .filter(r -> comparisonId.equals(r.getComparisonId()))
                .sorted((a, b) -> {
                    Instant ac = a.getCreatedAt() == null ? Instant.EPOCH : a.getCreatedAt();
                    Instant bc = b.getCreatedAt() == null ? Instant.EPOCH : b.getCreatedAt();
                    int cmp = ac.compareTo(bc);
                    if (cmp != 0) return cmp;
                    return String.valueOf(a.getId()).compareTo(String.valueOf(b.getId()));
                })
                .collect(Collectors.toList());
    }

    public CiPolicyBinding saveBinding(CiPolicyBinding b) {
        if (b.getId() == null) b.setId(UUID.randomUUID());
        bindings.put(b.getId(), b);
        return b;
    }

    public List<CiPolicyBinding> listBindings(UUID tenantId) {
        return bindings.values().stream()
                .filter(b -> tenantId.equals(b.getTenantId()))
                .collect(Collectors.toList());
    }

    public Optional<CiPolicyBinding> findBinding(UUID tenantId, String legacyParameter) {
        return bindings.values().stream()
                .filter(b -> tenantId.equals(b.getTenantId()) && legacyParameter.equals(b.getLegacyParameter()))
                .findFirst();
    }

    public CiLimitedPilotCertification savePilotCertification(CiLimitedPilotCertification c) {
        if (c.getId() == null) c.setId(UUID.randomUUID());
        if (c.getCreatedAt() == null) c.setCreatedAt(Instant.now());
        pilotCerts.put(c.getId(), c);
        return c;
    }

    public Optional<CiLimitedPilotCertification> latestPilotCertification(UUID cohortId) {
        return pilotCerts.values().stream()
                .filter(c -> cohortId.equals(c.getCohortId()))
                .max(Comparator.comparing(CiLimitedPilotCertification::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())));
    }

    public List<CiLimitedPilotCertification> listPilotCertifications(UUID cohortId) {
        return pilotCerts.values().stream()
                .filter(c -> cohortId.equals(c.getCohortId()))
                .sorted(Comparator.comparing(CiLimitedPilotCertification::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    public CiPilotDataGap saveDataGap(CiPilotDataGap g) {
        if (g.getId() == null) g.setId(UUID.randomUUID());
        if (g.getCreatedAt() == null) g.setCreatedAt(Instant.now());
        dataGaps.put(g.getId(), g);
        return g;
    }

    public List<CiPilotDataGap> listDataGaps(UUID cohortId) {
        return dataGaps.values().stream()
                .filter(g -> cohortId.equals(g.getCohortId()))
                .collect(Collectors.toList());
    }

    public CiCutoverOperationalEvent saveOperationalEvent(CiCutoverOperationalEvent e) {
        if (e.getId() == null) e.setId(UUID.randomUUID());
        if (e.getCreatedAt() == null) e.setCreatedAt(Instant.now());
        opsEvents.put(e.getId(), e);
        return e;
    }

    public List<CiCutoverOperationalEvent> listOperationalEvents(UUID cohortId) {
        return opsEvents.values().stream()
                .filter(e -> cohortId.equals(e.getCohortId()))
                .sorted(Comparator.comparing(CiCutoverOperationalEvent::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    public CiCutoverDrill saveDrill(CiCutoverDrill d) {
        if (d.getId() == null) d.setId(UUID.randomUUID());
        if (d.getPerformedAt() == null) d.setPerformedAt(Instant.now());
        drills.put(d.getId(), d);
        return d;
    }

    public List<CiCutoverDrill> listDrills(UUID cohortId) {
        return drills.values().stream()
                .filter(d -> cohortId.equals(d.getCohortId()))
                .collect(Collectors.toList());
    }

    public Optional<CiCutoverDrill> latestDrill(UUID cohortId, String drillType) {
        return drills.values().stream()
                .filter(d -> cohortId.equals(d.getCohortId()) && drillType.equals(d.getDrillType()))
                .max(Comparator.comparing(CiCutoverDrill::getPerformedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())));
    }

    public CiCutoverException saveException(CiCutoverException e) {
        if (e.getId() == null) e.setId(UUID.randomUUID());
        if (e.getCreatedAt() == null) e.setCreatedAt(Instant.now());
        exceptions.put(e.getId(), e);
        return e;
    }

    public List<CiCutoverException> listExceptions(UUID cohortId) {
        return exceptions.values().stream()
                .filter(e -> cohortId.equals(e.getCohortId()))
                .collect(Collectors.toList());
    }

    public CiPilotCandidateScore saveCandidateScore(CiPilotCandidateScore s) {
        if (s.getId() == null) s.setId(UUID.randomUUID());
        if (s.getRankedAt() == null) s.setRankedAt(Instant.now());
        candidateScores.put(s.getId(), s);
        return s;
    }

    public List<CiPilotCandidateScore> listCandidateScores() {
        return candidateScores.values().stream()
                .sorted(Comparator.comparing(CiPilotCandidateScore::getScore,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    public void clear() {
        defaults.clear();
        cohorts.clear();
        dimensions.clear();
        comparisons.clear();
        readiness.clear();
        policyCerts.clear();
        decisionCerts.clear();
        controls.clear();
        reviews.clear();
        bindings.clear();
        pilotCerts.clear();
        dataGaps.clear();
        opsEvents.clear();
        drills.clear();
        exceptions.clear();
        candidateScores.clear();
    }
}
