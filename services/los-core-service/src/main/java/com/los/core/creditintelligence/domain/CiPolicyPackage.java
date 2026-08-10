package com.los.core.creditintelligence.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ci_policy_package")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiPolicyPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "product_code", nullable = false, length = 80)
    private String productCode;

    @Column(name = "lender_id")
    private UUID lenderId;

    @Column(name = "policy_identifier", nullable = false, length = 120)
    private String policyIdentifier;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "status", nullable = false, length = 40)
    @Builder.Default
    private String status = "DRAFT";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 64)
    private String createdBy;
}
