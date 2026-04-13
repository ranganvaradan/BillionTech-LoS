package com.los.core.service.transaction;

import com.los.core.model.dto.response.TransactionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public interface ITransactionService {

    TransactionResponse triggerDisbursement(UUID applicationId, Map<String, Object> disbursementInfo);

    TransactionResponse recordRepayment(UUID applicationId, BigDecimal amount, String referenceNo, Map<String, Object> meta);

    Page<TransactionResponse> getTransactionHistory(UUID applicationId, Pageable pageable);

    BigDecimal getOutstandingBalance(UUID applicationId);
}
