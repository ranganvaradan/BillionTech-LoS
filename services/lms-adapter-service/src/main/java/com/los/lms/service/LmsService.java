package com.los.lms.service;

import com.los.lms.dto.*;
import com.los.lms.entity.LmsAccountSummary;
import com.los.lms.entity.LmsLoanHandover;
import com.los.lms.entity.LmsRepaymentCallback;
import com.los.lms.repository.LmsAccountSummaryRepository;
import com.los.lms.repository.LmsLoanHandoverRepository;
import com.los.lms.repository.LmsRepaymentCallbackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final EncoreLmsService encoreLmsService;
    private final LmsLoanHandoverRepository handoverRepository;
    private final LmsRepaymentCallbackRepository repaymentRepository;
    private final LmsAccountSummaryRepository summaryRepository;

    /**
     * Hand over a disbursed loan to LMS for servicing.
     * When Encore is configured: opens loan account + posts disbursement in Encore.
     * Always persists to database.
     */
    @Transactional
    public LoanHandoverResponse handoverLoan(LoanHandoverRequest request) {
        String encoreAccountId = null;
        String encoreTransactionId = null;
        String status = "ACCEPTED";
        String errorMessage = null;

        // Step 1: Open loan account in Encore (if configured)
        if (encoreLmsService.isActive()) {
            try {
                encoreAccountId = encoreLmsService.openLoanAccount(request);
                encoreTransactionId = encoreLmsService.disburse(encoreAccountId, request);
                log.info("Encore loan account opened: {} with disbursement txn: {}",
                        encoreAccountId, encoreTransactionId);
            } catch (Exception e) {
                log.error("Encore handover failed for {}: {}", request.getApplicationNumber(), e.getMessage());
                errorMessage = "Encore error: " + e.getMessage();
                status = "ENCORE_ERROR";
                // Continue with local processing — Encore failure is non-blocking
            }
        } else {
            log.info("[LMS] Encore not configured — using local calculation for {}",
                    request.getApplicationNumber());
        }

        String lmsRef = encoreAccountId != null ? encoreAccountId : "LMS-" + request.getApplicationNumber();

        // Step 2: Generate repayment schedule locally
        List<RepaymentScheduleEntry> schedule = generateRepaymentSchedule(
                request.getSanctionedAmount(),
                request.getInterestRate(),
                request.getTenureMonths()
        );

        LocalDate firstEmiDate = LocalDate.now().plusMonths(1).withDayOfMonth(5);
        BigDecimal emiAmount = schedule.isEmpty() ? BigDecimal.ZERO : schedule.get(0).getEmiAmount();

        // Step 3: Persist handover to database
        LmsLoanHandover handover = LmsLoanHandover.builder()
                .applicationNumber(request.getApplicationNumber())
                .borrowerName(request.getBorrowerName())
                .productCode(request.getProductCode())
                .sanctionedAmount(request.getSanctionedAmount())
                .interestRate(request.getInterestRate())
                .tenureMonths(request.getTenureMonths())
                .encoreAccountId(encoreAccountId)
                .encoreTransactionId(encoreTransactionId)
                .lmsReferenceId(lmsRef)
                .handoverStatus(status)
                .disbursementDate(LocalDate.now())
                .firstEmiDate(firstEmiDate)
                .emiAmount(emiAmount)
                .errorMessage(errorMessage)
                .build();
        handoverRepository.save(handover);

        // Step 4: Persist account summary to database
        LmsAccountSummary summaryEntity = LmsAccountSummary.builder()
                .applicationNumber(request.getApplicationNumber())
                .encoreAccountId(encoreAccountId)
                .loanStatus("ACTIVE")
                .sanctionedAmount(request.getSanctionedAmount())
                .disbursedAmount(request.getSanctionedAmount())
                .outstandingPrincipal(request.getSanctionedAmount())
                .totalPaid(BigDecimal.ZERO)
                .overdueAmount(BigDecimal.ZERO)
                .totalEmis(request.getTenureMonths())
                .paidEmis(0)
                .overdueEmis(0)
                .nextEmiDate(firstEmiDate)
                .nextEmiAmount(emiAmount)
                .dpd(0)
                .lastSyncedAt(Instant.now())
                .build();
        summaryRepository.save(summaryEntity);

        log.info("Loan handed over to LMS: {} -> {} (encore={})",
                request.getApplicationNumber(), lmsRef, encoreLmsService.isActive());

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
            if (encoreLmsService.isActive() && entity.getEncoreAccountId() != null) {
                try {
                    List<Map<String, Object>> encoreSummaries =
                            encoreLmsService.findSummaries(List.of(entity.getEncoreAccountId()));
                    if (!encoreSummaries.isEmpty()) {
                        Map<String, Object> encoreData = encoreSummaries.get(0);
                        // Update entity from Encore data
                        if (encoreData.containsKey("accountBalance")) {
                            entity.setOutstandingPrincipal(
                                    new BigDecimal(String.valueOf(encoreData.get("accountBalance"))));
                        }
                        if (encoreData.containsKey("operationalStatus")) {
                            entity.setLoanStatus(String.valueOf(encoreData.get("operationalStatus")));
                        }
                        entity.setLastSyncedAt(Instant.now());
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

        if (handoverOpt.isPresent()) {
            LmsLoanHandover handover = handoverOpt.get();
            amount = handover.getSanctionedAmount();
            rate = handover.getInterestRate();
            tenure = handover.getTenureMonths();

            // Try Encore repayment schedule if configured
            if (encoreLmsService.isActive() && handover.getEncoreAccountId() != null) {
                try {
                    List<Map<String, Object>> encoreSchedule =
                            encoreLmsService.findRepaymentSchedule(handover.getEncoreAccountId());
                    if (!encoreSchedule.isEmpty()) {
                        // Convert Encore schedule to our DTO format
                        List<RepaymentScheduleEntry> entries = encoreSchedule.stream().map(e -> {
                            BigDecimal instAmount = new BigDecimal(String.valueOf(e.getOrDefault("installmentAmount", "0")));
                            return RepaymentScheduleEntry.builder()
                                    .installmentNumber(((Number) e.getOrDefault("sequenceNum", 0)).intValue())
                                    .dueDate(LocalDate.parse(String.valueOf(e.getOrDefault("valueDateStr", LocalDate.now().toString()))))
                                    .emiAmount(instAmount)
                                    .principalComponent(new BigDecimal(String.valueOf(e.getOrDefault("principalAmount", "0"))))
                                    .interestComponent(new BigDecimal(String.valueOf(e.getOrDefault("interestAmount", "0"))))
                                    .outstandingPrincipal(new BigDecimal(String.valueOf(e.getOrDefault("balance", "0"))))
                                    .status("FROM_ENCORE")
                                    .build();
                        }).toList();

                        BigDecimal totalInterest = entries.stream()
                                .map(RepaymentScheduleEntry::getInterestComponent)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                        return RepaymentScheduleResponse.builder()
                                .applicationNumber(applicationNumber)
                                .lmsReferenceId(handover.getEncoreAccountId())
                                .sanctionedAmount(amount)
                                .interestRate(rate)
                                .tenureMonths(tenure)
                                .totalInterest(totalInterest)
                                .totalPayable(amount.add(totalInterest))
                                .schedule(entries)
                                .build();
                    }
                } catch (Exception e) {
                    log.warn("Failed to get Encore schedule for {}: {}", applicationNumber, e.getMessage());
                }
            }
        } else {
            // Use defaults for simulated schedule
            amount = new BigDecimal("500000");
            rate = new BigDecimal("12.5");
            tenure = 36;
        }

        // Generate schedule locally
        List<RepaymentScheduleEntry> schedule = generateRepaymentSchedule(amount, rate, tenure);

        BigDecimal totalInterest = schedule.stream()
                .map(RepaymentScheduleEntry::getInterestComponent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return RepaymentScheduleResponse.builder()
                .applicationNumber(applicationNumber)
                .lmsReferenceId("LMS-" + applicationNumber)
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
     */
    @Transactional
    public Map<String, Object> processRepaymentCallback(RepaymentCallbackRequest callback) {
        // Post to Encore if configured
        Optional<LmsLoanHandover> handoverOpt = handoverRepository.findByApplicationNumber(callback.getApplicationNumber());
        String encoreAccountId = handoverOpt.map(LmsLoanHandover::getEncoreAccountId).orElse(null);
        String encoreTxnId = null;

        if (encoreLmsService.isActive() && encoreAccountId != null) {
            try {
                encoreTxnId = encoreLmsService.repay(
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
                .paymentDate(callback.getPaymentDate())
                .paymentMode(callback.getPaymentMode())
                .utrNumber(callback.getUtrNumber())
                .status("PROCESSED")
                .build();
        repaymentRepository.save(entity);

        // Update account summary in DB
        Optional<LmsAccountSummary> summaryOpt = summaryRepository.findByApplicationNumber(callback.getApplicationNumber());
        if (summaryOpt.isPresent()) {
            LmsAccountSummary summary = summaryOpt.get();
            summary.setPaidEmis((summary.getPaidEmis() != null ? summary.getPaidEmis() : 0) + 1);
            summary.setTotalPaid((summary.getTotalPaid() != null ? summary.getTotalPaid() : BigDecimal.ZERO).add(callback.getPaidAmount()));
            summary.setOutstandingPrincipal(
                    (summary.getOutstandingPrincipal() != null ? summary.getOutstandingPrincipal() : BigDecimal.ZERO).subtract(callback.getPaidAmount()));
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
     * Get payment history for a loan (from database).
     */
    public List<RepaymentCallbackRequest> getPaymentHistory(String applicationNumber) {
        List<LmsRepaymentCallback> callbacks =
                repaymentRepository.findByApplicationNumberOrderByCreatedAtDesc(applicationNumber);
        return callbacks.stream().map(cb -> RepaymentCallbackRequest.builder()
                .applicationNumber(cb.getApplicationNumber())
                .installmentNumber(cb.getInstallmentNumber() != null ? cb.getInstallmentNumber() : 0)
                .paidAmount(cb.getAmount())
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
        summary.put("encoreActive", encoreLmsService.isActive());

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
        if (encoreLmsService.isActive() && summary.getEncoreAccountId() != null) {
            try {
                String rpyType = isFullPrepayment ? "Pre-closure" : "Prepayment";
                encoreLmsService.repay(summary.getEncoreAccountId(), prepaymentAmount, rpyType);
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
     */
    public List<Map<String, Object>> getEncoreAccountStatement(String applicationNumber,
                                                                 String fromDate, String toDate) {
        Optional<LmsLoanHandover> handoverOpt = handoverRepository.findByApplicationNumber(applicationNumber);
        if (handoverOpt.isEmpty() || handoverOpt.get().getEncoreAccountId() == null) {
            return Collections.emptyList();
        }
        return encoreLmsService.getAccountStatement(handoverOpt.get().getEncoreAccountId(), fromDate, toDate);
    }

    // ---- Helper methods ----

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
}
