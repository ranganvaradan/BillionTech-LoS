package com.los.core.model.entity;

import com.los.core.model.enums.KycStepType;
import com.los.core.model.enums.ProviderType;
import com.los.core.model.enums.StepOutcome;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "kyc_step_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycStepResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private KycStepType stepType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProviderType provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StepOutcome outcome = StepOutcome.PENDING;

    @Column
    private double confidenceScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> parsedData;

    @Column(columnDefinition = "text")
    private String rawResponse;

    @Column(length = 100)
    private String transactionId;

    @Column(length = 500)
    private String errorMessage;

    @Column
    private boolean overridden;

    @Column(length = 500)
    private String overrideReason;

    private UUID overrideBy;

    @Column
    private int attemptNumber;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    private Instant completedAt;
}
