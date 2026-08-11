package com.los.lms.service;

import com.los.core.exception.BusinessRuleException;
import com.los.core.model.catalog.StandardLoanProduct;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.service.workflow.ActiveWorkflowConfigService;
import com.los.plp.model.entity.ProgramMaster;
import com.los.plp.model.entity.SubProgramMaster;
import com.los.plp.repository.ProgramMasterRepository;
import com.los.plp.repository.SubProgramMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves Encore {@code productCode} and {@code tenureUnit} for a loan application.
 * <p>
 * LMS product authority (LMS-PRODUCT-MAPPING-P0):
 * program encore code (invoice discounting) → application.lmsProductCode → workflow.lmsProductCode.
 * No hardcoded / default product code on the openLoanAccount path — missing mapping fails closed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LmsApplicationConfigResolver {

    private static final String DEFAULT_TENURE_UNIT = "Month";
    public static final String REASON_LMS_PRODUCT_MAPPING_MISSING = "LMS_PRODUCT_MAPPING_MISSING";

    private final ActiveWorkflowConfigService activeWorkflowConfigService;
    private final SubProgramMasterRepository subProgramMasterRepository;
    private final ProgramMasterRepository programMasterRepository;

    /**
     * @return configured Encore product code
     * @throws BusinessRuleException {@link #REASON_LMS_PRODUCT_MAPPING_MISSING} when unmapped
     */
    public String resolveEncoreProductCode(LoanApplication app) {
        return requireEncoreProductMapping(app).lmsProductCode();
    }

    public LmsProductMappingResolution requireEncoreProductMapping(LoanApplication app) {
        return resolveEncoreProductMapping(app)
                .orElseThrow(() -> missingMapping(app));
    }

    /**
     * Soft resolve for previews / optional KFS helpers — does not throw.
     */
    public Optional<LmsProductMappingResolution> resolveEncoreProductMapping(LoanApplication app) {
        if (app == null) {
            return Optional.empty();
        }
        Optional<ProgramMaster> program = resolveProgramForApplication(app);
        if (program.isPresent()) {
            ProgramMaster p = program.get();
            if ("INVOICE_DISCOUNTING".equalsIgnoreCase(p.getProductType())
                    && hasText(p.getEncoreProductCode())) {
                return Optional.of(new LmsProductMappingResolution(
                        p.getEncoreProductCode().trim(),
                        LmsProductMappingResolution.SOURCE_PROGRAM,
                        null,
                        null,
                        app.getLoanProduct(),
                        borrowerTypeName(app)));
            }
        }
        if (hasText(app.getLmsProductCode())) {
            Optional<WorkflowConfig> wf = activeWorkflowConfigService.findActiveForApplication(app);
            return Optional.of(new LmsProductMappingResolution(
                    app.getLmsProductCode().trim(),
                    LmsProductMappingResolution.SOURCE_APPLICATION,
                    wf.map(WorkflowConfig::getId).orElse(app.getWorkflowId()),
                    wf.map(WorkflowConfig::getVersion).orElse(null),
                    app.getLoanProduct(),
                    borrowerTypeName(app)));
        }
        Optional<WorkflowConfig> wf = activeWorkflowConfigService.findActiveForApplication(app);
        if (wf.isPresent() && hasText(wf.get().getLmsProductCode())) {
            WorkflowConfig config = wf.get();
            return Optional.of(new LmsProductMappingResolution(
                    config.getLmsProductCode().trim(),
                    LmsProductMappingResolution.SOURCE_WORKFLOW,
                    config.getId(),
                    config.getVersion(),
                    app.getLoanProduct(),
                    borrowerTypeName(app)));
        }
        return Optional.empty();
    }

    public String resolveTenureUnit(LoanApplication app) {
        if (app == null) {
            return DEFAULT_TENURE_UNIT;
        }
        Optional<ProgramMaster> program = resolveProgramForApplication(app);
        if (program.isPresent()) {
            ProgramMaster p = program.get();
            if ("INVOICE_DISCOUNTING".equalsIgnoreCase(p.getProductType())
                    && hasText(p.getEncoreProductCode())) {
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
     * Maps UI/API tags (DAY, MONTH, WEEK or Day, Month, Week) to Encore title-case strings.
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

    private BusinessRuleException missingMapping(LoanApplication app) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        if (app != null) {
            ctx.put("applicationId", app.getId() == null ? null : app.getId().toString());
            ctx.put("applicationNumber", app.getApplicationNumber());
            ctx.put("loanProduct", app.getLoanProduct());
            ctx.put("borrowerType", borrowerTypeName(app));
            ctx.put("workflowId", app.getWorkflowId() == null ? null : app.getWorkflowId().toString());
            activeWorkflowConfigService.findActiveForApplication(app).ifPresent(wf -> {
                ctx.put("resolvedWorkflowId", wf.getId() == null ? null : wf.getId().toString());
                ctx.put("resolvedWorkflowVersion", wf.getVersion());
            });
        }
        ctx.put("mappingSource", null);
        ctx.put("allowCanonicalAuthority", false);
        log.warn("[LMS-PRODUCT-MAPPING] Missing LMS product mapping — openLoanAccount blocked | ctx={}", ctx);
        return new BusinessRuleException(
                "No LMS product mapping is configured for this application.",
                REASON_LMS_PRODUCT_MAPPING_MISSING,
                "OPEN_LOAN_ACCOUNT",
                ctx);
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

    private static String borrowerTypeName(LoanApplication app) {
        return app.getBorrowerType() == null ? null : app.getBorrowerType().name();
    }
}
