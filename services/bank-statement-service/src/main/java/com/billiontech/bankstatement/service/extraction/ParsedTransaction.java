package com.billiontech.bankstatement.service.extraction;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParsedTransaction {
    private LocalDate transactionDate;
    private LocalDate valueDate;
    private String narration;
    private String referenceNumber;
    private BigDecimal debitAmount;
    private BigDecimal creditAmount;
    private BigDecimal runningBalance;
}
