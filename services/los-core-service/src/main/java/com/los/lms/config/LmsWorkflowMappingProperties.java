package com.los.lms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Feature flag for resolving Encore {@code productCode} from {@code workflow_lms_product_mapping}
 * instead of echoing {@code loan_applications.loan_product} during LMS handover.
 */
@Data
@Component
@ConfigurationProperties(prefix = "los.lms.workflow-mapping")
public class LmsWorkflowMappingProperties {

    /**
     * When false (default), {@link com.los.lms.service.WorkflowLmsProductResolver} returns the fallback
     * (typically the loan product label), preserving legacy behaviour.
     */
    private boolean enabled = false;
}
