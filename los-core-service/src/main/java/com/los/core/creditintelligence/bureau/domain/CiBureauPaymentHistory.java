package com.los.core.creditintelligence.bureau.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ci_bureau_payment_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiBureauPaymentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tradeline_id", nullable = false)
    private UUID tradelineId;

    @Column(name = "month", nullable = false)
    private LocalDate month;

    @Column(name = "dpd")
    private Integer dpd;

    @Column(name = "status", nullable = false, length = 40)
    private String status;

    @Column(name = "provider_raw_status", length = 80)
    private String providerRawStatus;

    @Column(name = "estimated", nullable = false)
    @Builder.Default
    private boolean estimated = false;

    @Column(name = "suit_filed_status", length = 40)
    private String suitFiledStatus;

    @Column(name = "asset_classification_status", length = 40)
    private String assetClassificationStatus;

    @Column(name = "source_reference", length = 300)
    private String sourceReference;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
