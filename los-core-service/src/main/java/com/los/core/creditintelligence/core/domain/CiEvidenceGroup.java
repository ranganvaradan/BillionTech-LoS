package com.los.core.creditintelligence.core.domain;

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
@Table(name = "ci_evidence_group")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiEvidenceGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "group_type", nullable = false, length = 80)
    private String groupType;

    @Column(name = "member_count", nullable = false)
    @Builder.Default
    private int memberCount = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "member_ids", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> memberIds = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> summary = Map.of();

    /** Optional subject entity this evidence group is about (V98). */
    @Column(name = "subject_entity_id")
    private UUID subjectEntityId;

    /** Optional source family, e.g. BUREAU / GST / BANKING / TAX (V98). */
    @Column(name = "source_family", length = 40)
    private String sourceFamily;

    /** Optional evidence type discriminator (V98). */
    @Column(name = "evidence_type", length = 80)
    private String evidenceType;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
