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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> steps;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

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

    /**
     * BR-6.2: Parallel step groups — steps within a group execute concurrently.
     * Example: [{"group": "KYC_PARALLEL", "steps": ["AADHAAR_OTP", "PAN_VERIFY", "GSTIN_VERIFY"]}]
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> parallelGroups;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    private Instant updatedAt;
}
