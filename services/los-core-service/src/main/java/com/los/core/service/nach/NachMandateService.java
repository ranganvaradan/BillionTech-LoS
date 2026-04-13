package com.los.core.service.nach;

import com.los.core.model.entity.NachMandate;
import com.los.core.repository.NachMandateRepository;
import com.los.core.service.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * NACH Mandate Service — manages NACH/e-NACH mandate creation,
 * registration, and cancellation for EMI auto-debit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NachMandateService {

    private final NachMandateRepository nachMandateRepository;
    private final AuditService auditService;

    /**
     * Create a new NACH mandate for EMI auto-debit.
     */
    @Transactional
    public NachMandate createMandate(UUID applicationId, UUID customerId,
                                      String bankName, String accountNumber, String ifscCode,
                                      String accountHolderName, BigDecimal maxAmount,
                                      int tenureMonths) {
        String mandateRef = "NACH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        LocalDate startDate = LocalDate.now().plusMonths(1).withDayOfMonth(5);
        LocalDate endDate = startDate.plusMonths(tenureMonths);

        NachMandate mandate = NachMandate.builder()
                .applicationId(applicationId)
                .customerId(customerId)
                .mandateReference(mandateRef)
                .status("CREATED")
                .bankName(bankName)
                .accountNumber(accountNumber)
                .ifscCode(ifscCode)
                .accountHolderName(accountHolderName)
                .maxAmount(maxAmount)
                .frequency("MONTHLY")
                .startDate(startDate)
                .endDate(endDate)
                .build();

        mandate = nachMandateRepository.save(mandate);

        auditService.logEvent(applicationId, "NACH_MANDATE_CREATED",
                Map.of("mandateRef", mandateRef, "bank", bankName, "maxAmount", maxAmount.toString()));

        log.info("NACH mandate created: ref={} for application={}", mandateRef, applicationId);
        return mandate;
    }

    /**
     * Register mandate after eSign (UMRN assigned by NPCI).
     */
    @Transactional
    public NachMandate registerMandate(UUID mandateId, String umrn, String esignTransactionId) {
        NachMandate mandate = nachMandateRepository.findById(mandateId)
                .orElseThrow(() -> new RuntimeException("Mandate not found: " + mandateId));

        mandate.setStatus("REGISTERED");
        mandate.setUmrn(umrn);
        mandate.setEsignTransactionId(esignTransactionId);
        mandate.setEsigned(true);
        mandate.setRegisteredAt(Instant.now());
        mandate = nachMandateRepository.save(mandate);

        auditService.logEvent(mandate.getApplicationId(), "NACH_MANDATE_REGISTERED",
                Map.of("mandateRef", mandate.getMandateReference(), "umrn", umrn));

        log.info("NACH mandate registered: ref={}, UMRN={}", mandate.getMandateReference(), umrn);
        return mandate;
    }

    /**
     * Cancel a NACH mandate.
     */
    @Transactional
    public NachMandate cancelMandate(UUID mandateId, String reason) {
        NachMandate mandate = nachMandateRepository.findById(mandateId)
                .orElseThrow(() -> new RuntimeException("Mandate not found: " + mandateId));

        mandate.setStatus("CANCELLED");
        mandate.setCancelledAt(Instant.now());
        mandate.setCancelReason(reason);
        mandate = nachMandateRepository.save(mandate);

        auditService.logEvent(mandate.getApplicationId(), "NACH_MANDATE_CANCELLED",
                Map.of("mandateRef", mandate.getMandateReference(), "reason", reason));

        log.info("NACH mandate cancelled: ref={}, reason={}", mandate.getMandateReference(), reason);
        return mandate;
    }

    /**
     * Get mandates for an application.
     */
    public List<NachMandate> getMandates(UUID applicationId) {
        return nachMandateRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId);
    }

    /**
     * Get active mandate for an application.
     */
    public NachMandate getActiveMandate(UUID applicationId) {
        return nachMandateRepository.findFirstByApplicationIdAndStatusOrderByCreatedAtDesc(applicationId, "REGISTERED")
                .orElseThrow(() -> new RuntimeException("No active NACH mandate for application: " + applicationId));
    }
}
