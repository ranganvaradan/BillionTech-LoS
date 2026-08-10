package com.los.lms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflow_lms_product_mapping",
        uniqueConstraints = @UniqueConstraint(name = "uq_workflow_lms_mapping_bt_lp",
                columnNames = {"borrower_type", "loan_product"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowLmsProductMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "borrower_type", nullable = false, length = 30)
    private String borrowerType;

    @Column(name = "loan_product", nullable = false, length = 100)
    private String loanProduct;

    @Column(name = "encore_product_code", nullable = false, length = 100)
    private String encoreProductCode;

    @Column(name = "encore_product_type", length = 50)
    private String encoreProductType;

    @Column(name = "branch_set_code", length = 50)
    private String branchSetCode;

    @Column(name = "partner_code", length = 100)
    private String partnerCode;

    @Column(name = "tenure_unit", length = 20)
    private String tenureUnit;

    @Column(name = "penal_interest_rate", precision = 8, scale = 4)
    private BigDecimal penalInterestRate;

    @Column(name = "moratorium_type", length = 30)
    private String moratoriumType;

    @Column(name = "moratorium_period_magnitude")
    private Integer moratoriumPeriodMagnitude;

    @Column(name = "moratorium_period_unit", length = 20)
    private String moratoriumPeriodUnit;

    @Column(name = "co_lending_applicable")
    @Builder.Default
    private Boolean coLendingApplicable = false;

    @Column(name = "mapping_source", nullable = false, length = 20)
    @Builder.Default
    private String mappingSource = "HEURISTIC";

    @Column(name = "confidence", precision = 4, scale = 2)
    private BigDecimal confidence;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
