package com.los.core.creditintelligence.policystudio.runtime.canonicalconfig;

import com.los.core.model.entity.LoanApplication;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persists an immutable freeze of the canonical identity package.
 * RESOLVED rows are never overwritten. Retries return the same package.
 * Does not write category/workflow/policy/scorecard pins onto loan_applications.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CanonicalApplicationConfigurationFreezeService {

    public static final String STATUS_RESOLVED = CanonicalResolutionStatus.RESOLVED.name();
    public static final String STATUS_NOT_RESOLVABLE = CanonicalResolutionStatus.NOT_RESOLVABLE.name();

    private final CanonicalApplicationConfigurationResolver resolver;
    private final CanonicalApplicationConfigurationRepository repository;

    /**
     * Resolve and freeze. Idempotent for RESOLVED. Failures are returned, not thrown
     * as underwriting blockers.
     */
    @Transactional
    public CanonicalApplicationConfigurationResolution freezeObservably(LoanApplication app) {
        if (app == null || app.getId() == null) {
            return new CanonicalApplicationConfigurationResolution(
                    CanonicalResolutionStatus.NOT_RESOLVABLE,
                    null,
                    List.of(CanonicalResolutionFailureCode.CUSTOMER_CATEGORY_NOT_PINNED.name()),
                    Map.of());
        }
        Optional<CanonicalApplicationConfigurationEntity> existingResolved =
                repository.findFirstByApplicationIdAndStatusOrderByCreatedAtAsc(app.getId(), STATUS_RESOLVED);
        if (existingResolved.isPresent()) {
            return toResolution(existingResolved.get());
        }
        CanonicalApplicationConfigurationResolution resolution = resolver.resolve(app);
        persist(app.getId(), resolution);
        return resolution;
    }

    @Transactional(readOnly = true)
    public Optional<CanonicalApplicationConfigurationResolution> findResolvedFreeze(UUID applicationId) {
        return repository.findFirstByApplicationIdAndStatusOrderByCreatedAtAsc(applicationId, STATUS_RESOLVED)
                .map(CanonicalApplicationConfigurationFreezeService::toResolution);
    }

    @Transactional(readOnly = true)
    public Optional<CanonicalApplicationConfigurationResolution> findLatestFreeze(UUID applicationId) {
        List<CanonicalApplicationConfigurationEntity> rows =
                repository.findByApplicationIdOrderByCreatedAtDesc(applicationId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toResolution(rows.get(0)));
    }

    private void persist(UUID applicationId, CanonicalApplicationConfigurationResolution resolution) {
        Map<String, Object> packageJson = resolution.configuration() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(resolution.configuration().toMap());
        String hash = resolution.configuration() == null
                ? "unresolved"
                : resolution.configuration().identityHash();
        CanonicalApplicationConfigurationEntity row = CanonicalApplicationConfigurationEntity.builder()
                .id(UUID.randomUUID())
                .applicationId(applicationId)
                .status(resolution.status() == null ? STATUS_NOT_RESOLVABLE : resolution.status().name())
                .identityHash(hash)
                .reasonCodes(new ArrayList<>(resolution.reasonCodes()))
                .outcomes(new LinkedHashMap<>(resolution.outcomes()))
                .packageJson(packageJson)
                .build();
        repository.save(row);
        log.info("canonical_configuration_freeze app={} status={} reasons={}",
                applicationId, row.getStatus(), row.getReasonCodes());
    }

    static CanonicalApplicationConfigurationResolution toResolution(CanonicalApplicationConfigurationEntity row) {
        Map<String, String> outcomes = row.getOutcomes() == null ? Map.of() : row.getOutcomes();
        List<String> reasons = row.getReasonCodes() == null ? List.of() : row.getReasonCodes();
        return CanonicalApplicationConfigurationResolution.fromStored(
                row.getStatus(), row.getPackageJson(), reasons, outcomes);
    }
}
