package com.los.core.creditintelligence.policystudio.parameters.manualoverride;

import com.los.core.creditintelligence.bureau.repository.CiBureauReportRepository;
import com.los.core.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Create/list scoped manual parameter overrides. Fail-closed: only permits an override for a
 * parameter on the {@link ManualParameterOverridePolicy} allow-list, and only when that
 * parameter's real source is confirmed unavailable for this specific application — never a
 * general-purpose bypass.
 */
@Service
@RequiredArgsConstructor
public class ApplicationParameterOverrideService {

    public static final String PARAMETER_NOT_OVERRIDABLE = "PARAMETER_NOT_OVERRIDABLE";
    public static final String REAL_SOURCE_ALREADY_AVAILABLE = "REAL_SOURCE_ALREADY_AVAILABLE";

    private final ApplicationParameterManualOverrideRepository overrideRepository;
    private final CiBureauReportRepository bureauReportRepository;

    @Transactional
    public ApplicationParameterManualOverride createOverride(
            UUID applicationId, String canonicalParameterId, String value, String reason, String enteredBy) {
        if (!ManualParameterOverridePolicy.isOverridable(canonicalParameterId)) {
            throw new BusinessRuleException(
                    "Parameter " + canonicalParameterId + " is not eligible for manual override. "
                            + "Only a specific, confirmed-unintegrated-source allow-list may be overridden.",
                    PARAMETER_NOT_OVERRIDABLE,
                    "CREATE_PARAMETER_OVERRIDE",
                    Map.of(
                            "applicationId", applicationId == null ? "" : applicationId.toString(),
                            "canonicalParameterId", canonicalParameterId == null ? "" : canonicalParameterId,
                            "allowedParameterIds", ManualParameterOverridePolicy.OVERRIDABLE_PARAMETER_IDS));
        }
        assertSourceUnavailable(applicationId, canonicalParameterId);

        overrideRepository.findByApplicationIdAndCanonicalParameterIdAndActiveTrue(applicationId, canonicalParameterId)
                .ifPresent(existing -> {
                    existing.setActive(false);
                    existing.setSupersededAt(Instant.now());
                    overrideRepository.save(existing);
                });

        ApplicationParameterManualOverride created = ApplicationParameterManualOverride.builder()
                .applicationId(applicationId)
                .canonicalParameterId(canonicalParameterId.trim())
                .valueText(value)
                .reason(reason)
                .enteredBy(enteredBy)
                .enteredAt(Instant.now())
                .active(true)
                .build();
        return overrideRepository.save(created);
    }

    @Transactional(readOnly = true)
    public List<ApplicationParameterManualOverride> listOverrides(UUID applicationId) {
        return overrideRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId);
    }

    /**
     * Bureau-specific for now (the only overridable family today): a real
     * {@code ci_bureau_report} row existing at all means the real source is available and a
     * manual override must not silently shadow it.
     */
    private void assertSourceUnavailable(UUID applicationId, String canonicalParameterId) {
        if (canonicalParameterId != null && canonicalParameterId.trim().startsWith("bureau.")) {
            List<?> reports = bureauReportRepository.findByApplicationId(applicationId);
            if (!reports.isEmpty()) {
                throw new BusinessRuleException(
                        "Real bureau data is already available for this application; "
                                + "manual override is not permitted while a real source exists.",
                        REAL_SOURCE_ALREADY_AVAILABLE,
                        "CREATE_PARAMETER_OVERRIDE",
                        Map.of(
                                "applicationId", applicationId == null ? "" : applicationId.toString(),
                                "canonicalParameterId", canonicalParameterId,
                                "existingReportCount", reports.size()));
            }
        }
    }
}
