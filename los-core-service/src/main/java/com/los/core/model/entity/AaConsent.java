package com.los.core.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "aa_consents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AaConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID applicationId;

    @Column(nullable = false)
    private UUID customerId;

    @Column(nullable = false, unique = true, length = 100)
    private String consentHandle;

    @Column(length = 100)
    private String consentId;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "PENDING";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> fiTypes;

    @Column(nullable = false)
    private Instant consentStartDate;

    @Column(nullable = false)
    private Instant consentExpiryDate;

    @Column(length = 50)
    private String fetchFrequency;

    @Column(length = 50)
    private String consentMode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> purposeInfo;

    @Column(length = 200)
    private String aaName;

    private Instant approvedAt;

    private Instant revokedAt;

    @Column(length = 500)
    private String revokeReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> fetchedDataSummary;

    private Instant dataFetchedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
