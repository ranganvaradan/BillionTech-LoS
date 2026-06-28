package com.los.lms.service;

import com.los.core.model.enums.BorrowerType;
import com.los.lms.config.LmsWorkflowMappingProperties;
import com.los.lms.entity.WorkflowLmsProductMapping;
import com.los.lms.repository.WorkflowLmsProductMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

import static com.los.encore.client.api.EncoreTemporaryOverrides.DEFAULT_ENCORE_PRODUCT_CODE;

/**
 * Resolves Encore LMS {@code productCode} for a loan application segment.
 * <p>
 * Lookup priority (bl-core parity):
 * <ol>
 *   <li>{@code (partner_code, loan_product)} — partner-specific Encore product code</li>
 *   <li>{@code (borrower_type, loan_product)} — generic fallback</li>
 *   <li>Caller-supplied {@code fallbackCode}</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowLmsProductResolver {

    private final WorkflowLmsProductMappingRepository mappingRepository;
    private final LmsWorkflowMappingProperties mappingProperties;

    /**
     * @param borrowerType   application borrower type enum
     * @param loanProduct    canonical loan product code (aligned with workflow_configs.loan_product after V32)
     * @param fallbackCode   used when mapping is disabled, no row exists, or loan product is blank
     * @return Encore product code to send on {@code openLoanAccount} only
     */
    public String resolveEncoreProductCode(BorrowerType borrowerType, String loanProduct, String fallbackCode) {
        return resolveEncoreProductCode(null, borrowerType, loanProduct, fallbackCode);
    }

    /**
     * Partner-aware overload. When {@code partnerCode} is non-blank, tries
     * {@code (partner_code, loan_product)} first before falling back.
     */
    public String resolveEncoreProductCode(String partnerCode, BorrowerType borrowerType,
                                           String loanProduct, String fallbackCode) {
        // TEMP FIX:
        // Using hardcoded Encore product code until final product mapping is completed.
        // TODO: Restore dynamic mapping after Encore product master is finalized.
        log.info("[LMS-SANCTION] Using temporary Encore product code {} (bypassed dynamic mapping - " +
                        "partnerCode={}, borrowerType={}, loanProduct={}, fallbackCode={})",
                DEFAULT_ENCORE_PRODUCT_CODE, partnerCode, borrowerType, loanProduct, fallbackCode);
        return DEFAULT_ENCORE_PRODUCT_CODE;
    }

    /**
     * Retrieves the full mapping row for a partner+product combination, giving callers
     * access to tenure_unit, penal_interest_rate, moratorium config, etc.
     */
    public Optional<WorkflowLmsProductMapping> resolveFullMapping(String partnerCode, BorrowerType borrowerType,
                                                                   String loanProduct) {
        String lp = loanProduct != null ? loanProduct.trim() : "";
        if (lp.isEmpty() || !mappingProperties.isEnabled()) {
            return Optional.empty();
        }
        if (partnerCode != null && !partnerCode.isBlank()) {
            Optional<WorkflowLmsProductMapping> byPartner =
                    mappingRepository.findByPartnerCodeAndLoanProduct(partnerCode.trim(), lp);
            if (byPartner.isPresent()) {
                return byPartner;
            }
        }
        return mappingRepository.findByBorrowerTypeAndLoanProduct(borrowerType.name(), lp);
    }
}
