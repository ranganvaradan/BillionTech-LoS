package com.los.core.creditintelligence.policy.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_policy_package_test_run")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiPolicyPackageTestRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "package_id", nullable = false)
    private UUID packageId;

    @Column(name = "test_suite_hash", length = 128)
    private String testSuiteHash;

    @Column(name = "passed", nullable = false)
    @Builder.Default
    private Boolean passed = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "results", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> results = Map.of();

    @Column(name = "ran_at", nullable = false)
    private Instant ranAt;

    @Column(name = "ran_by", length = 120)
    private String ranBy;
}
