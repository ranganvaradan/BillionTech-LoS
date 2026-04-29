package com.billiontech.bankstatement.service.extraction;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParsedStatement {
    private String accountHolderName;
    private String accountNumber;
    private String bankName;
    private String bankCode;
    private String ifscCode;
    private String branchName;
    private String accountType;
    private LocalDate statementFromDate;
    private LocalDate statementToDate;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    @Builder.Default
    private List<ParsedTransaction> transactions = new ArrayList<>();
}
