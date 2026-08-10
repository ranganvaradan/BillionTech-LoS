package com.los.core.creditintelligence.bureau.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ci_bureau_product_mapping")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiBureauProductMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "provider_code", nullable = false, length = 64)
    private String providerCode;

    @Column(name = "provider_product_code", length = 120)
    private String providerProductCode;

    @Column(name = "provider_product_desc", length = 200)
    private String providerProductDesc;

    @Column(name = "canonical_category", nullable = false, length = 64)
    private String canonicalCategory;

    @Column(name = "secured")
    private Boolean secured;

    @Column(name = "revolving")
    private Boolean revolving;

    @Column(name = "applicability", nullable = false, length = 40)
    @Builder.Default
    private String applicability = "BOTH";

    @Column(name = "mapping_version", nullable = false, length = 40)
    private String mappingVersion;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
