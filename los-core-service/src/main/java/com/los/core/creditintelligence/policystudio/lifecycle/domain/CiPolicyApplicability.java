package com.los.core.creditintelligence.policystudio.lifecycle.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_policy_applicability")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiPolicyApplicability {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "policy_document_id")
    private UUID policyDocumentId;

    @Column(name = "draft_package_id")
    private UUID draftPackageId;

    @Column(name = "executable_package_id")
    private UUID executablePackageId;

    @Column(name = "policy_version_id")
    private UUID policyVersionId;

    @Column(name = "policy_name", nullable = false, length = 300)
    private String policyName;

    @Column(name = "policy_version_label", nullable = false, length = 40)
    private String policyVersionLabel;

    @Column(name = "policy_type", nullable = false, length = 80)
    @Builder.Default
    private String policyType = "CREDIT_POLICY";

    @Column(name = "business_status", nullable = false, length = 40)
    @Builder.Default
    private String businessStatus = "DRAFT";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "products", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> products = new ArrayList<>();

    @Column(name = "facility_type", length = 80)
    private String facilityType;

    @Column(name = "customer_segment", length = 120)
    private String customerSegment;

    @Column(name = "borrower_type", length = 80)
    private String borrowerType;

    /** Empty = ALL borrower types. Prefer over legacy {@link #borrowerType}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "borrower_types", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> borrowerTypes = new ArrayList<>();

    @Column(name = "secured_unsecured", length = 40)
    private String securedUnsecured;

    @Column(name = "program_scheme", length = 120)
    private String programScheme;

    @Column(name = "min_loan_amount", precision = 18, scale = 2)
    private BigDecimal minLoanAmount;

    @Column(name = "max_loan_amount", precision = 18, scale = 2)
    private BigDecimal maxLoanAmount;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_until")
    private LocalDate effectiveUntil;

    @Column(name = "replaces_version", length = 40)
    private String replacesVersion;

    @Column(name = "replaces_applicability_id")
    private UUID replacesApplicabilityId;

    @Column(name = "reason_for_change", columnDefinition = "TEXT")
    private String reasonForChange;

    @Column(name = "approved_by", length = 120)
    private String approvedBy;

    @Column(name = "checker", length = 120)
    private String checker;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "data_readiness_status", length = 40)
    private String dataReadinessStatus;

    @Column(name = "tests_status", length = 40)
    private String testsStatus;

    @Column(name = "simulation_review_status", length = 40)
    private String simulationReviewStatus;

    @Column(name = "content_immutable", nullable = false)
    @Builder.Default
    private Boolean contentImmutable = false;

    @Column(name = "production_authority_enabled", nullable = false)
    @Builder.Default
    private Boolean productionAuthorityEnabled = false;

    /** PROPER_IMMUTABLE_PACKAGE_LINK | DEMO_ONLY_NOT_ROUTABLE | DEMO_ONLY_UNLINKED | INVALID | UNLINKED */
    @Column(name = "linkage_class", nullable = false, length = 60)
    @Builder.Default
    private String linkageClass = "UNLINKED";

    /** ELIGIBLE_FOR_SHADOW_ROUTING | NOT_ELIGIBLE_FOR_SHADOW_ROUTING | DEMO_ONLY_NOT_ROUTABLE | POLICY_PACKAGE_NOT_EXECUTABLE */
    @Column(name = "shadow_eligibility", nullable = false, length = 60)
    @Builder.Default
    private String shadowEligibility = "NOT_ELIGIBLE_FOR_SHADOW_ROUTING";

    @Column(name = "content_hash", length = 128)
    private String contentHash;

    @Column(name = "package_content_hash", length = 128)
    private String packageContentHash;

    @Column(name = "shadow_routable", nullable = false)
    @Builder.Default
    private Boolean shadowRoutable = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "eligibility_detail", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> eligibilityDetail = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isInForceOn(LocalDate asOf) {
        if (asOf == null || effectiveFrom == null) {
            return false;
        }
        if (asOf.isBefore(effectiveFrom)) {
            return false;
        }
        return effectiveUntil == null || !asOf.isAfter(effectiveUntil);
    }
}
