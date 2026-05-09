package com.los.core.model.entity;

import com.los.core.model.enums.ApplicationStatus;
import com.los.core.model.enums.BorrowerType;
import com.los.core.model.enums.VkycStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "loan_applications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 30)
    private String applicationNumber;

    @Column(nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BorrowerType borrowerType;

    @Column(nullable = false, length = 50)
    private String loanProduct;

    @Column(precision = 15, scale = 2)
    private BigDecimal requestedAmount;

    @Column(precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column
    private Integer tenureMonths;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ApplicationStatus status = ApplicationStatus.DRAFT;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> personalInfo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> businessInfo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> financialInfo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> collateralInfo;

    @Column(length = 500)
    private String remarks;

    private UUID assignedTo;

    private Instant slaDeadline;

    private Instant currentStepStartedAt;

    @Builder.Default
    private boolean escalated = false;

    private Instant escalatedAt;

    // --- Flow orchestration fields (V6 migration) ---

    @Column(precision = 15, scale = 2)
    private BigDecimal sanctionedAmount;

    @Column(precision = 5, scale = 2)
    private BigDecimal approvedRate;

    @Column(precision = 15, scale = 2)
    private BigDecimal disbursedAmount;

    private Instant disbursedAt;

    @Column(length = 100)
    private String lmsReferenceId;

    @Column(length = 100)
    private String esignTransactionId;

    private Boolean vkycRequired;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private VkycStatus vkycStatus;

    private Instant vkycCompletedAt;

    private UUID vkycAgentId;

    private UUID vkycAuditorId;

    @Column(length = 150)
    private String vkycReferenceId;

    @Column(columnDefinition = "text")
    private String vkycUrl;
    @Column(length = 120)
    private String vkycTransactionId;

    private Instant vkycUrlGeneratedAt;

    private Instant vkycUrlExpiryAt;

    private Instant vkycLastResentAt;

    private Integer vkycResendCount;

    private Boolean vkycEmailSent;

    private Instant vkycEmailSentAt;

    private UUID vkycGeneratedBy;
    @Column(length = 80)
    private String vkycLastEvent;
    @Column(columnDefinition = "text")
    private String vkycEventPayload;
    @Column(columnDefinition = "text")
    private String vkycResultPayload;
    @Column(length = 200)
    private String vkycAgentName;
    private Instant vkycAgentUpdatedOn;
    private Instant vkycCompletedOn;
    @Column(columnDefinition = "text")
    private String vkycVideoUrl;
    @Column(columnDefinition = "text")
    private String vkycPanImageUrl;
    @Column(columnDefinition = "text")
    private String vkycFaceImageUrl;
    private Boolean amlHit;

    private Integer bureauScore;

    private Integer manualBureauScore;

    @Column(columnDefinition = "text")
    private String manualBureauRemarks;

    private UUID manualBureauDocumentId;

    @Column(length = 30)
    private String creditDecision;

    private Integer creditRiskScore;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    private Instant submittedAt;
}
