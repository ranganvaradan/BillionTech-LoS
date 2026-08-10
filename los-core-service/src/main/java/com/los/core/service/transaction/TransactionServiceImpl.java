package com.los.core.service.transaction;

import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.dto.response.TransactionResponse;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.Transaction;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.repository.TransactionRepository;
import com.los.core.service.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements ITransactionService {

    private final TransactionRepository transactionRepository;
    private final LoanApplicationRepository applicationRepository;
    private final AuditService auditService;

    @Override
    @Transactional
    public TransactionResponse triggerDisbursement(UUID applicationId, BigDecimal amount, Map<String, Object> metadata) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));

        if (app.getStatus() != ApplicationStatus.DISBURSEMENT_PENDING) {
            throw new BusinessRuleException("Application must be in DISBURSEMENT_PENDING status. Current: " + app.getStatus());
        }

        String referenceNumber = "DISB-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();

        Transaction transaction = Transaction.builder()
                .applicationId(applicationId)
                .transactionType("DISBURSEMENT")
                .amount(amount)
                .status("INITIATED")
                .referenceNumber(referenceNumber)
                .metadata(metadata)
                .build();

        transaction = transactionRepository.save(transaction);

        // Simulate processing — in production, integrate with payment gateway
        transaction.setStatus("COMPLETED");
        transaction.setUtrNumber("UTR" + System.currentTimeMillis());
        transaction.setCompletedAt(Instant.now());
        transaction = transactionRepository.save(transaction);

        // Transition application to DISBURSED
        app.setStatus(ApplicationStatus.DISBURSED);
        applicationRepository.save(app);

        log.info("Disbursement completed for application {}: {} (ref: {})",
                applicationId, amount, referenceNumber);

        auditService.logEvent(applicationId, "TRANSACTION", "DISBURSEMENT",
                null, Map.of("status", "DISBURSEMENT_PENDING"),
                Map.of("status", "DISBURSED", "amount", amount, "referenceNumber", referenceNumber),
                "Loan disbursed: " + amount);

        return toResponse(transaction);
    }

    @Override
    @Transactional
    public TransactionResponse recordRepayment(UUID applicationId, BigDecimal amount, String utrNumber, Map<String, Object> metadata) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));

        if (app.getStatus() != ApplicationStatus.DISBURSED) {
            throw new BusinessRuleException("Repayments only accepted for DISBURSED applications. Current: " + app.getStatus());
        }

        String referenceNumber = "RPMT-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();

        Transaction transaction = Transaction.builder()
                .applicationId(applicationId)
                .transactionType("REPAYMENT")
                .amount(amount)
                .status("COMPLETED")
                .referenceNumber(referenceNumber)
                .utrNumber(utrNumber)
                .metadata(metadata)
                .completedAt(Instant.now())
                .build();

        transaction = transactionRepository.save(transaction);
        log.info("Repayment recorded for application {}: {} (UTR: {})", applicationId, amount, utrNumber);

        auditService.logEvent(applicationId, "TRANSACTION", "REPAYMENT",
                null, null,
                Map.of("amount", amount, "utrNumber", utrNumber != null ? utrNumber : ""),
                "Repayment recorded: " + amount);

        return toResponse(transaction);
    }

    @Override
    public Page<TransactionResponse> getTransactionHistory(UUID applicationId, Pageable pageable) {
        return transactionRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId, pageable)
                .map(this::toResponse);
    }

    @Override
    public BigDecimal getOutstandingBalance(UUID applicationId) {
        BigDecimal disbursed = transactionRepository.sumDisbursedAmount(applicationId);
        BigDecimal repaid = transactionRepository.sumRepaidAmount(applicationId);
        return disbursed.subtract(repaid);
    }

    private TransactionResponse toResponse(Transaction txn) {
        return TransactionResponse.builder()
                .id(txn.getId())
                .applicationId(txn.getApplicationId())
                .transactionType(txn.getTransactionType())
                .amount(txn.getAmount())
                .status(txn.getStatus())
                .referenceNumber(txn.getReferenceNumber())
                .utrNumber(txn.getUtrNumber())
                .metadata(txn.getMetadata())
                .createdAt(txn.getCreatedAt())
                .completedAt(txn.getCompletedAt())
                .build();
    }
}
