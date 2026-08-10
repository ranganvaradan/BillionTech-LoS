package com.los.lms.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.model.entity.KfsDocument;
import com.los.core.model.entity.LoanApplication;
import com.los.core.repository.KfsDocumentRepository;
import com.los.core.service.kfs.KfsService;
import com.los.core.service.loan.ApplicationPartyResolver;
import com.los.core.service.loan.InvoiceDiscountingLosLoanGuard;
import com.los.core.service.audit.IntegrationApiAuditService;
import com.los.encore.client.api.EncoreLmsApi;
import com.los.encore.client.api.EncoreOpenLoanParams;
import com.los.encore.client.api.EncoreTemporaryOverrides;
import com.los.encore.client.config.EncoreClientProperties;
import com.los.encore.client.logging.LmsLogEvent;
import com.los.lms.config.LmsCallbackSecurityProperties;
import com.los.lms.dto.*;
import com.los.lms.entity.LmsAccountSummary;
import com.los.lms.entity.LmsLoanHandover;
import com.los.lms.entity.LmsRepaymentCallback;
import com.los.lms.legacy.BlCoreEncoreLmsAdapter;
import com.los.lms.repository.LmsAccountSummaryRepository;
import com.los.lms.repository.LmsLoanHandoverRepository;
import com.los.lms.repository.LmsRepaymentCallbackRepository;
import com.los.lms.support.EncoreSummaryNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.slf4j.MDC;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * LMS Adapter Service — manages loan handover, repayment schedule generation,
 * account summaries, and repayment callbacks.
 *
 * Integrates with Encore LMS (from legacy bl-core EncoreServiceFacadeImpl) when
 * credentials are configured. Falls back to local calculation when Encore is unavailable.
 * All data is persisted to PostgreSQL (replaces in-memory ConcurrentHashMap).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LmsService {

    private final EncoreLmsApi encoreLmsApi;
    private final EncoreClientProperties encoreClientProperties;
    private final BlCoreEncoreLmsAdapter blCoreEncoreLmsAdapter;
    private final WorkflowLmsProductResolver workflowLmsProductResolver;
    private final LmsProgramResolver lmsProgramResolver;
    private final LmsApplicationConfigResolver lmsApplicationConfigResolver;
    private final ObjectMapper objectMapper;
    private final LmsLoanHandoverRepository handoverRepository;
    private final LmsRepaymentCallbackRepository repaymentRepository;
    private final LmsAccountSummaryRepository summaryRepository;
    private final LmsCallbackSecurityProperties callbackSecurity;
    private final InvoiceDiscountingLosLoanGuard invoiceDiscountingLosLoanGuard;
    private final KfsDocumentRepository kfsDocumentRepository;
    private final KfsService kfsService;
    private final IntegrationApiAuditService integrationApiAuditService;

    /**
     * Hand over a disbursed loan to LMS for servicing.
     * When Encore is configured: opens loan account via BlCoreEncoreLmsAdapter (full bl-core parity payload),
     * posts disbursement transaction, and fetches repayment schedule from Encore.
     * Always persists to database.
     */
    @Transactional
    public LoanHandoverResponse handoverLoan(LoanHandoverRequest request) {
        if (invoiceDiscountingLosLoanGuard.skipsLosTermLoanCreation(request.getApplicationId())
                || invoiceDiscountingLosLoanGuard.skipsLosTermLoanCreation(request.getApplicationNumber())) {
            log.info("[LMS-HANDOVER] Skipped — invoice discounting borrower onboarding for {}",
                    request.getApplicationNumber());
            return LoanHandoverResponse.builder()
                    .applicationNumber(request.getApplicationNumber())
                    .status("SKIPPED")
                    .message("Invoice discounting borrower onboarding does not create a term loan in LOS/LMS")
                    .build();
        }

        String encoreAccountId = null;
        String encoreTransactionId = null;
        String status = "ACCEPTED";
        String errorMessage = null;
        String openAccountRequestJson = null;
        String openAccountResponseJson = null;
        String repaymentScheduleJson = null;

        // Reuse Encore account if already created at sanction time (idempotent)
        Optional<LmsLoanHandover> priorHandover = handoverRepository.findByApplicationNumber(request.getApplicationNumber());
        String priorEncoreAccountId = priorHandover.map(LmsLoanHandover::getEncoreAccountId).orElse(null);

        boolean encoreAllowed = encoreLmsApi.isActive() && isEncoreAllowedForApplication(request.getApplicationNumber());
        if (encoreAllowed) {
            try {
                MDC.put("applicationId", request.getApplicationNumber());
                MDC.put("productCode", request.getProductCode() != null ? request.getProductCode() : "");

                log.info("[LMS-SANCTION] Generated compact Encore customerId={} from applicationNo={}",
                        EncoreTemporaryOverrides.buildEncoreCustomerId(request.getApplicationNumber()),
                        request.getApplicationNumber());

                maybeValidateEncoreWorkingDate();
                EncoreOpenLoanParams encoreParams = toEncoreParams(request);

                if (priorEncoreAccountId != null && !priorEncoreAccountId.isBlank()) {
                    encoreAccountId = priorEncoreAccountId;
                    log.info("Encore account reused from sanction: {} - posting disbursement only for {}",
                            encoreAccountId, request.getApplicationNumber());
                } else {
                    var loanOd = blCoreEncoreLmsAdapter.buildLoanOdAccount(encoreParams, request);
                    String transactionId = "BL-" + UUID.randomUUID().toString().substring(0, 8);
                    openAccountRequestJson = objectMapper.writeValueAsString(loanOd);

                    log.info("Encore openLoanAccount | app={} | productCode={} | transactionId={}",
                            request.getApplicationNumber(), request.getProductCode(), transactionId);
                    log.info("[LMS-SANCTION] Encore openLoanAccount request payload | app={} | transactionId={} | payload={}",
                            request.getApplicationNumber(), transactionId, openAccountRequestJson);

                    encoreAccountId = encoreLmsApi.openLoanAccountWithJson(transactionId, openAccountRequestJson);
                    openAccountResponseJson = encoreAccountId;
                }

                log.info("[LMS-DISBURSE-FINAL-PAYLOAD] Pre-disburse context | app={} | encoreAccountId={} | " +
                        "productCode={} | amount={} | endpoint={}",
                        request.getApplicationNumber(), encoreAccountId,
                        encoreParams.productCode(), encoreParams.sanctionedAmount(),
                        "webservices/loans/accounts/postTransactions");
                encoreTransactionId = encoreLmsApi.disburse(encoreAccountId, encoreParams);
                log.info("Encore disbursement posted: accountId={}, txn={} for {}",
                        encoreAccountId, encoreTransactionId, request.getApplicationNumber());

                // Fetch repayment schedule from Encore after successful creation (bl-core pattern)
                try {
                    var scheduleEntries = encoreLmsApi.findRepaymentSchedule(encoreAccountId);
                    if (scheduleEntries != null && !scheduleEntries.isEmpty()) {
                        repaymentScheduleJson = objectMapper.writeValueAsString(scheduleEntries);
                        log.info("Fetched {} repayment schedule entries from Encore for accountId={}",
                                scheduleEntries.size(), encoreAccountId);
                    }
                } catch (Exception schedEx) {
                    log.warn("Failed to fetch repayment schedule from Encore for {}: {}",
                            encoreAccountId, schedEx.getMessage());
                }

            } catch (Exception e) {
                log.error("Encore handover failed for {}: {}", request.getApplicationNumber(), e.getMessage(), e);
                errorMessage = "Encore error: " + e.getMessage();
                status = "ENCORE_ERROR";
            } finally {
                MDC.remove("applicationId");
                MDC.remove("productCode");
            }
        } else {
            log.info("[LMS] Encore not configured — using local calculation for {}",
                    request.getApplicationNumber());
        }

        String lmsRef = encoreAccountId != null ? encoreAccountId : "LMS-" + request.getApplicationNumber();

        List<RepaymentScheduleEntry> schedule = generateRepaymentSchedule(
                request.getSanctionedAmount(),
                request.getInterestRate(),
                request.getTenureMonths()
        );

        LocalDate firstEmiDate = LocalDate.now().plusMonths(1).withDayOfMonth(5);
        BigDecimal emiAmount = schedule.isEmpty() ? BigDecimal.ZERO : schedule.get(0).getEmiAmount();

        // Persist handover (upsert — idempotent on retry)
        LmsLoanHandover handover = handoverRepository.findByApplicationNumber(request.getApplicationNumber())
                .orElse(LmsLoanHandover.builder()
                        .applicationNumber(request.getApplicationNumber())
                        .build());
        handover.setBorrowerName(request.getBorrowerName());
        handover.setProductCode(request.getProductCode());
        handover.setSanctionedAmount(request.getSanctionedAmount());
        handover.setInterestRate(request.getInterestRate());
        handover.setTenureMonths(request.getTenureMonths());
        handover.setEncoreAccountId(encoreAccountId);
        handover.setEncoreTransactionId(encoreTransactionId);
        handover.setLmsReferenceId(lmsRef);
        handover.setHandoverStatus(status);
        handover.setDisbursementDate(parseHandoverDisbursementDate(request));
        handover.setFirstEmiDate(firstEmiDate);
        handover.setEmiAmount(emiAmount);
        handover.setErrorMessage(errorMessage);
        handover.setEncoreOpenAccountRequestJson(openAccountRequestJson);
        handover.setEncoreOpenAccountResponseJson(openAccountResponseJson);
        handover.setEncoreRepaymentScheduleJson(repaymentScheduleJson);
        handoverRepository.save(handover);

        Instant auditAt = Instant.now();
        integrationApiAuditService.record(
                "ENCORE_LMS",
                "LMS_HANDOVER_OPEN_ACCOUNT",
                openAccountRequestJson,
                openAccountResponseJson,
                "ENCORE_ERROR".equals(status) ? "FAILED" : "SUCCESS",
                "ENCORE_ERROR".equals(status) ? 500 : 200,
                errorMessage,
                encoreTransactionId != null ? encoreTransactionId : encoreAccountId,
                request.getApplicationId(),
                auditAt,
                auditAt,
                null);
        if (encoreTransactionId != null) {
            integrationApiAuditService.record(
                    "ENCORE_LMS",
                    "LMS_HANDOVER_DISBURSE",
                    "{\"encoreAccountId\":\"" + encoreAccountId + "\"}",
                    "{\"transactionId\":\"" + encoreTransactionId + "\"}",
                    "SUCCESS",
                    200,
                    null,
                    encoreTransactionId,
                    request.getApplicationId(),
                    auditAt,
                    auditAt,
                    null);
        }

        // Persist account summary (upsert — idempotent on retry)
        LmsAccountSummary summaryEntity = summaryRepository.findByApplicationNumber(request.getApplicationNumber())
                .orElse(LmsAccountSummary.builder()
                        .applicationNumber(request.getApplicationNumber())
                        .build());
        summaryEntity.setEncoreAccountId(encoreAccountId);
        summaryEntity.setLoanStatus("ACTIVE");
        summaryEntity.setSanctionedAmount(request.getSanctionedAmount());
        summaryEntity.setDisbursedAmount(request.getSanctionedAmount());
        summaryEntity.setOutstandingPrincipal(request.getSanctionedAmount());
        summaryEntity.setTotalPaid(BigDecimal.ZERO);
        summaryEntity.setOverdueAmount(BigDecimal.ZERO);
        summaryEntity.setTotalEmis(request.getTenureMonths());
        summaryEntity.setPaidEmis(0);
        summaryEntity.setOverdueEmis(0);
        summaryEntity.setNextEmiDate(firstEmiDate);
        summaryEntity.setNextEmiAmount(emiAmount);
        summaryEntity.setDpd(0);
        summaryEntity.setLastSyncedAt(Instant.now());
        summaryRepository.save(summaryEntity);

        log.info("Loan handed over to LMS: {} -> {} (encore={})",
                request.getApplicationNumber(), lmsRef, encoreLmsApi.isActive());

        return LoanHandoverResponse.builder()
                .handoverId(handover.getId())
                .applicationNumber(request.getApplicationNumber())
                .lmsReferenceId(lmsRef)
                .status(status)
                .message(errorMessage != null
                        ? "Loan handed over with Encore warning: " + errorMessage
                        : "Loan successfully handed over to LMS for servicing")
                .firstEmiDate(firstEmiDate)
                .emiAmount(emiAmount)
                .totalEmis(request.getTenureMonths())
                .build();
    }

    /**
     * Get loan account summary from LMS.
     * Tries Encore first (if configured), then falls back to database, then simulated.
     */
    public LoanAccountSummary getAccountSummary(String applicationNumber) {
        // Try database first
        Optional<LmsAccountSummary> dbSummary = summaryRepository.findByApplicationNumber(applicationNumber);
        if (dbSummary.isPresent()) {
            LmsAccountSummary entity = dbSummary.get();

            // If Encore is active and we have an account ID, try to sync from Encore
            if (encoreLmsApi.isActive() && entity.getEncoreAccountId() != null) {
                try {
                    List<Map<String, Object>> encoreSummaries =
                            encoreLmsApi.findSummaries(List.of(entity.getEncoreAccountId()));
                    if (!encoreSummaries.isEmpty()) {
                        Map<String, Object> encoreData = encoreSummaries.get(0);
                        EncoreSummaryNormalizer.applyEncoreRowToAccountSummary(entity, encoreData);
                        summaryRepository.save(entity);
                    }
                } catch (Exception e) {
                    log.warn("Failed to sync from Encore for {}: {}", applicationNumber, e.getMessage());
                }
            }

            return toAccountSummaryDto(entity);
        }

        // Fallback: return simulated summary
        return LoanAccountSummary.builder()
                .applicationNumber(applicationNumber)
                .lmsReferenceId("LMS-" + applicationNumber)
                .loanStatus("ACTIVE")
                .sanctionedAmount(new BigDecimal("500000"))
                .disbursedAmount(new BigDecimal("500000"))
                .outstandingPrincipal(new BigDecimal("485000"))
                .totalPaid(new BigDecimal("27500"))
                .overdueAmount(BigDecimal.ZERO)
                .totalEmis(36)
                .paidEmis(2)
                .overdueEmis(0)
                .nextEmiDate(LocalDate.now().plusDays(15))
                .nextEmiAmount(new BigDecimal("16250"))
                .lastPaymentDate(LocalDate.now().minusDays(20))
                .dpd(0)
                .build();
    }

    /**
     * Get full repayment schedule for a loan.
     * Tries Encore first, then generates locally.
     */
    public RepaymentScheduleResponse getRepaymentSchedule(String applicationNumber) {
        Optional<LmsLoanHandover> handoverOpt = handoverRepository.findByApplicationNumber(applicationNumber);

        BigDecimal amount;
        BigDecimal rate;
        int tenure;
        String lmsRef = "LMS-" + applicationNumber;

        if (handoverOpt.isPresent()) {
            LmsLoanHandover handover = handoverOpt.get();
            amount = handover.getSanctionedAmount();
            rate = handover.getInterestRate();
            tenure = handover.getTenureMonths();
            if (handover.getEncoreAccountId() != null && !handover.getEncoreAccountId().isBlank()) {
                lmsRef = handover.getEncoreAccountId();
            }

            if (encoreLmsApi.isActive() && handover.getEncoreAccountId() != null) {
                try {
                    List<Map<String, Object>> encoreSchedule =
                            encoreLmsApi.findRepaymentSchedule(handover.getEncoreAccountId());
                    RepaymentScheduleResponse fromEncore = buildScheduleResponse(
                            applicationNumber, lmsRef, amount, rate, tenure,
                            mapEncoreScheduleMaps(encoreSchedule, "FROM_ENCORE"));
                    if (fromEncore != null) {
                        return fromEncore;
                    }
                } catch (Exception e) {
                    log.warn("Failed to get Encore schedule for {}: {}", applicationNumber, e.getMessage());
                }
            }

            RepaymentScheduleResponse fromCache = scheduleFromCachedEncoreJson(
                    handover.getEncoreRepaymentScheduleJson(),
                    applicationNumber, lmsRef, amount, rate, tenure);
            if (fromCache != null) {
                return fromCache;
            }
        } else {
            amount = new BigDecimal("500000");
            rate = new BigDecimal("12.5");
            tenure = 36;
        }

        List<RepaymentScheduleEntry> schedule = withScheduleStatus(
                generateRepaymentSchedule(amount, rate, tenure), "INDICATIVE");

        BigDecimal totalInterest = schedule.stream()
                .map(RepaymentScheduleEntry::getInterestComponent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return RepaymentScheduleResponse.builder()
                .applicationNumber(applicationNumber)
                .lmsReferenceId(lmsRef)
                .sanctionedAmount(amount)
                .interestRate(rate)
                .tenureMonths(tenure)
                .totalInterest(totalInterest)
                .totalPayable(amount.add(totalInterest))
                .schedule(schedule)
                .build();
    }

    /**
     * Process a repayment callback from LMS.
     * Posts to Encore if configured, always persists to database.
     *
     * @param signatureHeader optional {@code X-LMS-Signature} (hex SHA-256 of canonical payload + secret) when {@code los.lms.callback.hmac-secret} is set
     */
    @Transactional
    public Map<String, Object> processRepaymentCallback(RepaymentCallbackRequest callback, String signatureHeader) {
        log.info("event={} application={} installment={} amount={}",
                LmsLogEvent.LMS_CALLBACK_RECEIVED, callback.getApplicationNumber(),
                callback.getInstallmentNumber(), callback.getPaidAmount());

        validateRepaymentCallback(callback);

        if (!callbackSecurity.verifySignature(callback, signatureHeader)) {
            log.warn("Invalid LMS callback signature for {}", callback.getApplicationNumber());
            return Map.of(
                    "status", "REJECTED",
                    "applicationNumber", callback.getApplicationNumber(),
                    "message", "Invalid or missing X-LMS-Signature"
            );
        }

        if (callback.getIdempotencyKey() != null && !callback.getIdempotencyKey().isBlank()) {
            Optional<LmsRepaymentCallback> existing = repaymentRepository.findByIdempotencyKey(callback.getIdempotencyKey());
            if (existing.isPresent()) {
                LmsRepaymentCallback e = existing.get();
                return Map.of(
                        "status", "DUPLICATE",
                        "applicationNumber", callback.getApplicationNumber(),
                        "installmentNumber", callback.getInstallmentNumber(),
                        "encoreTransactionId", e.getTransactionId() != null ? e.getTransactionId() : "",
                        "message", "Callback already processed (idempotent)"
                );
            }
        }

        // Post to Encore if configured
        Optional<LmsLoanHandover> handoverOpt = handoverRepository.findByApplicationNumber(callback.getApplicationNumber());
        String encoreAccountId = handoverOpt.map(LmsLoanHandover::getEncoreAccountId).orElse(null);
        String encoreTxnId = null;

        if (encoreLmsApi.isActive() && encoreAccountId != null) {
            try {
                encoreTxnId = encoreLmsApi.repay(
                        encoreAccountId, callback.getPaidAmount(), "ScheduledRepayment");
            } catch (Exception e) {
                log.error("Encore repayment failed for {}: {}", callback.getApplicationNumber(), e.getMessage());
            }
        }

        // Persist repayment callback
        LmsRepaymentCallback entity = LmsRepaymentCallback.builder()
                .applicationNumber(callback.getApplicationNumber())
                .encoreAccountId(encoreAccountId)
                .transactionId(encoreTxnId)
                .installmentNumber(callback.getInstallmentNumber())
                .repaymentType("SCHEDULED")
                .amount(callback.getPaidAmount())
                .principalComponent(callback.getPrincipalComponent())
                .interestComponent(callback.getInterestComponent())
                .paymentDate(callback.getPaymentDate())
                .paymentMode(callback.getPaymentMode())
                .utrNumber(callback.getUtrNumber())
                .idempotencyKey(callback.getIdempotencyKey())
                .status("PROCESSED")
                .build();
        repaymentRepository.save(entity);

        // Update account summary in DB
        // Only subtract the principal component from outstanding principal (not the full EMI which includes interest)
        Optional<LmsAccountSummary> summaryOpt = summaryRepository.findByApplicationNumber(callback.getApplicationNumber());
        if (summaryOpt.isPresent()) {
            LmsAccountSummary summary = summaryOpt.get();
            summary.setPaidEmis((summary.getPaidEmis() != null ? summary.getPaidEmis() : 0) + 1);
            summary.setTotalPaid((summary.getTotalPaid() != null ? summary.getTotalPaid() : BigDecimal.ZERO).add(callback.getPaidAmount()));
            BigDecimal principalReduction = callback.getPrincipalComponent() != null
                    ? callback.getPrincipalComponent() : callback.getPaidAmount();
            if (principalReduction.compareTo(BigDecimal.ZERO) > 0) {
                if (callback.getPrincipalComponent() == null) {
                    log.warn("No principalComponent provided for {} — falling back to full paidAmount for principal reduction. "
                            + "Callers should provide principalComponent for accurate tracking.", callback.getApplicationNumber());
                }
                summary.setOutstandingPrincipal(
                        (summary.getOutstandingPrincipal() != null ? summary.getOutstandingPrincipal() : BigDecimal.ZERO).subtract(principalReduction));
            }
            summary.setLastPaymentDate(callback.getPaymentDate());
            if (summary.getNextEmiDate() != null) {
                summary.setNextEmiDate(summary.getNextEmiDate().plusMonths(1));
            }
            summaryRepository.save(summary);
        }

        log.info("Repayment callback processed: {} installment #{} amount={}",
                callback.getApplicationNumber(), callback.getInstallmentNumber(), callback.getPaidAmount());

        return Map.of(
                "status", "PROCESSED",
                "applicationNumber", callback.getApplicationNumber(),
                "installmentNumber", callback.getInstallmentNumber(),
                "encoreTransactionId", encoreTxnId != null ? encoreTxnId : "",
                "message", "Repayment recorded successfully"
        );
    }

    /**
     * Borrower-initiated repayment from the borrower portal. Posts the amount to Encore as a
     * {@code ScheduledRepayment} when Encore is active and an account exists, always persists the
     * repayment, and updates the local account summary. Returns the refreshed account summary.
     */
    @Transactional
    public LoanAccountSummary recordBorrowerRepayment(String applicationNumber, BigDecimal amount) {
        if (applicationNumber == null || applicationNumber.isBlank()) {
            throw new IllegalArgumentException("applicationNumber is required");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Repayment amount must be positive");
        }

        Optional<LmsLoanHandover> handoverOpt = handoverRepository.findByApplicationNumber(applicationNumber);
        String encoreAccountId = handoverOpt.map(LmsLoanHandover::getEncoreAccountId).orElse(null);
        String encoreTxnId = null;
        if (encoreLmsApi.isActive() && encoreAccountId != null) {
            try {
                encoreTxnId = encoreLmsApi.repay(encoreAccountId, amount, "ScheduledRepayment");
            } catch (Exception e) {
                log.error("Encore borrower repayment failed for {}: {}", applicationNumber, e.getMessage());
            }
        }

        LmsRepaymentCallback entity = LmsRepaymentCallback.builder()
                .applicationNumber(applicationNumber)
                .encoreAccountId(encoreAccountId)
                .transactionId(encoreTxnId)
                .repaymentType("BORROWER_PORTAL")
                .amount(amount)
                .paymentDate(LocalDate.now())
                .paymentMode("BORROWER_PORTAL")
                .status("PROCESSED")
                .build();
        repaymentRepository.save(entity);

        summaryRepository.findByApplicationNumber(applicationNumber).ifPresent(summary -> {
            summary.setPaidEmis((summary.getPaidEmis() != null ? summary.getPaidEmis() : 0) + 1);
            summary.setTotalPaid((summary.getTotalPaid() != null ? summary.getTotalPaid() : BigDecimal.ZERO).add(amount));
            BigDecimal outstanding = summary.getOutstandingPrincipal() != null
                    ? summary.getOutstandingPrincipal() : BigDecimal.ZERO;
            summary.setOutstandingPrincipal(outstanding.subtract(amount).max(BigDecimal.ZERO));
            summary.setLastPaymentDate(LocalDate.now());
            if (summary.getNextEmiDate() != null) {
                summary.setNextEmiDate(summary.getNextEmiDate().plusMonths(1));
            }
            summaryRepository.save(summary);
        });

        log.info("Borrower repayment recorded for {}: amount={}, encoreTxn={}", applicationNumber, amount, encoreTxnId);
        return getAccountSummary(applicationNumber);
    }

    /**
     * Admin PG settlement after successful PayU collection (PRUS/PIP). Posts repayment to LMS with settlement UTR.
     */
    @Transactional
    public LoanAccountSummary recordPgSettlementRepayment(String applicationNumber, BigDecimal amount, String utr) {
        if (applicationNumber == null || applicationNumber.isBlank()) {
            throw new IllegalArgumentException("applicationNumber is required");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Repayment amount must be positive");
        }
        if (utr == null || utr.isBlank()) {
            throw new IllegalArgumentException("Settlement UTR is required");
        }

        Optional<LmsLoanHandover> handoverOpt = handoverRepository.findByApplicationNumber(applicationNumber);
        String encoreAccountId = handoverOpt.map(LmsLoanHandover::getEncoreAccountId).orElse(null);
        String encoreTxnId = null;
        if (encoreLmsApi.isActive() && encoreAccountId != null) {
            try {
                encoreTxnId = encoreLmsApi.repay(encoreAccountId, amount, "ScheduledRepayment");
            } catch (Exception e) {
                log.error("Encore PG settlement repayment failed for {}: {}", applicationNumber, e.getMessage());
            }
        }

        LmsRepaymentCallback entity = LmsRepaymentCallback.builder()
                .applicationNumber(applicationNumber)
                .encoreAccountId(encoreAccountId)
                .transactionId(encoreTxnId)
                .repaymentType("PG_SETTLEMENT")
                .amount(amount)
                .paymentDate(LocalDate.now())
                .paymentMode("PAYU_PG_SETTLED")
                .utrNumber(utr.trim())
                .status("PROCESSED")
                .build();
        repaymentRepository.save(entity);

        summaryRepository.findByApplicationNumber(applicationNumber).ifPresent(summary -> {
            summary.setPaidEmis((summary.getPaidEmis() != null ? summary.getPaidEmis() : 0) + 1);
            summary.setTotalPaid((summary.getTotalPaid() != null ? summary.getTotalPaid() : BigDecimal.ZERO).add(amount));
            BigDecimal outstanding = summary.getOutstandingPrincipal() != null
                    ? summary.getOutstandingPrincipal() : BigDecimal.ZERO;
            summary.setOutstandingPrincipal(outstanding.subtract(amount).max(BigDecimal.ZERO));
            summary.setLastPaymentDate(LocalDate.now());
            if (summary.getNextEmiDate() != null) {
                summary.setNextEmiDate(summary.getNextEmiDate().plusMonths(1));
            }
            summaryRepository.save(summary);
        });

        log.info("PG settlement repayment recorded for {}: amount={}, utr={}", applicationNumber, amount, utr);
        return getAccountSummary(applicationNumber);
    }

    /**
     * Get payment history for a loan (from database).
     */
    public List<RepaymentCallbackRequest> getPaymentHistory(String applicationNumber) {
        List<LmsRepaymentCallback> callbacks =
                repaymentRepository.findByApplicationNumberOrderByCreatedAtDesc(applicationNumber);
        return callbacks.stream().map(cb -> RepaymentCallbackRequest.builder()
                .applicationNumber(cb.getApplicationNumber())
                .installmentNumber(cb.getInstallmentNumber() != null ? cb.getInstallmentNumber() : 0)
                .paidAmount(cb.getAmount())
                .principalComponent(cb.getPrincipalComponent())
                .interestComponent(cb.getInterestComponent())
                .paymentDate(cb.getPaymentDate())
                .paymentMode(cb.getPaymentMode())
                .utrNumber(cb.getUtrNumber())
                .status(cb.getStatus())
                .build()).toList();
    }

    /**
     * Get all active loan accounts (from database).
     */
    public List<LoanAccountSummary> getAllAccounts() {
        return summaryRepository.findAll().stream()
                .map(this::toAccountSummaryDto)
                .toList();
    }

    /**
     * Update NPA flag for a loan account based on DPD (BR-11.4).
     */
    @Transactional
    public Map<String, Object> updateNpaStatus(String applicationNumber, int currentDpd) {
        Optional<LmsAccountSummary> summaryOpt = summaryRepository.findByApplicationNumber(applicationNumber);
        if (summaryOpt.isEmpty()) {
            return Map.of("status", "NOT_FOUND", "applicationNumber", applicationNumber);
        }

        LmsAccountSummary summary = summaryOpt.get();
        summary.setDpd(currentDpd);
        String previousStatus = summary.getLoanStatus();

        if (currentDpd > 90) {
            summary.setLoanStatus("NPA");
        } else if (currentDpd > 60) {
            summary.setLoanStatus("SMA-2");
        } else if (currentDpd > 30) {
            summary.setLoanStatus("SMA-1");
        } else if (currentDpd > 0) {
            summary.setLoanStatus("SMA-0");
        } else {
            summary.setLoanStatus("ACTIVE");
        }
        summaryRepository.save(summary);

        log.info("NPA status updated for {}: DPD={}, status={}, previous={}",
                applicationNumber, currentDpd, summary.getLoanStatus(), previousStatus);

        return Map.of(
                "applicationNumber", applicationNumber,
                "dpd", currentDpd,
                "loanStatus", summary.getLoanStatus(),
                "previousStatus", previousStatus
        );
    }

    /**
     * Get collection summary — overdue accounts, DPD buckets, NPA portfolio.
     */
    public Map<String, Object> getCollectionSummary() {
        List<LmsAccountSummary> allAccounts = summaryRepository.findAll();

        long totalAccounts = allAccounts.size();
        long overdueAccounts = allAccounts.stream().filter(a -> a.getDpd() != null && a.getDpd() > 0).count();

        BigDecimal totalOverdue = allAccounts.stream()
                .filter(a -> a.getDpd() != null && a.getDpd() > 0)
                .map(a -> a.getOverdueAmount() != null ? a.getOverdueAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalOutstanding = allAccounts.stream()
                .map(a -> a.getOutstandingPrincipal() != null ? a.getOutstandingPrincipal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long sma0 = allAccounts.stream().filter(a -> a.getDpd() != null && a.getDpd() > 0 && a.getDpd() <= 30).count();
        long sma1 = allAccounts.stream().filter(a -> a.getDpd() != null && a.getDpd() > 30 && a.getDpd() <= 60).count();
        long sma2 = allAccounts.stream().filter(a -> a.getDpd() != null && a.getDpd() > 60 && a.getDpd() <= 90).count();
        long npa = allAccounts.stream().filter(a -> a.getDpd() != null && a.getDpd() > 90).count();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalAccounts", totalAccounts);
        summary.put("overdueAccounts", overdueAccounts);
        summary.put("npaAccounts", npa);
        summary.put("totalOverdueAmount", totalOverdue);
        summary.put("totalOutstandingAmount", totalOutstanding);
        summary.put("collectionEfficiency", totalAccounts > 0
                ? (totalAccounts - overdueAccounts) * 100.0 / totalAccounts : 100.0);
        summary.put("dpdBuckets", Map.of(
                "SMA-0 (1-30)", sma0,
                "SMA-1 (31-60)", sma1,
                "SMA-2 (61-90)", sma2,
                "NPA (>90)", npa
        ));
        summary.put("encoreActive", encoreLmsApi.isActive());

        return summary;
    }

    /**
     * BR-11.5: Process prepayment — partial or full.
     */
    @Transactional
    public Map<String, Object> processPrepayment(String applicationNumber, BigDecimal prepaymentAmount,
                                                   String prepaymentType) {
        Optional<LmsAccountSummary> summaryOpt = summaryRepository.findByApplicationNumber(applicationNumber);
        if (summaryOpt.isEmpty()) {
            return Map.of("status", "NOT_FOUND", "applicationNumber", applicationNumber);
        }

        LmsAccountSummary summary = summaryOpt.get();
        BigDecimal outstanding = summary.getOutstandingPrincipal() != null ? summary.getOutstandingPrincipal() : BigDecimal.ZERO;
        boolean isFullPrepayment = "FULL".equalsIgnoreCase(prepaymentType)
                || prepaymentAmount.compareTo(outstanding) >= 0;

        // Post prepayment to Encore if configured
        if (encoreLmsApi.isActive() && summary.getEncoreAccountId() != null) {
            try {
                String rpyType = isFullPrepayment ? "Pre-closure" : "Prepayment";
                encoreLmsApi.repay(summary.getEncoreAccountId(), prepaymentAmount, rpyType);
            } catch (Exception e) {
                log.error("Encore prepayment failed for {}: {}", applicationNumber, e.getMessage());
            }
        }

        BigDecimal foreclosureCharges = BigDecimal.ZERO;
        if (isFullPrepayment) {
            foreclosureCharges = outstanding.multiply(new BigDecimal("0.02"))
                    .setScale(2, RoundingMode.HALF_UP);
            summary.setOutstandingPrincipal(BigDecimal.ZERO);
            summary.setLoanStatus("CLOSED");
            summary.setNextEmiDate(null);
            summary.setNextEmiAmount(BigDecimal.ZERO);
        } else {
            summary.setOutstandingPrincipal(outstanding.subtract(prepaymentAmount));
            int remainingEmis = (summary.getTotalEmis() != null ? summary.getTotalEmis() : 0) - (summary.getPaidEmis() != null ? summary.getPaidEmis() : 0);
            if (remainingEmis > 0) {
                Optional<LmsLoanHandover> handoverOpt = handoverRepository.findByApplicationNumber(applicationNumber);
                BigDecimal rate = handoverOpt.map(LmsLoanHandover::getInterestRate).orElse(new BigDecimal("12.5"));
                List<RepaymentScheduleEntry> newSchedule = generateRepaymentSchedule(
                        summary.getOutstandingPrincipal(), rate, remainingEmis);
                if (!newSchedule.isEmpty()) {
                    summary.setNextEmiAmount(newSchedule.get(0).getEmiAmount());
                }
            }
        }

        summary.setTotalPaid((summary.getTotalPaid() != null ? summary.getTotalPaid() : BigDecimal.ZERO).add(prepaymentAmount));
        summaryRepository.save(summary);

        log.info("Prepayment processed for {}: type={}, amount={}, remaining={}",
                applicationNumber, isFullPrepayment ? "FORECLOSURE" : "PARTIAL",
                prepaymentAmount, summary.getOutstandingPrincipal());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("applicationNumber", applicationNumber);
        result.put("prepaymentType", isFullPrepayment ? "FORECLOSURE" : "PARTIAL");
        result.put("prepaymentAmount", prepaymentAmount);
        result.put("foreclosureCharges", foreclosureCharges);
        result.put("totalPayable", isFullPrepayment ? prepaymentAmount.add(foreclosureCharges) : prepaymentAmount);
        result.put("remainingOutstanding", summary.getOutstandingPrincipal());
        result.put("newEmiAmount", summary.getNextEmiAmount());
        result.put("loanStatus", summary.getLoanStatus());
        result.put("status", "PROCESSED");
        return result;
    }

    /**
     * BR-9.7: Multi-tranche disbursement — disburse in multiple tranches.
     */
    @Transactional
    public Map<String, Object> processTrancheDisbursement(String applicationNumber, BigDecimal trancheAmount,
                                                            int trancheNumber, int totalTranches) {
        Optional<LmsAccountSummary> summaryOpt = summaryRepository.findByApplicationNumber(applicationNumber);
        if (summaryOpt.isEmpty()) {
            return Map.of("status", "NOT_FOUND", "applicationNumber", applicationNumber);
        }

        LmsAccountSummary summary = summaryOpt.get();
        BigDecimal previouslyDisbursed = summary.getDisbursedAmount() != null ? summary.getDisbursedAmount() : BigDecimal.ZERO;
        BigDecimal newDisbursed = previouslyDisbursed.add(trancheAmount);
        summary.setDisbursedAmount(newDisbursed);
        summary.setOutstandingPrincipal((summary.getOutstandingPrincipal() != null ? summary.getOutstandingPrincipal() : BigDecimal.ZERO).add(trancheAmount));
        summaryRepository.save(summary);

        log.info("Tranche disbursement for {}: tranche {}/{}, amount={}, totalDisbursed={}",
                applicationNumber, trancheNumber, totalTranches, trancheAmount, newDisbursed);

        return Map.of(
                "applicationNumber", applicationNumber,
                "trancheNumber", trancheNumber,
                "totalTranches", totalTranches,
                "trancheAmount", trancheAmount,
                "totalDisbursed", newDisbursed,
                "sanctionedAmount", summary.getSanctionedAmount() != null ? summary.getSanctionedAmount() : BigDecimal.ZERO,
                "remainingToDisburse", (summary.getSanctionedAmount() != null ? summary.getSanctionedAmount() : BigDecimal.ZERO).subtract(newDisbursed),
                "status", trancheNumber >= totalTranches ? "FULLY_DISBURSED" : "PARTIALLY_DISBURSED"
        );
    }

    /**
     * Get Encore account statement for a loan.
     * Prefers {@code compositeStatement} from loan OD account details; falls back to legacy findAccountStatements.
     */
    public List<Map<String, Object>> getEncoreAccountStatement(String applicationNumber,
                                                                 String fromDate, String toDate) {
        Optional<LmsLoanHandover> handoverOpt = handoverRepository.findByApplicationNumber(applicationNumber);
        if (handoverOpt.isEmpty() || handoverOpt.get().getEncoreAccountId() == null) {
            return Collections.emptyList();
        }
        return encoreLmsApi.getAccountStatement(handoverOpt.get().getEncoreAccountId(), fromDate, toDate);
    }

    /** Composite or legacy Encore statement rows (webservices first, then REST composite when configured). */
    public List<Map<String, Object>> getEncoreCompositeStatement(String applicationNumber) {
        return getEncoreAccountStatement(applicationNumber, null, null);
    }

    /**
     * Raw {@code findLoanInfo} JSON for the Encore account linked to this application (if handover exists).
     */
    public String getEncoreLoanInfoRaw(String applicationNumber) {
        return handoverRepository.findByApplicationNumber(applicationNumber)
                .map(LmsLoanHandover::getEncoreAccountId)
                .filter(id -> id != null && !id.isBlank())
                .map(encoreLmsApi::findLoanInfoRaw)
                .orElse("");
    }

    /** bl-core {@code findAccountsForCustomer} — raw JSON array string. */
    public String getEncoreLoanAccountsForCustomerRaw(long customerId) {
        return encoreLmsApi.findLoanAccountsForCustomerRaw(customerId);
    }

    /** bl-core {@code findPreclosureAmountAsOfDate} — raw response string. */
    public String getEncorePrecloseAmountRaw(String applicationNumber, String valueDate) {
        Optional<LmsLoanHandover> h = handoverRepository.findByApplicationNumber(applicationNumber);
        if (h.isEmpty() || h.get().getEncoreAccountId() == null || valueDate == null || valueDate.isBlank()) {
            return "";
        }
        return encoreLmsApi.getPrecloseAmountRaw(h.get().getEncoreAccountId(), valueDate);
    }

    /**
     * Triggered at sanction time: opens loan account in Encore LMS WITHOUT posting disbursement.
     * Matches the old bl-core pattern where {@code encoreService.openLoanAccount()} was called
     * inline during sanction. Idempotent — skips if handover already exists for this application.
     *
     * @param app the sanctioned loan application
     * @return the Encore account ID (or local reference if Encore is not active), null on failure
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public String createLmsAccountOnSanction(LoanApplication app) {
        log.info("[LMS-SANCTION] Triggered for {} — starting LMS account creation", app.getApplicationNumber());

        if (invoiceDiscountingLosLoanGuard.skipsLosTermLoanCreation(app)) {
            log.info("[LMS-SANCTION] Skipped — invoice discounting borrower onboarding for {}",
                    app.getApplicationNumber());
            return null;
        }

        Optional<com.los.plp.model.entity.ProgramMaster> linkedProgram = lmsProgramResolver.resolveForApplication(app);
        if (linkedProgram.isPresent() && !lmsProgramResolver.isLmsEntryEnabled(linkedProgram.get())) {
            log.info("[LMS-SANCTION] Skipped — program lms_entry_in=NO for {}", app.getApplicationNumber());
            return null;
        }

        // Idempotency: skip if handover already exists
        Optional<LmsLoanHandover> existing = handoverRepository.findByApplicationNumber(app.getApplicationNumber());
        if (existing.isPresent() && existing.get().getEncoreAccountId() != null) {
            LmsLoanHandover handover = existing.get();
            refreshEncoreRepaymentScheduleCache(handover);
            log.info("[LMS-SANCTION] Skipped — handover already exists for {} with encoreAccountId={}",
                    app.getApplicationNumber(), handover.getEncoreAccountId());
            return handover.getEncoreAccountId();
        }

        BigDecimal amount = app.getSanctionedAmount() != null ? app.getSanctionedAmount() : app.getRequestedAmount();
        BigDecimal rate = app.getApprovedRate() != null ? app.getApprovedRate() : app.getInterestRate();
        int tenure = app.getTenureMonths() != null ? app.getTenureMonths() : 0;
        String loanProduct = app.getLoanProduct() != null ? app.getLoanProduct() : "";

        String encoreProductCode = lmsProgramResolver.resolveEncoreProductCode(app, loanProduct);
        String tenureUnit = lmsApplicationConfigResolver.resolveTenureUnit(app);
        log.info("[LMS-SANCTION] Encore product code {} tenureUnit {} for {} (loanProduct={})",
                encoreProductCode, tenureUnit, app.getApplicationNumber(), loanProduct);

        Optional<com.los.lms.entity.WorkflowLmsProductMapping> fullMapping =
                workflowLmsProductResolver.resolveFullMapping(null, app.getBorrowerType(), loanProduct);

        // Build handover request
        String borrowerName = ApplicationPartyResolver.resolveDisplayName(app);
        LoanHandoverRequest.LoanHandoverRequestBuilder builder = LoanHandoverRequest.builder()
                .applicationId(app.getId())
                .applicationNumber(app.getApplicationNumber())
                .borrowerName(borrowerName)
                .borrowerType(app.getBorrowerType().name())
                .loanProduct(loanProduct)
                .encorePartyOrCustomerId(app.getLmsEncoreCustomerId())
                .productCode(encoreProductCode)
                .sanctionedAmount(amount)
                .interestRate(rate)
                .tenureMonths(tenure)
                .numberOfInstallments(tenure)
                .tenureUnit(tenureUnit)
                .borrowerDetails(ApplicationPartyResolver.buildIntegrationPartyMap(app));

        fullMapping.ifPresent(m -> {
            if (m.getBranchSetCode() != null) builder.encoreBranchCode(m.getBranchSetCode());
            if (m.getPenalInterestRate() != null) builder.penalInterestRate(m.getPenalInterestRate());
            if (m.getMoratoriumType() != null) builder.moratoriumType(m.getMoratoriumType());
            if (m.getMoratoriumPeriodMagnitude() != null) builder.moratoriumPeriodMagnitude(m.getMoratoriumPeriodMagnitude());
            if (m.getMoratoriumPeriodUnit() != null) builder.moratoriumPeriodUnit(m.getMoratoriumPeriodUnit());
        });

        LoanHandoverRequest request = builder.build();

        String compactCustomerId = EncoreTemporaryOverrides.buildEncoreCustomerId(app.getApplicationNumber());
        log.info("[LMS-SANCTION] Generated compact Encore customerId={} from applicationNo={}",
                compactCustomerId, app.getApplicationNumber());

        String encoreAccountId = null;
        String openAccountRequestJson = null;
        String openAccountResponseJson = null;
        String repaymentScheduleJson = null;
        String status = "ACCEPTED";
        String errorMessage = null;

        boolean encoreAllowed = encoreLmsApi.isActive()
                && (!linkedProgram.isPresent() || lmsProgramResolver.isLmsEntryEnabled(linkedProgram.get()));
        if (encoreAllowed) {
            try {
                MDC.put("applicationId", app.getApplicationNumber());
                MDC.put("productCode", encoreProductCode);

                EncoreOpenLoanParams encoreParams = toEncoreParams(request);
                var loanOd = blCoreEncoreLmsAdapter.buildLoanOdAccount(encoreParams, request);
                String transactionId = "BL-SANCTION-" + UUID.randomUUID().toString().substring(0, 8);
                openAccountRequestJson = objectMapper.writeValueAsString(loanOd);

                log.info("[LMS-SANCTION] Encore openLoanAccount | app={} | productCode={} | transactionId={}",
                        app.getApplicationNumber(), encoreProductCode, transactionId);
                log.info("[LMS-SANCTION] Encore openLoanAccount request payload | app={} | transactionId={} | payload={}",
                        app.getApplicationNumber(), transactionId, openAccountRequestJson);

                encoreAccountId = encoreLmsApi.openLoanAccountWithJson(transactionId, openAccountRequestJson);
                openAccountResponseJson = encoreAccountId;

                log.info("[LMS-SANCTION] Encore loan account created: {} for app={}",
                        encoreAccountId, app.getApplicationNumber());

                // Fetch repayment schedule from Encore
                try {
                    var scheduleEntries = encoreLmsApi.findRepaymentSchedule(encoreAccountId);
                    if (scheduleEntries != null && !scheduleEntries.isEmpty()) {
                        repaymentScheduleJson = objectMapper.writeValueAsString(scheduleEntries);
                        log.info("[LMS-SANCTION] Fetched {} repayment entries from Encore for accountId={}",
                                scheduleEntries.size(), encoreAccountId);
                    }
                } catch (Exception schedEx) {
                    log.warn("[LMS-SANCTION] Failed to fetch repayment schedule for {}: {}",
                            encoreAccountId, schedEx.getMessage());
                }

            } catch (Exception e) {
                log.error("[LMS-SANCTION] Encore account creation failed for {}: {}",
                        app.getApplicationNumber(), e.getMessage(), e);
                errorMessage = "Encore sanction error: " + e.getMessage();
                status = "ENCORE_ERROR";
            } finally {
                MDC.remove("applicationId");
                MDC.remove("productCode");
            }
        } else {
            log.info("[LMS-SANCTION] Encore not active — using local reference for {}",
                    app.getApplicationNumber());
        }

        String lmsRef = encoreAccountId != null ? encoreAccountId : "LMS-" + app.getApplicationNumber();

        // Generate local repayment schedule
        List<RepaymentScheduleEntry> schedule = generateRepaymentSchedule(amount, rate, tenure);
        LocalDate firstEmiDate = LocalDate.now().plusMonths(1).withDayOfMonth(5);
        BigDecimal emiAmount = schedule.isEmpty() ? BigDecimal.ZERO : schedule.get(0).getEmiAmount();

        // Persist handover (upsert — idempotent)
        LmsLoanHandover handover = existing.orElse(LmsLoanHandover.builder()
                .applicationNumber(app.getApplicationNumber())
                .build());
        handover.setBorrowerName(borrowerName);
        handover.setProductCode(encoreProductCode);
        handover.setSanctionedAmount(amount);
        handover.setInterestRate(rate);
        handover.setTenureMonths(tenure);
        handover.setEncoreAccountId(encoreAccountId);
        handover.setLmsReferenceId(lmsRef);
        handover.setHandoverStatus(status);
        handover.setFirstEmiDate(firstEmiDate);
        handover.setEmiAmount(emiAmount);
        handover.setErrorMessage(errorMessage);
        handover.setEncoreOpenAccountRequestJson(openAccountRequestJson);
        handover.setEncoreOpenAccountResponseJson(openAccountResponseJson);
        handover.setEncoreRepaymentScheduleJson(repaymentScheduleJson);
        handoverRepository.save(handover);

        // Persist account summary (upsert — idempotent)
        LmsAccountSummary summaryEntity = summaryRepository.findByApplicationNumber(app.getApplicationNumber())
                .orElse(LmsAccountSummary.builder()
                        .applicationNumber(app.getApplicationNumber())
                        .build());
        summaryEntity.setEncoreAccountId(encoreAccountId);
        summaryEntity.setLoanStatus("SANCTIONED");
        summaryEntity.setSanctionedAmount(amount);
        summaryEntity.setOutstandingPrincipal(amount);
        summaryEntity.setTotalPaid(BigDecimal.ZERO);
        summaryEntity.setOverdueAmount(BigDecimal.ZERO);
        summaryEntity.setTotalEmis(tenure);
        summaryEntity.setPaidEmis(0);
        summaryEntity.setOverdueEmis(0);
        summaryEntity.setNextEmiDate(firstEmiDate);
        summaryEntity.setNextEmiAmount(emiAmount);
        summaryEntity.setDpd(0);
        summaryEntity.setLastSyncedAt(Instant.now());
        summaryRepository.save(summaryEntity);

        log.info("[LMS-SANCTION] Complete for {} — lmsRef={}, encoreActive={}, status={}",
                app.getApplicationNumber(), lmsRef, encoreLmsApi.isActive(), status);

        return lmsRef;
    }

    private static String extractBorrowerName(LoanApplication app) {
        if (app.getPersonalInfo() == null) return "";
        Map<String, Object> pi = app.getPersonalInfo();
        Object name = pi.get("fullName");
        if (name == null) name = pi.get("name");
        if (name == null) {
            String first = String.valueOf(pi.getOrDefault("firstName", ""));
            String last = String.valueOf(pi.getOrDefault("lastName", ""));
            name = (first + " " + last).trim();
        }
        return name.toString();
    }

    /**
     * Copies cached Encore repayment schedule from handover onto the KFS row so EDI PDF generation
     * can resolve schedules even when live Encore is temporarily unavailable.
     */
    @Transactional
    public void attachHandoverScheduleToKfs(String applicationNumber, UUID kfsId) {
        if (applicationNumber == null || kfsId == null) {
            return;
        }
        handoverRepository.findByApplicationNumber(applicationNumber).ifPresent(handover -> {
            String json = handover.getEncoreRepaymentScheduleJson();
            if (json == null || json.isBlank()) {
                return;
            }
            kfsDocumentRepository.findById(kfsId).ifPresent(kfs -> {
                Map<String, Object> terms = kfs.getAdditionalTerms();
                Map<String, Object> updated = terms != null ? new LinkedHashMap<>(terms) : new LinkedHashMap<>();
                updated.put("encoreRepaymentScheduleJson", json);
                kfs.setAdditionalTerms(updated);
                kfsDocumentRepository.save(kfs);
                log.info("[LMS-SANCTION] Attached {}-char Encore schedule JSON to KFS {} for {}",
                        json.length(), kfsId, applicationNumber);
            });
        });
    }

    /**
     * After Encore account creation, pull {@code findPreOpenSummary} or {@code findSummaries} and
     * update the KFS row (APR, EDI/EMI, total payable, processing fee) for the Sanction &amp; KFS tab.
     */
    @Transactional
    public void reconcileKfsFromEncoreAfterSanction(LoanApplication app, UUID kfsId, String encoreAccountId) {
        if (app == null || kfsId == null || encoreAccountId == null || encoreAccountId.isBlank()) {
            return;
        }
        if (!encoreLmsApi.isActive()) {
            return;
        }
        Map<String, Object> charges = new LinkedHashMap<>();

        handoverRepository.findByApplicationNumber(app.getApplicationNumber()).ifPresent(handover -> {
            String scheduleJson = handover.getEncoreRepaymentScheduleJson();
            if (scheduleJson != null && !scheduleJson.isBlank()) {
                charges.put("encoreRepaymentScheduleJson", scheduleJson);
            }
        });

        try {
            List<Map<String, Object>> summaries = encoreLmsApi.findSummaries(List.of(encoreAccountId));
            if (summaries != null && !summaries.isEmpty()) {
                charges.put("encorePreOpenSummaryJson", objectMapper.writeValueAsString(summaries.get(0)));
                log.info("[LMS-SANCTION] KFS reconcile using findSummaries for accountId={}", encoreAccountId);
            }
        } catch (Exception e) {
            log.warn("[LMS-SANCTION] findSummaries for KFS reconcile failed for {}: {}",
                    encoreAccountId, e.getMessage());
        }

        if (!charges.containsKey("encorePreOpenSummaryJson")) {
            kfsDocumentRepository.findById(kfsId).ifPresent(kfs -> {
                Map<String, Object> terms = kfs.getAdditionalTerms();
                if (terms != null && terms.get("encorePreOpenSummaryJson") != null) {
                    charges.put("encorePreOpenSummaryJson", terms.get("encorePreOpenSummaryJson"));
                    log.info("[LMS-SANCTION] KFS reconcile reusing pre-sanction pre-open summary for {}",
                            app.getApplicationNumber());
                }
            });
        }

        if (!charges.containsKey("encorePreOpenSummaryJson") && !charges.containsKey("encoreRepaymentScheduleJson")) {
            log.warn("[LMS-SANCTION] No Encore summary available to reconcile KFS {} for {}",
                    kfsId, app.getApplicationNumber());
            return;
        }

        kfsService.applyEncorePreOpenSummary(kfsId, charges);
    }

    private void refreshEncoreRepaymentScheduleCache(LmsLoanHandover handover) {
        if (!encoreLmsApi.isActive()) {
            return;
        }
        String accountId = handover.getEncoreAccountId();
        if (accountId == null || accountId.isBlank()) {
            return;
        }
        String cached = handover.getEncoreRepaymentScheduleJson();
        if (cached != null && !cached.isBlank()) {
            return;
        }
        try {
            var scheduleEntries = encoreLmsApi.findRepaymentSchedule(accountId);
            if (scheduleEntries != null && !scheduleEntries.isEmpty()) {
                handover.setEncoreRepaymentScheduleJson(objectMapper.writeValueAsString(scheduleEntries));
                handoverRepository.save(handover);
                log.info("[LMS-SANCTION] Refreshed {} repayment entries for existing accountId={}",
                        scheduleEntries.size(), accountId);
            }
        } catch (Exception e) {
            log.warn("[LMS-SANCTION] Could not refresh repayment schedule for {}: {}",
                    accountId, e.getMessage());
        }
    }

    /**
     * Calls Encore {@code findPreOpenSummary} and stores JSON under {@code encorePreOpenSummaryJson} in {@code charges}.
     * Always runs for EDI (Day tenure); for other loans only when {@code pre-open-summary-for-kfs} is enabled.
     */
    public void appendPreOpenEncoreSummaryForKfsIfEnabled(LoanApplication app, Map<String, Object> charges) {
        if (!encoreLmsApi.isActive()) {
            return;
        }
        boolean edi = "day".equalsIgnoreCase(lmsApplicationConfigResolver.resolveTenureUnit(app));
        if (!edi && !encoreClientProperties.getBlCoreParity().isPreOpenSummaryForKfs()) {
            return;
        }
        if (app.getTenureMonths() == null) {
            return;
        }
        BigDecimal amt = app.getSanctionedAmount() != null ? app.getSanctionedAmount() : app.getRequestedAmount();
        if (amt == null) {
            return;
        }
        String loanP = app.getLoanProduct() != null ? app.getLoanProduct() : "";
        String product = lmsApplicationConfigResolver.resolveEncoreProductCode(app);
        String tenureUnit = lmsApplicationConfigResolver.resolveTenureUnit(app);
        log.info("[LMS-SANCTION] KFS pre-open summary productCode={} tenureUnit={} for app={}",
                product, tenureUnit, app.getApplicationNumber());
        String body = blCoreEncoreLmsAdapter.buildPreOpenSummaryRequestBody(
                app.getApplicationNumber(),
                amt,
                LocalDate.now(),
                product,
                app.getTenureMonths(),
                tenureUnit,
                ApplicationPartyResolver.resolvePincode(app));
        try {
            String resp = encoreLmsApi.findPreOpenSummaryRaw(body);
            charges.put("encorePreOpenSummaryJson", resp);
        } catch (Exception e) {
            log.warn("Encore findPreOpenSummary for KFS skipped: {}", e.getMessage());
        }
    }

    private void maybeValidateEncoreWorkingDate() {
        if (!encoreClientProperties.getBlCoreParity().isValidateBankWorkingDateBeforeMutations()) {
            return;
        }
        String raw = encoreLmsApi.findBankWorkingDateRaw();
        if (raw == null || raw.isBlank()) {
            return;
        }
        LocalDate encoreDay = tryParseEncoreWorkingDate(raw.trim());
        if (encoreDay != null && !encoreDay.equals(LocalDate.now())) {
            throw new IllegalStateException("Encore bank working date " + encoreDay + " does not match host date "
                    + LocalDate.now() + " (raw=" + raw + ")");
        }
    }

    private LocalDate tryParseEncoreWorkingDate(String raw) {
        try {
            JsonNode n = objectMapper.readTree(raw);
            if (n.isTextual()) {
                return tryParseIsoOrDdMmYy(n.asText().trim());
            }
            if (n.isObject()) {
                java.util.Iterator<String> it = n.fieldNames();
                while (it.hasNext()) {
                    String k = it.next();
                    if (k.toLowerCase().contains("date") || k.toLowerCase().contains("working")) {
                        JsonNode v = n.get(k);
                        if (v != null && v.isTextual()) {
                            LocalDate d = tryParseIsoOrDdMmYy(v.asText().trim());
                            if (d != null) {
                                return d;
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return tryParseIsoOrDdMmYy(raw);
    }

    private static LocalDate tryParseIsoOrDdMmYy(String s) {
        if (s.length() >= 10 && s.charAt(4) == '-') {
            try {
                return LocalDate.parse(s.substring(0, 10));
            } catch (Exception ignored) {
                return null;
            }
        }
        if (s.length() >= 10 && Character.isDigit(s.charAt(0)) && s.charAt(2) == '-') {
            try {
                String[] p = s.substring(0, 10).split("-");
                if (p.length == 3 && p[0].length() <= 2) {
                    int day = Integer.parseInt(p[0]);
                    int month = Integer.parseInt(p[1]);
                    int year = Integer.parseInt(p[2]);
                    return LocalDate.of(year, month, day);
                }
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static LocalDate parseHandoverDisbursementDate(LoanHandoverRequest request) {
        if (request != null && request.getDisbursementDate() != null && !request.getDisbursementDate().isBlank()) {
            return LocalDate.parse(request.getDisbursementDate().trim());
        }
        return LocalDate.now();
    }

    // ---- Helper methods ----

    private static EncoreOpenLoanParams toEncoreParams(LoanHandoverRequest request) {
        int tenureMonths = request.getTenureMonths() != null ? request.getTenureMonths() : 0;
        int numInstallments = request.getNumberOfInstallments() != null
                ? request.getNumberOfInstallments() : tenureMonths;
        return new EncoreOpenLoanParams(
                request.getApplicationNumber(),
                request.getBorrowerName(),
                request.getSanctionedAmount(),
                request.getInterestRate(),
                tenureMonths,
                request.getProductCode(),
                request.getEncoreBranchCode(),
                request.getEncorePartyOrCustomerId(),
                request.getTenureUnit(),
                request.getPenalInterestRate(),
                numInstallments,
                request.getMoratoriumType(),
                request.getMoratoriumPeriodMagnitude() != null ? request.getMoratoriumPeriodMagnitude() : 0,
                request.getMoratoriumPeriodUnit(),
                request.isMoratoriumNormalInterestRateApplicable(),
                request.getMoratoriumNormalInterestRate(),
                request.getMoratoriumInterestAccrualCalculation(),
                extractGeo(request, "pinCode", "pincode", "postalCode"),
                extractGeo(request, "cityCode", "city"),
                extractGeo(request, "stateCode", "state"),
                extractGeo(request, "countryCode", "country"),
                request.getColendingApplicable(),
                request.getColenderProductCode(),
                request.getColenderId(),
                request.getColenderLendingRatio(),
                request.getColenderNormalInterestRate(),
                request.getDisbursementDate(),
                request.getUserId()
        );
    }

    private static String extractGeo(LoanHandoverRequest req, String... keys) {
        if (req.getBorrowerDetails() == null) return null;
        for (String k : keys) {
            Object v = req.getBorrowerDetails().get(k);
            if (v != null) {
                String s = String.valueOf(v).trim();
                if (!s.isEmpty()) return s;
            }
        }
        return null;
    }

    private LoanAccountSummary toAccountSummaryDto(LmsAccountSummary entity) {
        int dpd = entity.getDpd() != null ? entity.getDpd() : 0;

        // Derive NPA status from DPD (RBI classification)
        boolean npaFlag = dpd > 90;
        String npaCategory;
        if (dpd > 90) {
            npaCategory = "NPA";
        } else if (dpd > 60) {
            npaCategory = "SMA-2";
        } else if (dpd > 30) {
            npaCategory = "SMA-1";
        } else if (dpd > 0) {
            npaCategory = "SMA-0";
        } else {
            npaCategory = "STANDARD";
        }

        return LoanAccountSummary.builder()
                .applicationNumber(entity.getApplicationNumber())
                .lmsReferenceId(entity.getEncoreAccountId() != null
                        ? entity.getEncoreAccountId() : "LMS-" + entity.getApplicationNumber())
                .loanStatus(entity.getLoanStatus())
                .sanctionedAmount(entity.getSanctionedAmount())
                .disbursedAmount(entity.getDisbursedAmount())
                .outstandingPrincipal(entity.getOutstandingPrincipal())
                .totalPaid(entity.getTotalPaid())
                .overdueAmount(entity.getOverdueAmount())
                .encoreBlFeeDue(entity.getFeeBlDue())
                .encoreLenderFeeDue(entity.getFeeLenderDue())
                .encoreAccountStatementEntriesJson(entity.getEncoreAccountStatementEntriesJson())
                .totalEmis(entity.getTotalEmis() != null ? entity.getTotalEmis() : 0)
                .paidEmis(entity.getPaidEmis() != null ? entity.getPaidEmis() : 0)
                .overdueEmis(entity.getOverdueEmis() != null ? entity.getOverdueEmis() : 0)
                .nextEmiDate(entity.getNextEmiDate())
                .nextEmiAmount(entity.getNextEmiAmount())
                .lastPaymentDate(entity.getLastPaymentDate())
                .dpd(dpd)
                .npaFlag(npaFlag)
                .npaCategory(npaCategory)
                .build();
    }

    /**
     * Generate amortization schedule using reducing balance method.
     */
    private List<RepaymentScheduleEntry> generateRepaymentSchedule(
            BigDecimal principal, BigDecimal annualRate, int tenureMonths) {

        List<RepaymentScheduleEntry> schedule = new ArrayList<>();
        MathContext mc = new MathContext(10);

        BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(1200), mc);
        BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
        BigDecimal onePlusRPowN = onePlusR.pow(tenureMonths, mc);
        BigDecimal emi = principal.multiply(monthlyRate).multiply(onePlusRPowN)
                .divide(onePlusRPowN.subtract(BigDecimal.ONE), mc)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal outstanding = principal;
        LocalDate emiDate = LocalDate.now().plusMonths(1).withDayOfMonth(5);

        for (int i = 1; i <= tenureMonths; i++) {
            BigDecimal interest = outstanding.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP);
            BigDecimal principalComponent = emi.subtract(interest);

            if (i == tenureMonths) {
                principalComponent = outstanding;
                BigDecimal actualEmi = principalComponent.add(interest);
                schedule.add(RepaymentScheduleEntry.builder()
                        .installmentNumber(i)
                        .dueDate(emiDate)
                        .emiAmount(actualEmi)
                        .principalComponent(principalComponent)
                        .interestComponent(interest)
                        .outstandingPrincipal(BigDecimal.ZERO)
                        .status("UPCOMING")
                        .build());
            } else {
                outstanding = outstanding.subtract(principalComponent);
                schedule.add(RepaymentScheduleEntry.builder()
                        .installmentNumber(i)
                        .dueDate(emiDate)
                        .emiAmount(emi)
                        .principalComponent(principalComponent)
                        .interestComponent(interest)
                        .outstandingPrincipal(outstanding.setScale(2, RoundingMode.HALF_UP))
                        .status("UPCOMING")
                        .build());
            }
            emiDate = emiDate.plusMonths(1);
        }

        return schedule;
    }

    private void validateRepaymentCallback(RepaymentCallbackRequest callback) {
        if (callback.getApplicationNumber() == null || callback.getApplicationNumber().isBlank()) {
            throw new IllegalArgumentException("applicationNumber is required");
        }
        if (callback.getPaidAmount() == null || callback.getPaidAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("paidAmount must be positive");
        }
    }

    private boolean isEncoreAllowedForApplication(String applicationNumber) {
        Optional<com.los.plp.model.entity.ProgramMaster> program =
                lmsProgramResolver.resolveByApplicationNumber(applicationNumber);
        if (program.isEmpty()) {
            return true;
        }
        return lmsProgramResolver.isLmsEntryEnabled(program.get());
    }

    private RepaymentScheduleResponse scheduleFromCachedEncoreJson(
            String json,
            String applicationNumber,
            String lmsRef,
            BigDecimal amount,
            BigDecimal rate,
            int tenure) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            List<Map<String, Object>> cached = objectMapper.readValue(json, new TypeReference<>() {});
            RepaymentScheduleResponse resp = buildScheduleResponse(
                    applicationNumber, lmsRef, amount, rate, tenure,
                    mapEncoreScheduleMaps(cached, "FROM_ENCORE"));
            if (resp != null) {
                log.info("Using cached Encore repayment schedule for {} ({} rows)", applicationNumber,
                        resp.getSchedule().size());
            }
            return resp;
        } catch (Exception e) {
            log.warn("Failed to parse cached Encore schedule for {}: {}", applicationNumber, e.getMessage());
            return null;
        }
    }

    private RepaymentScheduleResponse buildScheduleResponse(
            String applicationNumber,
            String lmsRef,
            BigDecimal amount,
            BigDecimal rate,
            int tenure,
            List<RepaymentScheduleEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        BigDecimal totalInterest = entries.stream()
                .map(RepaymentScheduleEntry::getInterestComponent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return RepaymentScheduleResponse.builder()
                .applicationNumber(applicationNumber)
                .lmsReferenceId(lmsRef)
                .sanctionedAmount(amount)
                .interestRate(rate)
                .tenureMonths(tenure)
                .totalInterest(totalInterest)
                .totalPayable(amount.add(totalInterest))
                .schedule(entries)
                .build();
    }

    private List<RepaymentScheduleEntry> mapEncoreScheduleMaps(
            List<Map<String, Object>> encoreSchedule, String status) {
        if (encoreSchedule == null || encoreSchedule.isEmpty()) {
            return List.of();
        }
        return encoreSchedule.stream().map(e -> {
            BigDecimal instAmount = new BigDecimal(String.valueOf(e.getOrDefault("installmentAmount", "0")));
            String dueRaw = String.valueOf(e.getOrDefault("valueDateStr", LocalDate.now().toString()));
            LocalDate dueDate;
            try {
                dueDate = LocalDate.parse(dueRaw);
            } catch (Exception ignored) {
                dueDate = LocalDate.now();
            }
            return RepaymentScheduleEntry.builder()
                    .installmentNumber(((Number) e.getOrDefault("sequenceNum", 0)).intValue())
                    .dueDate(dueDate)
                    .emiAmount(instAmount)
                    .principalComponent(new BigDecimal(String.valueOf(e.getOrDefault("principalAmount", "0"))))
                    .interestComponent(new BigDecimal(String.valueOf(e.getOrDefault("interestAmount", "0"))))
                    .outstandingPrincipal(new BigDecimal(String.valueOf(e.getOrDefault("balance", "0"))))
                    .status(status)
                    .build();
        }).toList();
    }

    private static List<RepaymentScheduleEntry> withScheduleStatus(
            List<RepaymentScheduleEntry> schedule, String status) {
        return schedule.stream()
                .map(e -> RepaymentScheduleEntry.builder()
                        .installmentNumber(e.getInstallmentNumber())
                        .dueDate(e.getDueDate())
                        .emiAmount(e.getEmiAmount())
                        .principalComponent(e.getPrincipalComponent())
                        .interestComponent(e.getInterestComponent())
                        .outstandingPrincipal(e.getOutstandingPrincipal())
                        .status(status)
                        .build())
                .toList();
    }

}
