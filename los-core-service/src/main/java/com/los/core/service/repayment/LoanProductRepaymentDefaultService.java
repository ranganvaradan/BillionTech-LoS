package com.los.core.service.repayment;

import com.los.core.model.dto.request.LoanProductRepaymentDefaultUpdateRequest;
import com.los.core.model.dto.response.LoanProductRepaymentDefaultResponse;
import com.los.core.model.entity.LoanProductRepaymentDefault;
import com.los.core.repository.LoanProductRepaymentDefaultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LoanProductRepaymentDefaultService {

    public static final String INVOICE_DISCOUNTING_PRODUCT = "BUSINESS_WC_INVOICE_DISCOUNTING";

    private static final Set<String> MECHANISMS = Set.of("SMART_COLLECT", "PAYU_PG", "API_PG");

    private final LoanProductRepaymentDefaultRepository repository;

    /** Resolved mechanism for runtime (disabled or missing row → SMART_COLLECT). */
    @Transactional(readOnly = true)
    public String resolveMechanism(String loanProduct) {
        if (loanProduct == null || loanProduct.isBlank()) {
            return "SMART_COLLECT";
        }
        if (INVOICE_DISCOUNTING_PRODUCT.equalsIgnoreCase(loanProduct.trim())) {
            return "SMART_COLLECT";
        }
        return repository.findById(loanProduct.trim())
                .filter(row -> Boolean.TRUE.equals(row.getEnabled()))
                .map(row -> row.getRepaymentMechanism() != null ? row.getRepaymentMechanism() : "SMART_COLLECT")
                .orElse("SMART_COLLECT");
    }

    @Transactional(readOnly = true)
    public List<LoanProductRepaymentDefaultResponse> listAll() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(LoanProductRepaymentDefault::getLoanProduct))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public LoanProductRepaymentDefaultResponse update(
            String loanProduct, LoanProductRepaymentDefaultUpdateRequest request, String updatedBy) {
        if (INVOICE_DISCOUNTING_PRODUCT.equalsIgnoreCase(loanProduct)) {
            throw new IllegalArgumentException(
                    "Invoice discounting repayments are configured in PLP Platform Admin (INVOICE_DISCOUNTING)");
        }
        LoanProductRepaymentDefault row = repository.findById(loanProduct).orElseGet(() ->
                LoanProductRepaymentDefault.builder()
                        .loanProduct(loanProduct)
                        .repaymentMechanism("SMART_COLLECT")
                        .enabled(true)
                        .build());
        if (request.getRepaymentMechanism() != null) {
            String mechanism = request.getRepaymentMechanism().trim().toUpperCase();
            if (!MECHANISMS.contains(mechanism)) {
                throw new IllegalArgumentException("Unsupported repayment mechanism: " + mechanism);
            }
            row.setRepaymentMechanism(mechanism);
        }
        if (request.getPgProviderCode() != null) {
            row.setPgProviderCode(request.getPgProviderCode().isBlank()
                    ? null
                    : request.getPgProviderCode().trim().toUpperCase());
        }
        if (request.getEnabled() != null) {
            row.setEnabled(request.getEnabled());
        }
        if (updatedBy != null && !updatedBy.isBlank()) {
            row.setUpdatedBy(updatedBy.trim());
        }
        return toResponse(repository.save(row));
    }

    private LoanProductRepaymentDefaultResponse toResponse(LoanProductRepaymentDefault row) {
        return LoanProductRepaymentDefaultResponse.builder()
                .loanProduct(row.getLoanProduct())
                .repaymentMechanism(row.getRepaymentMechanism())
                .pgProviderCode(row.getPgProviderCode())
                .enabled(row.getEnabled())
                .updatedAt(row.getUpdatedAt())
                .updatedBy(row.getUpdatedBy())
                .managedByPlp(INVOICE_DISCOUNTING_PRODUCT.equalsIgnoreCase(row.getLoanProduct()))
                .build();
    }
}
