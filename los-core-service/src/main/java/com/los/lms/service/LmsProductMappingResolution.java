package com.los.lms.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Evidence for LMS product-code resolution used by openLoanAccount / Product Configuration correlation.
 */
public record LmsProductMappingResolution(
        String lmsProductCode,
        String mappingSource,
        UUID workflowId,
        Integer workflowVersion,
        String loanProduct,
        String borrowerType,
        UUID externalProductMappingId,
        Integer externalProductMappingVersion,
        String externalSystem
) {
    public static final String SOURCE_PROGRAM = "PROGRAM_ENCORE_PRODUCT_CODE";
    public static final String SOURCE_APPLICATION = "APPLICATION_LMS_PRODUCT_CODE";
    public static final String SOURCE_WORKFLOW = "WORKFLOW_LMS_PRODUCT_CODE";
    public static final String SOURCE_EXTERNAL_PINNED_APPLICATION = "APPLICATION_PINNED_EXTERNAL_PRODUCT_MAPPING";

    public Map<String, Object> toEvidenceMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lmsProductCode", lmsProductCode);
        m.put("mappingSource", mappingSource);
        m.put("workflowId", workflowId == null ? null : workflowId.toString());
        m.put("workflowVersion", workflowVersion);
        m.put("loanProduct", loanProduct);
        m.put("borrowerType", borrowerType);
        m.put("externalProductMappingId", externalProductMappingId == null ? null : externalProductMappingId.toString());
        m.put("externalProductMappingVersion", externalProductMappingVersion);
        m.put("externalSystem", externalSystem);
        m.put("allowCanonicalAuthority", false);
        return m;
    }
}
