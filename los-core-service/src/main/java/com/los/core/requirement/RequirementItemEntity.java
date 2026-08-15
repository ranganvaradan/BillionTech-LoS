package com.los.core.requirement;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "requirement_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequirementItemEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private RequirementPlanEntity plan;

    @Column(name = "item_key", nullable = false, length = 120)
    private String itemKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "requirement_type", nullable = false, length = 40)
    private RequirementType requirementType;

    @Enumerated(EnumType.STRING)
    @Column(name = "requirement_class", nullable = false, length = 40)
    private RequirementClass requirementClass;

    @Enumerated(EnumType.STRING)
    @Column(name = "phase", length = 40)
    private RequirementPhase phase;

    @Column(name = "canonical_parameter_id", length = 120)
    private String canonicalParameterId;

    @Column(name = "business_name", length = 200)
    private String businessName;

    @Column(nullable = false)
    @Builder.Default
    private boolean required = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_fulfilment_state", nullable = false, length = 40)
    @Builder.Default
    private CustomerFulfilmentState customerFulfilmentState = CustomerFulfilmentState.REQUIRED;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_readiness_state", nullable = false, length = 40)
    @Builder.Default
    private DataReadinessState dataReadinessState = DataReadinessState.NOT_AVAILABLE;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_acquisition_state", nullable = false, length = 40)
    @Builder.Default
    private SourceAcquisitionState sourceAcquisitionState = SourceAcquisitionState.NOT_STARTED;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_fulfilment_modes", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<FulfilmentMode> allowedFulfilmentModes = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfilment_mode_used", length = 40)
    private FulfilmentMode fulfilmentModeUsed;

    @Column(name = "evidence_ref", length = 255)
    private String evidenceRef;

    @Column(name = "document_ref", length = 255)
    private String documentRef;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "policy_rule_refs", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<String> policyRuleRefs = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_hints", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> sourceHints = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provenance", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> provenance = new LinkedHashMap<>();

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "provided_at")
    private Instant providedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (allowedFulfilmentModes == null) {
            allowedFulfilmentModes = new ArrayList<>();
        }
        if (policyRuleRefs == null) {
            policyRuleRefs = new ArrayList<>();
        }
        if (sourceHints == null) {
            sourceHints = new LinkedHashMap<>();
        }
        if (provenance == null) {
            provenance = new LinkedHashMap<>();
        }
        if (customerFulfilmentState == null) {
            customerFulfilmentState = CustomerFulfilmentState.REQUIRED;
        }
        if (dataReadinessState == null) {
            dataReadinessState = DataReadinessState.NOT_AVAILABLE;
        }
        if (sourceAcquisitionState == null) {
            sourceAcquisitionState = SourceAcquisitionState.NOT_STARTED;
        }
    }

    public boolean allowsOnly(FulfilmentMode mode) {
        return allowedFulfilmentModes != null
                && allowedFulfilmentModes.size() == 1
                && allowedFulfilmentModes.contains(mode);
    }

    public boolean allows(FulfilmentMode mode) {
        return allowedFulfilmentModes != null && allowedFulfilmentModes.contains(mode);
    }
}
