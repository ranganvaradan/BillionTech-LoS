package com.los.lms.service;

import com.los.core.exception.BusinessRuleException;
import com.los.core.model.enums.BorrowerType;
import com.los.lms.config.LmsWorkflowMappingProperties;
import com.los.lms.entity.WorkflowLmsProductMapping;
import com.los.lms.repository.WorkflowLmsProductMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Optional table-driven Encore LMS mapping ({@code workflow_lms_product_mapping}).
 * Live openLoanAccount product code authority is {@link LmsApplicationConfigResolver}
 * (application → workflow). This resolver must never hardcode a product code.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowLmsProductResolver {

    private final WorkflowLmsProductMappingRepository mappingRepository;
    private final LmsWorkflowMappingProperties mappingProperties;

    /**
     * @param borrowerType   application borrower type enum
     * @param loanProduct    canonical loan product code
     * @param fallbackCode   used when mapping is disabled or no row exists (must be non-blank when required)
     * @return Encore product code — never a hardcoded default
     */
    public String resolveEncoreProductCode(BorrowerType borrowerType, String loanProduct, String fallbackCode) {
        return resolveEncoreProductCode(null, borrowerType, loanProduct, fallbackCode);
    }

    public String resolveEncoreProductCode(String partnerCode, BorrowerType borrowerType,
                                           String loanProduct, String fallbackCode) {
        if (mappingProperties.isEnabled()) {
            Optional<WorkflowLmsProductMapping> mapped = resolveFullMapping(partnerCode, borrowerType, loanProduct);
            if (mapped.isPresent() && hasText(mapped.get().getEncoreProductCode())) {
                String code = mapped.get().getEncoreProductCode().trim();
                log.info("[LMS-PRODUCT-MAPPING] Table mapping productCode={} partner={} borrowerType={} loanProduct={}",
                        code, partnerCode, borrowerType, loanProduct);
                return code;
            }
        }
        if (hasText(fallbackCode)) {
            return fallbackCode.trim();
        }
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("partnerCode", partnerCode);
        ctx.put("borrowerType", borrowerType == null ? null : borrowerType.name());
        ctx.put("loanProduct", loanProduct);
        ctx.put("mappingEnabled", mappingProperties.isEnabled());
        throw new BusinessRuleException(
                "No LMS product mapping is configured for this application.",
                LmsApplicationConfigResolver.REASON_LMS_PRODUCT_MAPPING_MISSING,
                "OPEN_LOAN_ACCOUNT",
                ctx);
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
        if (borrowerType == null) {
            return Optional.empty();
        }
        return mappingRepository.findByBorrowerTypeAndLoanProduct(borrowerType.name(), lp);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
