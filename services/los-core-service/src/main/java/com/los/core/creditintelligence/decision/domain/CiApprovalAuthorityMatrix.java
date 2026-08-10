package com.los.core.creditintelligence.decision.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_approval_authority_matrix")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiApprovalAuthorityMatrix {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "matrix_code", nullable = false, length = 120)
    private String matrixCode;

    @Column(name = "version", nullable = false, length = 40)
    private String version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> content = new LinkedHashMap<>();

    @Column(name = "content_hash", length = 128)
    private String contentHash;

    @Column(name = "status", nullable = false, length = 40)
    @Builder.Default
    private String status = "SHADOW";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
