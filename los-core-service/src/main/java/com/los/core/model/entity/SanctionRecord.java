package com.los.core.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sanction_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SanctionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "approved_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal approvedAmount;

    @Column(name = "approved_tenure", nullable = false)
    private Integer approvedTenure;

    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column(name = "processing_fee", precision = 15, scale = 2)
    private BigDecimal processingFee;

    @Column(name = "conditions_text", columnDefinition = "text")
    private String conditionsText;

    @Column(columnDefinition = "text")
    private String remarks;

    @Column(name = "approved_by", length = 200)
    private String approvedBy;

    /** Own Book vs Colending — audit copy of the value captured at sanction. */
    @Column(name = "book_type", length = 20)
    private String bookType;

    @Column(name = "sanction_pdf_path", length = 500)
    private String sanctionPdfPath;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
