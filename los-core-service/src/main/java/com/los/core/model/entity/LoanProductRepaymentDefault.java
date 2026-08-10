package com.los.core.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "loan_product_repayment_defaults")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanProductRepaymentDefault {

    @Id
    @Column(name = "loan_product", nullable = false, length = 80)
    private String loanProduct;

    @Column(name = "repayment_mechanism", nullable = false, length = 30)
    @Builder.Default
    private String repaymentMechanism = "SMART_COLLECT";

    @Column(name = "pg_provider_code", length = 30)
    private String pgProviderCode;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;
}
