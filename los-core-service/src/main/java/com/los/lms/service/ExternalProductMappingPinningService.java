package com.los.lms.service;

import com.los.core.exception.BusinessRuleException;
import com.los.core.model.catalog.StandardLoanProduct;
import com.los.core.model.entity.LoanApplication;
import com.los.lms.entity.ExternalProductMapping;
import com.los.lms.repository.ExternalProductMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalProductMappingPinningService {

    private static final String EXTERNAL_SYSTEM_ENCORE = "ENCORE";
    private static final String ACTIVE_STATUS = "ACTIVE";

    private final ExternalProductMappingRepository externalProductMappingRepository;

    /**
     * Pins the effective external product mapping onto the application before LMS openLoanAccount.
     *
     * Fail-closed semantics for Category-governed / new apps:
     * - missing mapping -> {@link LmsApplicationConfigResolver#REASON_LMS_PRODUCT_MAPPING_MISSING}
     * - ambiguous mapping -> {@link LmsApplicationConfigResolver#REASON_LMS_PRODUCT_MAPPING_AMBIGUOUS}
     * - inactive/expired mapping -> {@link LmsApplicationConfigResolver#REASON_LMS_PRODUCT_MAPPING_NOT_EFFECTIVE}
     *
     * Legacy compatibility:
     * - if {@code loan_applications.lms_product_code} is already populated, we do not override it.
     */
    public void pinEncoreMappingIfNeeded(LoanApplication app) {
        if (app == null) {
            return;
        }

        // Invoice-discounting product is handled by ProgramMaster -> Program-specific Encore product code.
        if (StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING.equals(app.getLoanProduct())) {
            return;
        }

        boolean categoryGoverned = app.getWorkflowResolutionSource() != null
                && "CATEGORY_SELECTION".equalsIgnoreCase(app.getWorkflowResolutionSource());

        boolean alreadyPinned = app.getExternalProductMappingId() != null
                && app.getExternalProductMappingVersion() != null
                && app.getLmsProductCode() != null
                && !app.getLmsProductCode().isBlank();
        if (alreadyPinned) {
            return;
        }

        // Legacy compatibility: for non-Category governance, persisted lmsProductCode stays authoritative.
        // For Category-governed apps, canonical external mapping must drive the pinned product code.
        if (!categoryGoverned
                && app.getLmsProductCode() != null
                && !app.getLmsProductCode().isBlank()) {
            return;
        }

        LocalDate asOf = LocalDate.now();
        // Effective mapping should be stable once product/workflow identity is stable.
        if (app.getCategorySelectedAt() != null) {
            asOf = app.getCategorySelectedAt().atZone(java.time.ZoneId.of("Asia/Kolkata")).toLocalDate();
        }

        List<ExternalProductMapping> effectiveActive =
                externalProductMappingRepository
                        .findByLosProductCodeAndExternalSystemAndStatusAndEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqualOrderByVersionDesc(
                                app.getLoanProduct(), EXTERNAL_SYSTEM_ENCORE, ACTIVE_STATUS, asOf);

        if (effectiveActive.size() > 1) {
            throw ambiguous(app, asOf, effectiveActive);
        }
        if (effectiveActive.size() == 1) {
            ExternalProductMapping m = effectiveActive.get(0);
            pin(app, m);
            return;
        }

        // No active effective mapping: differentiate "missing" vs "not effective (inactive/expired)".
        List<ExternalProductMapping> effectiveAnyStatus =
                externalProductMappingRepository
                        .findByLosProductCodeAndExternalSystemAndEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqualOrderByVersionDesc(
                                app.getLoanProduct(), EXTERNAL_SYSTEM_ENCORE, asOf);

        if (!effectiveAnyStatus.isEmpty()) {
            // There are mappings covering the date but none are ACTIVE.
            throw notEffective(app, asOf);
        }

        // No coverage for date: check whether *any* mapping exists (expired vs truly missing).
        List<ExternalProductMapping> anyMappingsForProductSystem =
                externalProductMappingRepository
                        .findByLosProductCodeAndExternalSystemOrderByVersionDesc(
                                app.getLoanProduct(), EXTERNAL_SYSTEM_ENCORE);

        if (!anyMappingsForProductSystem.isEmpty()) {
            // Mappings exist but none are effective for the as-of date.
            throw notEffective(app, asOf);
        }

        throw missing(app, asOf);
    }

    private void pin(LoanApplication app, ExternalProductMapping m) {
        log.info("[LMS-EXTERNAL-MAPPING] Pinning external mapping id={} v{} code={} for app={}",
                m.getId(), m.getVersion(), m.getExternalProductCode(), app.getApplicationNumber());
        app.setExternalProductMappingId(m.getId());
        app.setExternalProductMappingVersion(m.getVersion());
        app.setLmsProductCode(m.getExternalProductCode());
    }

    private static BusinessRuleException missing(LoanApplication app, LocalDate asOf) {
        return new BusinessRuleException(
                "No external LMS product mapping is configured for this application.",
                LmsApplicationConfigResolver.REASON_LMS_PRODUCT_MAPPING_MISSING,
                "OPEN_LOAN_ACCOUNT",
                java.util.Map.of(
                        "loanProduct", app.getLoanProduct(),
                        "externalSystem", EXTERNAL_SYSTEM_ENCORE,
                        "asOf", asOf.toString()
                ));
    }

    private static BusinessRuleException ambiguous(LoanApplication app, LocalDate asOf, List<ExternalProductMapping> matches) {
        ExternalProductMapping top = matches.stream()
                .max(Comparator.comparingInt(ExternalProductMapping::getVersion))
                .orElse(null);
        return new BusinessRuleException(
                "Ambiguous external LMS product mapping (multiple active effective rows).",
                LmsApplicationConfigResolver.REASON_LMS_PRODUCT_MAPPING_AMBIGUOUS,
                "OPEN_LOAN_ACCOUNT",
                java.util.Map.of(
                        "loanProduct", app.getLoanProduct(),
                        "externalSystem", EXTERNAL_SYSTEM_ENCORE,
                        "asOf", asOf.toString(),
                        "topCandidateId", top != null ? top.getId().toString() : null
                ));
    }

    private static BusinessRuleException notEffective(LoanApplication app, LocalDate asOf) {
        return new BusinessRuleException(
                "External LMS product mapping is present but not active/effective for this date.",
                LmsApplicationConfigResolver.REASON_LMS_PRODUCT_MAPPING_NOT_EFFECTIVE,
                "OPEN_LOAN_ACCOUNT",
                java.util.Map.of(
                        "loanProduct", app.getLoanProduct(),
                        "externalSystem", EXTERNAL_SYSTEM_ENCORE,
                        "asOf", asOf.toString()
                ));
    }
}

