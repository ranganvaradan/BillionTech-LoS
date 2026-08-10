package com.los.lms.service;

import com.los.core.model.catalog.StandardLoanProduct;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.service.workflow.ActiveWorkflowConfigService;
import com.los.plp.model.entity.ProgramMaster;
import com.los.plp.model.entity.SubProgramMaster;
import com.los.plp.repository.ProgramMasterRepository;
import com.los.plp.repository.SubProgramMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static com.los.encore.client.api.EncoreTemporaryOverrides.DEFAULT_ENCORE_PRODUCT_CODE;

/**
 * Resolves Encore {@code productCode} and {@code tenureUnit} for a loan application.
 * Invoice discounting with PLP program LMS entry uses {@code program_masters.encore_product_code}.
 */
@Component
@RequiredArgsConstructor
public class LmsApplicationConfigResolver {

    private static final String DEFAULT_TENURE_UNIT = "Month";

    private final ActiveWorkflowConfigService activeWorkflowConfigService;
    private final SubProgramMasterRepository subProgramMasterRepository;
    private final ProgramMasterRepository programMasterRepository;

    public String resolveEncoreProductCode(LoanApplication app) {
        if (app == null) {
            return DEFAULT_ENCORE_PRODUCT_CODE;
        }
        Optional<ProgramMaster> program = resolveProgramForApplication(app);
        if (program.isPresent()) {
            ProgramMaster p = program.get();
            if ("INVOICE_DISCOUNTING".equalsIgnoreCase(p.getProductType())
                    && p.getEncoreProductCode() != null
                    && !p.getEncoreProductCode().isBlank()) {
                return p.getEncoreProductCode().trim();
            }
        }
        if (isInvoiceDiscountingProduct(app.getLoanProduct())) {
            return DEFAULT_ENCORE_PRODUCT_CODE;
        }
        if (hasText(app.getLmsProductCode())) {
            return app.getLmsProductCode().trim();
        }
        return activeWorkflowConfigService.findActiveForApplication(app)
                .map(WorkflowConfig::getLmsProductCode)
                .filter(this::hasText)
                .orElse(DEFAULT_ENCORE_PRODUCT_CODE);
    }

    public String resolveTenureUnit(LoanApplication app) {
        if (app == null) {
            return DEFAULT_TENURE_UNIT;
        }
        Optional<ProgramMaster> program = resolveProgramForApplication(app);
        if (program.isPresent()) {
            ProgramMaster p = program.get();
            if ("INVOICE_DISCOUNTING".equalsIgnoreCase(p.getProductType())
                    && p.getEncoreProductCode() != null
                    && !p.getEncoreProductCode().isBlank()) {
                return DEFAULT_TENURE_UNIT;
            }
        }
        if (isInvoiceDiscountingProduct(app.getLoanProduct())) {
            return DEFAULT_TENURE_UNIT;
        }
        if (hasText(app.getLmsTenureUnit())) {
            return normalizeTenureUnit(app.getLmsTenureUnit());
        }
        return activeWorkflowConfigService.findActiveForApplication(app)
                .map(WorkflowConfig::getLmsTenureUnit)
                .filter(this::hasText)
                .map(this::normalizeTenureUnit)
                .orElse(DEFAULT_TENURE_UNIT);
    }

    /**
     * Maps UI/API values (DAY, MONTH, WEEK or Day, Month, Week) to Encore title-case strings.
     */
    public String normalizeTenureUnit(String tenureUnit) {
        if (tenureUnit == null || tenureUnit.isBlank()) {
            return DEFAULT_TENURE_UNIT;
        }
        String normalized = tenureUnit.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "DAY" -> "Day";
            case "WEEK" -> "Week";
            case "MONTH" -> "Month";
            case "QUARTER" -> "Quarter";
            case "HALF YEAR", "HALFYEAR" -> "Half Year";
            case "YEAR" -> "Year";
            default -> {
                if ("Day".equalsIgnoreCase(tenureUnit.trim())
                        || "Week".equalsIgnoreCase(tenureUnit.trim())
                        || "Month".equalsIgnoreCase(tenureUnit.trim())) {
                    yield tenureUnit.trim().substring(0, 1).toUpperCase(Locale.ROOT)
                            + tenureUnit.trim().substring(1).toLowerCase(Locale.ROOT);
                }
                yield tenureUnit.trim();
            }
        };
    }

    private Optional<ProgramMaster> resolveProgramForApplication(LoanApplication app) {
        UUID subProgramId = app.getSubProgramId();
        if (subProgramId == null) {
            return Optional.empty();
        }
        return subProgramMasterRepository.findById(subProgramId)
                .map(SubProgramMaster::getProgramId)
                .flatMap(programMasterRepository::findById);
    }

    private boolean isInvoiceDiscountingProduct(String loanProduct) {
        return StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING.equals(loanProduct);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
