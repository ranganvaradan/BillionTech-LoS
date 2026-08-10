package com.los.core.creditintelligence.gst.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_gst_period_financials")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiGstPeriodFinancials {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "return_period_id", nullable = false)
    private UUID returnPeriodId;

    @Column(name = "return_type", nullable = false, length = 40)
    private String returnType;

    @Column(name = "taxable_turnover", precision = 18, scale = 2)
    private BigDecimal taxableTurnover;

    @Column(name = "zero_rated_turnover", precision = 18, scale = 2)
    private BigDecimal zeroRatedTurnover;

    @Column(name = "exempt_turnover", precision = 18, scale = 2)
    private BigDecimal exemptTurnover;

    @Column(name = "non_gst_turnover", precision = 18, scale = 2)
    private BigDecimal nonGstTurnover;

    @Column(name = "outward_taxable_supplies", precision = 18, scale = 2)
    private BigDecimal outwardTaxableSupplies;

    @Column(name = "reverse_charge_supplies", precision = 18, scale = 2)
    private BigDecimal reverseChargeSupplies;

    @Column(name = "tax_liability", precision = 18, scale = 2)
    private BigDecimal taxLiability;

    @Column(name = "igst", precision = 18, scale = 2)
    private BigDecimal igst;

    @Column(name = "cgst", precision = 18, scale = 2)
    private BigDecimal cgst;

    @Column(name = "sgst", precision = 18, scale = 2)
    private BigDecimal sgst;

    @Column(name = "cess", precision = 18, scale = 2)
    private BigDecimal cess;

    @Column(name = "tax_paid_cash", precision = 18, scale = 2)
    private BigDecimal taxPaidCash;

    @Column(name = "tax_paid_itc", precision = 18, scale = 2)
    private BigDecimal taxPaidItc;

    @Column(name = "itc_available", precision = 18, scale = 2)
    private BigDecimal itcAvailable;

    @Column(name = "itc_claimed", precision = 18, scale = 2)
    private BigDecimal itcClaimed;

    @Column(name = "credit_note_value", precision = 18, scale = 2)
    private BigDecimal creditNoteValue;

    @Column(name = "debit_note_value", precision = 18, scale = 2)
    private BigDecimal debitNoteValue;

    @Column(name = "b2b_invoice_count")
    private Integer b2bInvoiceCount;

    @Column(name = "b2c_invoice_count")
    private Integer b2cInvoiceCount;

    @Column(name = "einvoice_value", precision = 18, scale = 2)
    private BigDecimal einvoiceValue;

    @Column(name = "eway_bill_value", precision = 18, scale = 2)
    private BigDecimal ewayBillValue;

    @Column(name = "extraction_quality", nullable = false, length = 40)
    @Builder.Default
    private String extractionQuality = "OK";

    @Column(name = "source_reference", length = 300)
    private String sourceReference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
