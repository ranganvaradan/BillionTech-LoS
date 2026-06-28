package com.los.core.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "kfs_templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KfsTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String loanProduct;

    @Column(nullable = false, length = 20)
    private String version;

    @Column(columnDefinition = "text", nullable = false)
    private String templateContent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> defaultCharges;

    @Column(length = 500)
    private String grievanceOfficerDetails;

    @Column(length = 500)
    private String lspDetails;

    @Column(length = 500)
    private String rbiCircularRef;

    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
