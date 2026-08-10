package com.los.core.model.dto.request;

import com.los.core.model.enums.BorrowerType;
import com.los.core.model.enums.IntakeSegment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class WorkflowConfigRequest {

    @NotBlank(message = "Workflow name is required")
    private String name;

    @NotNull(message = "Borrower type is required")
    private BorrowerType borrowerType;

    @NotBlank(message = "Loan product is required")
    private String loanProduct;

    /** Encore LMS product code default (e.g. IPPOPAYM01). */
    private String lmsProductCode;

    /** Encore tenure unit default (Day, Month, Week). */
    private String lmsTenureUnit;

    /** Defaults to {@link IntakeSegment#BORROWER} when omitted. */
    private IntakeSegment intakeSegment;

    /** Optional anchor identity step field definitions (JSON array). */
    private List<Map<String, Object>> intakeIdentitySchema;

    /** Workflow-driven intake rules (policy, age, tenure, OR groups, standalone documents). */
    private Map<String, Object> intakeConfig;

    private Boolean bureauEnabled;
    private Boolean autoPullBureauAfterKycSuccess;

    private List<Map<String, Object>> steps;
    private List<Map<String, Object>> processNotificationMappings;
    private List<Map<String, Object>> manualOverridePolicies;
    private List<Map<String, Object>> conditionalRules;
    private List<Map<String, Object>> vkycTriggerCondition;
    private String workflowPosition;
}
