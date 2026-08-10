package com.los.core.creditintelligence.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ci_fact_definition")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiFactDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "canonical_path", nullable = false, length = 200)
    private String canonicalPath;

    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "value_type", nullable = false, length = 40)
    private String valueType;

    @Column(name = "domain", nullable = false, length = 64)
    private String domain;

    @Column(name = "repeatable", nullable = false)
    @Builder.Default
    private boolean repeatable = false;

    @Column(name = "sensitive", nullable = false)
    @Builder.Default
    private boolean sensitive = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_classifications", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> allowedClassifications = List.of();

    @Column(name = "status", nullable = false, length = 40)
    @Builder.Default
    private String status = "ACTIVE";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
