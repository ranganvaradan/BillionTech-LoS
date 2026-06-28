package com.los.core.payment.model;

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
@Table(name = "los_loan_payment_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LosLoanPaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "pg_transaction_ref", nullable = false, unique = true, length = 100)
    private String pgTransactionRef;

    @Column(name = "borrower_user_id", nullable = false)
    private UUID borrowerUserId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String gateway = "PAYU";

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "INITIATED";

    @Column(name = "payu_mihpayid", length = 100)
    private String payuMihpayid;

    @Column(name = "portal_source", nullable = false, length = 30)
    @Builder.Default
    private String portalSource = "LOS_LOAN";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_callback_json", columnDefinition = "jsonb")
    private Map<String, Object> rawCallbackJson;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
