package com.los.lms.service;

import com.los.core.exception.BusinessRuleException;
import com.los.lms.entity.ExternalProductMapping;
import com.los.lms.repository.ExternalProductMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalProductMappingAdminService {

    // Kept as a semantic constant (external system itself is configured per mapping row).
    private static final String ACTIVE_STATUS = "ACTIVE";

    // Database-level backstop for the same rule enforced by findActiveOverlapping below
    // (see V153__external_product_mapping_overlap_exclusion.sql) — closes the TOCTOU race
    // where two concurrent admin requests could both pass the app-level SELECT check.
    private static final String OVERLAP_EXCLUSION_CONSTRAINT = "excl_external_product_mapping_active_no_overlap";

    private final ExternalProductMappingRepository externalProductMappingRepository;

    public List<ExternalProductMapping> listMappings(
            String losProductCode,
            String externalSystem) {
        return listMappings(losProductCode, externalSystem, null);
    }

    /**
     * @param bookType optional filter (OWN_BOOK / COLENDING); null/blank lists across both —
     *                 admin visibility should show the full picture, not just one book type.
     */
    public List<ExternalProductMapping> listMappings(
            String losProductCode,
            String externalSystem,
            String bookType) {
        if (losProductCode == null || losProductCode.isBlank()) {
            throw new BusinessRuleException(
                    "losProductCode is required",
                    "INVALID_ARGUMENT",
                    "LIST_EXTERNAL_PRODUCT_MAPPINGS",
                    java.util.Map.of("losProductCode", losProductCode));
        }
        List<ExternalProductMapping> rows;
        if (externalSystem == null || externalSystem.isBlank()) {
            rows = externalProductMappingRepository.findByLosProductCodeOrderByExternalSystemAscVersionDesc(
                    losProductCode.trim());
        } else {
            rows = externalProductMappingRepository
                    .findByLosProductCodeAndExternalSystemOrderByVersionDesc(
                            losProductCode.trim(),
                            externalSystem.trim());
        }
        if (bookType == null || bookType.isBlank()) {
            return rows;
        }
        String normalized = bookType.trim();
        return rows.stream().filter(m -> normalized.equalsIgnoreCase(m.getBookType())).toList();
    }

    public ExternalProductMapping createMapping(CreateMappingRequest req) {
        requireNotBlank(req.losProductCode(), "losProductCode");
        requireNotBlank(req.externalSystem(), "externalSystem");
        requireNotBlank(req.bookType(), "bookType");
        String bookType = normalizeBookType(req.bookType());
        requireNotBlank(req.externalProductCode(), "externalProductCode");
        if (req.version() == null) {
            throw new BusinessRuleException(
                    "version is required",
                    "INVALID_ARGUMENT",
                    "CREATE_EXTERNAL_PRODUCT_MAPPING",
                    java.util.Map.of("version", req.version()));
        }
        if (req.effectiveFrom() == null || req.effectiveTo() == null) {
            throw new BusinessRuleException(
                    "effectiveFrom and effectiveTo are required",
                    "INVALID_ARGUMENT",
                    "CREATE_EXTERNAL_PRODUCT_MAPPING",
                    java.util.Map.of(
                            "effectiveFrom", req.effectiveFrom(),
                            "effectiveTo", req.effectiveTo()));
        }
        if (req.effectiveFrom().isAfter(req.effectiveTo())) {
            throw new BusinessRuleException(
                    "effectiveFrom must be <= effectiveTo",
                    "INVALID_ARGUMENT",
                    "CREATE_EXTERNAL_PRODUCT_MAPPING",
                    java.util.Map.of(
                            "effectiveFrom", req.effectiveFrom(),
                            "effectiveTo", req.effectiveTo()));
        }

        String status = normalizeStatus(req.status());

        // Immutable-evidence discipline:
        // - We only allow inserting new version rows.
        // - Updates are handled via status-only patch in controller.
        // - This service therefore never mutates effective window or externalProductCode for an existing mapping.

        if (ACTIVE_STATUS.equalsIgnoreCase(status)) {
            List<ExternalProductMapping> overlaps = externalProductMappingRepository.findActiveOverlapping(
                    req.losProductCode().trim(),
                    req.externalSystem().trim(),
                    bookType,
                    ACTIVE_STATUS,
                    req.effectiveFrom(),
                    req.effectiveTo(),
                    null);
            if (!overlaps.isEmpty()) {
                ExternalProductMapping top = overlaps.stream()
                        .max(Comparator.comparingInt(ExternalProductMapping::getVersion))
                        .orElse(null);
                throw new BusinessRuleException(
                        "Overlapping ACTIVE/effective mappings are not allowed for the same "
                                + "(los_product_code, external_system, book_type).",
                        "LMS_PRODUCT_MAPPING_AMBIGUOUS_ACTIVE_OVERLAP",
                        "CREATE_EXTERNAL_PRODUCT_MAPPING",
                        java.util.Map.of(
                                "losProductCode", req.losProductCode(),
                                "externalSystem", req.externalSystem(),
                                "bookType", bookType,
                                "effectiveFrom", req.effectiveFrom().toString(),
                                "effectiveTo", req.effectiveTo().toString(),
                                "topCandidateVersion", top == null ? null : top.getVersion()));
            }
        }

        ExternalProductMapping entity = ExternalProductMapping.builder()
                .losProductCode(req.losProductCode().trim())
                .externalSystem(req.externalSystem().trim())
                .bookType(bookType)
                .externalProductCode(req.externalProductCode().trim())
                .version(req.version())
                .status(status)
                .effectiveFrom(req.effectiveFrom())
                .effectiveTo(req.effectiveTo())
                .metadataJson(req.metadataJson())
                .build();

        try {
            return externalProductMappingRepository.save(entity);
        } catch (DataIntegrityViolationException e) {
            throw translateOverlapViolation(e, req.losProductCode(), req.externalSystem(),
                    "CREATE_EXTERNAL_PRODUCT_MAPPING");
        }
    }

    public ExternalProductMapping patchStatus(UUID mappingId, String newStatus) {
        if (mappingId == null) {
            throw new BusinessRuleException(
                    "mappingId is required",
                    "INVALID_ARGUMENT",
                    "PATCH_EXTERNAL_PRODUCT_MAPPING_STATUS",
                    java.util.Map.of("mappingId", null));
        }
        ExternalProductMapping existing = externalProductMappingRepository.findById(mappingId)
                .orElseThrow(() -> new BusinessRuleException(
                        "Unknown external product mapping",
                        "NOT_FOUND",
                        "PATCH_EXTERNAL_PRODUCT_MAPPING_STATUS",
                        java.util.Map.of("mappingId", mappingId)));

        String status = normalizeStatus(newStatus);
        if (Objects.equals(existing.getStatus(), status)) {
            return existing;
        }

        if (ACTIVE_STATUS.equalsIgnoreCase(status)) {
            List<ExternalProductMapping> overlaps = externalProductMappingRepository.findActiveOverlapping(
                    existing.getLosProductCode(),
                    existing.getExternalSystem(),
                    existing.getBookType(),
                    ACTIVE_STATUS,
                    existing.getEffectiveFrom(),
                    existing.getEffectiveTo(),
                    existing.getId());
            if (!overlaps.isEmpty()) {
                throw new BusinessRuleException(
                        "Cannot activate mapping due to overlapping ACTIVE/effective mappings for the same (los_product_code, external_system).",
                        "LMS_PRODUCT_MAPPING_AMBIGUOUS_ACTIVE_OVERLAP",
                        "PATCH_EXTERNAL_PRODUCT_MAPPING_STATUS",
                        java.util.Map.of(
                                "losProductCode", existing.getLosProductCode(),
                                "externalSystem", existing.getExternalSystem(),
                                "mappingId", existing.getId().toString()));
            }
        }

        existing.setStatus(status);
        try {
            existing = externalProductMappingRepository.save(existing);
        } catch (DataIntegrityViolationException e) {
            throw translateOverlapViolation(e, existing.getLosProductCode(), existing.getExternalSystem(),
                    "PATCH_EXTERNAL_PRODUCT_MAPPING_STATUS");
        }
        return existing;
    }

    /**
     * Translates the database-level exclusion-constraint violation (the TOCTOU backstop for
     * the app-level findActiveOverlapping check above) into the same business error the app-level
     * check throws. Any other integrity violation is rethrown unchanged.
     */
    private BusinessRuleException translateOverlapViolation(
            DataIntegrityViolationException e, String losProductCode, String externalSystem, String action) {
        String detail = String.valueOf(e.getMostSpecificCause() != null
                ? e.getMostSpecificCause().getMessage() : e.getMessage());
        if (!detail.contains(OVERLAP_EXCLUSION_CONSTRAINT)) {
            throw e;
        }
        log.warn("[EXTERNAL-PRODUCT-MAPPING] Overlap exclusion constraint caught a concurrent activation race "
                        + "losProductCode={} externalSystem={}", losProductCode, externalSystem);
        return new BusinessRuleException(
                "Overlapping ACTIVE/effective mappings are not allowed for the same (los_product_code, external_system).",
                "LMS_PRODUCT_MAPPING_AMBIGUOUS_ACTIVE_OVERLAP",
                action,
                java.util.Map.of(
                        "losProductCode", losProductCode,
                        "externalSystem", externalSystem));
    }

    private static final java.util.Set<String> VALID_BOOK_TYPES = java.util.Set.of("OWN_BOOK", "COLENDING");

    private static String normalizeBookType(String bookType) {
        String normalized = bookType.trim().toUpperCase();
        if (!VALID_BOOK_TYPES.contains(normalized)) {
            throw new BusinessRuleException(
                    "bookType must be one of " + VALID_BOOK_TYPES,
                    "INVALID_ARGUMENT",
                    "VALIDATE_EXTERNAL_PRODUCT_MAPPING",
                    java.util.Map.of("bookType", bookType));
        }
        return normalized;
    }

    private static String normalizeStatus(String status) {
        String s = status == null ? "" : status.trim().toUpperCase();
        if (s.isBlank()) {
            return "INACTIVE";
        }
        // Accept platform-provided values as long as they are stable strings.
        return s;
    }

    private static void requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException(
                    fieldName + " is required",
                    "INVALID_ARGUMENT",
                    "VALIDATE_EXTERNAL_PRODUCT_MAPPING",
                    java.util.Collections.singletonMap(fieldName, value));
        }
    }

    public record CreateMappingRequest(
            String losProductCode,
            String externalSystem,
            /** OWN_BOOK or COLENDING — required, no wildcard. */
            String bookType,
            String externalProductCode,
            Integer version,
            String status,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            java.util.Map<String, Object> metadataJson) {}
}

