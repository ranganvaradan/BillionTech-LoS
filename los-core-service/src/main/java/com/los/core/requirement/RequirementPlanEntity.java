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
@Table(name = "requirement_plan")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequirementPlanEntity {

    @Id
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "customer_category_id")
    private UUID customerCategoryId;

    @Column(name = "policy_document_id")
    private UUID policyDocumentId;

    @Column(name = "policy_applicability_id")
    private UUID policyApplicabilityId;

    @Column(name = "workflow_id")
    private UUID workflowId;

    @Column(name = "plan_version", nullable = false)
    @Builder.Default
    private int planVersion = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private RequirementPlanStatus status = RequirementPlanStatus.DRAFT;

    @Column(name = "plan_hash", length = 64)
    private String planHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC, createdAt ASC")
    @Builder.Default
    private List<RequirementItemEntity> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void addItem(RequirementItemEntity item) {
        items.add(item);
        item.setPlan(this);
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (metadata == null) {
            metadata = new LinkedHashMap<>();
        }
        if (items == null) {
            items = new ArrayList<>();
        }
        if (status == null) {
            status = RequirementPlanStatus.DRAFT;
        }
    }
}
