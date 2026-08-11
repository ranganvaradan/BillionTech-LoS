package com.los.core.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "workflow_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 30)
    private String borrowerType;

    @Column(nullable = false, length = 50)
    private String loanProduct;

    /** Encore LMS product code for applications using this workflow (required before openLoanAccount). */
    @Column(name = "lms_product_code", length = 50)
    private String lmsProductCode;

    /** Encore tenure unit default (Day, Month, Week, etc.). */
    @Column(name = "lms_tenure_unit", length = 20)
    @Builder.Default
    private String lmsTenureUnit = "Month";

    @Column(name = "intake_segment", nullable = false, length = 20)
    @Builder.Default
    private String intakeSegment = "BORROWER";

    /**
     * Optional JSON array of field definitions for anchor identity intake (key, label, required, visible, inputType).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "intake_identity_schema", columnDefinition = "jsonb")
    private List<Map<String, Object>> intakeIdentitySchema;

    /**
     * Workflow-driven intake rules: policy, personal fields, age/tenure, OR mandatory groups, standalone documents.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "intake_config", columnDefinition = "jsonb")
    private Map<String, Object> intakeConfig;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> steps;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "bureau_enabled", nullable = false)
    @Builder.Default
    private boolean bureauEnabled = true;

    @Column(name = "auto_pull_bureau_after_kyc_success", nullable = false)
    @Builder.Default
    private boolean autoPullBureauAfterKycSuccess = true;

    @Column
    private int version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Integer> slaHoursPerStep;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> escalationEmails;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> conditionalRules;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> vkycTriggerCondition;

    @Column(length = 50)
    private String workflowPosition;

    /**
     * BR-6.2: Parallel step groups — steps within a group execute concurrently.
     * Example: [{"group": "KYC_PARALLEL", "steps": ["AADHAAR_OTP", "PAN_VERIFY", "GSTIN_VERIFY"]}]
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> parallelGroups;

    /**
     * Business-friendly notification configuration at process/event level.
     * Backward compatibility: legacy {@code steps[].notifications} remains supported by resolver fallback.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> processNotificationMappings;

    /**
     * Configurable manual-override policy definitions.
     * Example keys: process_code, failure_code, override_allowed, allowed_roles,
     * requires_reason, requires_remarks, requires_approval, override_to_status, is_active.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> manualOverridePolicies;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    private Instant updatedAt;
}
