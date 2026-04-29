package com.billiontech.bankstatement.model.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionResponse {
    private Long id;
    private LocalDate transactionDate;
    private LocalDate valueDate;
    private String narration;
    private String referenceNumber;
    private BigDecimal debitAmount;
    private BigDecimal creditAmount;
    private BigDecimal runningBalance;
    private String category;
    private String subCategory;
    private String channel;
    private String counterpartyName;
    private Boolean isBounce;
    private Boolean isReversal;
    private Boolean isCircular;
}
