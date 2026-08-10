package com.los.core.creditintelligence.validation.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_lender_alias")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiLenderAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "lender_identity_id", nullable = false)
    private UUID lenderIdentityId;

    @Column(name = "alias_text", nullable = false, length = 300)
    private String aliasText;

    @Column(name = "alias_source", nullable = false, length = 40)
    @Builder.Default
    private String aliasSource = "MANUAL";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
