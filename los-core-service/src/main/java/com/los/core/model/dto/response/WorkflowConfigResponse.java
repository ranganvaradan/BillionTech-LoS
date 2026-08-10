package com.los.core.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class WorkflowConfigResponse {

    private UUID id;
    private String name;
    private String borrowerType;
    private String loanProduct;
    private String lmsProductCode;
    private String lmsTenureUnit;
    private String intakeSegment;
    private List<Map<String, Object>> intakeIdentitySchema;
    private Map<String, Object> intakeConfig;
    private boolean bureauEnabled;
    private boolean autoPullBureauAfterKycSuccess;
    private List<Map<String, Object>> steps;
    private List<Map<String, Object>> processNotificationMappings;
    private List<Map<String, Object>> manualOverridePolicies;
    private List<Map<String, Object>> conditionalRules;
    private List<Map<String, Object>> vkycTriggerCondition;
    private String workflowPosition;
    private boolean active;
    private int version;
    private Instant createdAt;
}
