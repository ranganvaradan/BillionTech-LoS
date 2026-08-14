package com.los.core.customercategory;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "policy_set")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicySetEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConfigLifecycleStatus status;

    @Column(name = "primary_rule_set_id", nullable = false)
    private UUID primaryRuleSetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "additional_rule_set_ids", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<UUID> additionalRuleSetIds = new ArrayList<>();

    @Column(name = "scorecard_id")
    private UUID scorecardId;

    @Column(name = "seed_source_rule_set_id")
    private UUID seedSourceRuleSetId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 120)
    private String updatedBy;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "activated_by", length = 120)
    private String activatedBy;

    @Column(name = "retired_at")
    private Instant retiredAt;

    @Column(name = "retired_by", length = 120)
    private String retiredBy;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (additionalRuleSetIds == null) {
            additionalRuleSetIds = new ArrayList<>();
        }
    }
}
