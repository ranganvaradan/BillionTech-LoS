package com.billiontech.bankstatement.model.dto.response;

import com.billiontech.bankstatement.model.enums.ParsingStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatementResponse {
    private Long id;
    private String fileName;
    private String accountHolderName;
    private String accountNumberMasked;
    private String bankName;
    private String bankCode;
    private String ifscCode;
    private String branchName;
    private String accountType;
    private LocalDate statementFromDate;
    private LocalDate statementToDate;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private Integer totalTransactions;
    private BigDecimal totalCreditAmount;
    private BigDecimal totalDebitAmount;
    private ParsingStatus parsingStatus;
    private String parsingError;
    private String tamperCheckStatus;
    private String applicationId;
    private String batchId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
