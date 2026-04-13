package com.los.lms.service;

import com.los.lms.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LMS Adapter Service — manages loan handover, repayment schedule generation,
 * account summaries, and repayment callbacks.
 *
 * In production this would integrate with an external LMS via REST/SOAP.
 * Currently uses in-memory storage for simulation.
 */
@Slf4j
@Service
public class LmsService {

    private final Map<String, LoanHandoverRequest> handovers = new ConcurrentHashMap<>();
    private final Map<String, LoanAccountSummary> accounts = new ConcurrentHashMap<>();
    private final Map<String, List<RepaymentScheduleEntry>> schedules = new ConcurrentHashMap<>();
    private final Map<String, List<RepaymentCallbackRequest>> payments = new ConcurrentHashMap<>();

    /**
     * Hand over a disbursed loan to LMS for servicing.
     */
    public LoanHandoverResponse handoverLoan(LoanHandoverRequest request) {
        String lmsRef = "LMS-" + request.getApplicationNumber();

        handovers.put(request.getApplicationNumber(), request);

        // Generate repayment schedule
        List<RepaymentScheduleEntry> schedule = generateRepaymentSchedule(
                request.getSanctionedAmount(),
                request.getInterestRate(),
                request.getTenureMonths()
        );
        schedules.put(request.getApplicationNumber(), schedule);

        LocalDate firstEmiDate = LocalDate.now().plusMonths(1).withDayOfMonth(5);
        BigDecimal emiAmount = schedule.isEmpty() ? BigDecimal.ZERO : schedule.get(0).getEmiAmount();

        // Create initial account summary
        LoanAccountSummary summary = LoanAccountSummary.builder()
                .applicationNumber(request.getApplicationNumber())
                .lmsReferenceId(lmsRef)
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
                .lastPaymentDate(null)
                .dpd(0)
                .build();
        accounts.put(request.getApplicationNumber(), summary);

        log.info("Loan handed over to LMS: {} -> {}", request.getApplicationNumber(), lmsRef);

        return LoanHandoverResponse.builder()
                .handoverId(UUID.randomUUID())
                .applicationNumber(request.getApplicationNumber())
                .lmsReferenceId(lmsRef)
                .status("ACCEPTED")
                .message("Loan successfully handed over to LMS for servicing")
                .firstEmiDate(firstEmiDate)
                .emiAmount(emiAmount)
                .totalEmis(request.getTenureMonths())
                .build();
    }

    /**
     * Get loan account summary from LMS.
     */
    public LoanAccountSummary getAccountSummary(String applicationNumber) {
        LoanAccountSummary summary = accounts.get(applicationNumber);
        if (summary == null) {
            // Return a default simulated summary
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
        return summary;
    }

    /**
     * Get full repayment schedule for a loan.
     */
    public RepaymentScheduleResponse getRepaymentSchedule(String applicationNumber) {
        List<RepaymentScheduleEntry> schedule = schedules.get(applicationNumber);
        LoanHandoverRequest handover = handovers.get(applicationNumber);

        if (schedule == null || handover == null) {
            // Generate a simulated schedule
            BigDecimal amount = new BigDecimal("500000");
            BigDecimal rate = new BigDecimal("12.5");
            int tenure = 36;
            schedule = generateRepaymentSchedule(amount, rate, tenure);

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

        BigDecimal totalInterest = schedule.stream()
                .map(RepaymentScheduleEntry::getInterestComponent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return RepaymentScheduleResponse.builder()
                .applicationNumber(applicationNumber)
                .lmsReferenceId("LMS-" + applicationNumber)
                .sanctionedAmount(handover.getSanctionedAmount())
                .interestRate(handover.getInterestRate())
                .tenureMonths(handover.getTenureMonths())
                .totalInterest(totalInterest)
                .totalPayable(handover.getSanctionedAmount().add(totalInterest))
                .schedule(schedule)
                .build();
    }

    /**
     * Process a repayment callback from LMS.
     */
    public Map<String, Object> processRepaymentCallback(RepaymentCallbackRequest callback) {
        payments.computeIfAbsent(callback.getApplicationNumber(), k -> new ArrayList<>()).add(callback);

        // Update account summary
        LoanAccountSummary summary = accounts.get(callback.getApplicationNumber());
        if (summary != null) {
            summary.setPaidEmis(summary.getPaidEmis() + 1);
            summary.setTotalPaid(summary.getTotalPaid().add(callback.getPaidAmount()));
            summary.setOutstandingPrincipal(
                    summary.getOutstandingPrincipal().subtract(callback.getPaidAmount())
            );
            summary.setLastPaymentDate(callback.getPaymentDate());
            if (summary.getNextEmiDate() != null) {
                summary.setNextEmiDate(summary.getNextEmiDate().plusMonths(1));
            }
        }

        log.info("Repayment callback processed: {} installment #{} amount={}",
                callback.getApplicationNumber(), callback.getInstallmentNumber(), callback.getPaidAmount());

        return Map.of(
                "status", "PROCESSED",
                "applicationNumber", callback.getApplicationNumber(),
                "installmentNumber", callback.getInstallmentNumber(),
                "message", "Repayment recorded successfully"
        );
    }

    /**
     * Get payment history for a loan.
     */
    public List<RepaymentCallbackRequest> getPaymentHistory(String applicationNumber) {
        return payments.getOrDefault(applicationNumber, List.of());
    }

    /**
     * Get all active loan accounts.
     */
    public List<LoanAccountSummary> getAllAccounts() {
        return new ArrayList<>(accounts.values());
    }

    /**
     * Update NPA flag for a loan account based on DPD (BR-11.4).
     * SMA-0: 1-30 days, SMA-1: 31-60 days, SMA-2: 61-90 days, NPA: >90 days.
     */
    public Map<String, Object> updateNpaStatus(String applicationNumber, int currentDpd) {
        LoanAccountSummary summary = accounts.get(applicationNumber);
        if (summary == null) {
            return Map.of("status", "NOT_FOUND", "applicationNumber", applicationNumber);
        }

        summary.setDpd(currentDpd);
        String previousStatus = summary.getLoanStatus();

        if (currentDpd > 90) {
            summary.setNpaFlag(true);
            summary.setNpaCategory("NPA");
            summary.setNpaDate(LocalDate.now());
            summary.setLoanStatus("NPA");
        } else if (currentDpd > 60) {
            summary.setNpaFlag(false);
            summary.setNpaCategory("SMA-2");
            summary.setLoanStatus("SMA-2");
        } else if (currentDpd > 30) {
            summary.setNpaFlag(false);
            summary.setNpaCategory("SMA-1");
            summary.setLoanStatus("SMA-1");
        } else if (currentDpd > 0) {
            summary.setNpaFlag(false);
            summary.setNpaCategory("SMA-0");
            summary.setLoanStatus("SMA-0");
        } else {
            summary.setNpaFlag(false);
            summary.setNpaCategory("STANDARD");
            summary.setLoanStatus("ACTIVE");
        }

        log.info("NPA status updated for {}: DPD={}, category={}, previousStatus={}",
                applicationNumber, currentDpd, summary.getNpaCategory(), previousStatus);

        return Map.of(
                "applicationNumber", applicationNumber,
                "dpd", currentDpd,
                "npaFlag", summary.isNpaFlag(),
                "npaCategory", summary.getNpaCategory(),
                "loanStatus", summary.getLoanStatus(),
                "previousStatus", previousStatus
        );
    }

    /**
     * Get collection summary — overdue accounts, DPD buckets, NPA portfolio.
     */
    public Map<String, Object> getCollectionSummary() {
        List<LoanAccountSummary> allAccounts = new ArrayList<>(accounts.values());

        long totalAccounts = allAccounts.size();
        long overdueAccounts = allAccounts.stream().filter(a -> a.getDpd() > 0).count();
        long npaAccounts = allAccounts.stream().filter(LoanAccountSummary::isNpaFlag).count();

        BigDecimal totalOverdue = allAccounts.stream()
                .filter(a -> a.getDpd() > 0)
                .map(LoanAccountSummary::getOverdueAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalOutstanding = allAccounts.stream()
                .map(LoanAccountSummary::getOutstandingPrincipal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // DPD bucket analysis
        long sma0 = allAccounts.stream().filter(a -> a.getDpd() > 0 && a.getDpd() <= 30).count();
        long sma1 = allAccounts.stream().filter(a -> a.getDpd() > 30 && a.getDpd() <= 60).count();
        long sma2 = allAccounts.stream().filter(a -> a.getDpd() > 60 && a.getDpd() <= 90).count();
        long npa = allAccounts.stream().filter(a -> a.getDpd() > 90).count();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalAccounts", totalAccounts);
        summary.put("overdueAccounts", overdueAccounts);
        summary.put("npaAccounts", npaAccounts);
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

        return summary;
    }

    /**
     * BR-11.5: Process prepayment — partial or full.
     */
    public Map<String, Object> processPrepayment(String applicationNumber, BigDecimal prepaymentAmount,
                                                   String prepaymentType) {
        LoanAccountSummary summary = accounts.get(applicationNumber);
        if (summary == null) {
            return Map.of("status", "NOT_FOUND", "applicationNumber", applicationNumber);
        }

        BigDecimal outstanding = summary.getOutstandingPrincipal();
        boolean isFullPrepayment = "FULL".equalsIgnoreCase(prepaymentType)
                || prepaymentAmount.compareTo(outstanding) >= 0;

        BigDecimal foreclosureCharges = BigDecimal.ZERO;
        if (isFullPrepayment) {
            // Foreclosure charges: 2% of outstanding for fixed rate, 0 for floating
            foreclosureCharges = outstanding.multiply(new BigDecimal("0.02"))
                    .setScale(2, RoundingMode.HALF_UP);
            summary.setOutstandingPrincipal(BigDecimal.ZERO);
            summary.setLoanStatus("CLOSED");
            summary.setNextEmiDate(null);
            summary.setNextEmiAmount(BigDecimal.ZERO);
        } else {
            summary.setOutstandingPrincipal(outstanding.subtract(prepaymentAmount));
            // Recalculate EMI based on reduced principal
            int remainingEmis = summary.getTotalEmis() - summary.getPaidEmis();
            if (remainingEmis > 0) {
                LoanHandoverRequest handover = handovers.get(applicationNumber);
                BigDecimal rate = handover != null ? handover.getInterestRate() : new BigDecimal("12.5");
                List<RepaymentScheduleEntry> newSchedule = generateRepaymentSchedule(
                        summary.getOutstandingPrincipal(), rate, remainingEmis);
                schedules.put(applicationNumber, newSchedule);
                if (!newSchedule.isEmpty()) {
                    summary.setNextEmiAmount(newSchedule.get(0).getEmiAmount());
                }
            }
        }

        summary.setTotalPaid(summary.getTotalPaid().add(prepaymentAmount));

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
    public Map<String, Object> processTrancheDisbursement(String applicationNumber, BigDecimal trancheAmount,
                                                            int trancheNumber, int totalTranches) {
        LoanAccountSummary summary = accounts.get(applicationNumber);
        if (summary == null) {
            return Map.of("status", "NOT_FOUND", "applicationNumber", applicationNumber);
        }

        BigDecimal previouslyDisbursed = summary.getDisbursedAmount();
        BigDecimal newDisbursed = previouslyDisbursed.add(trancheAmount);
        summary.setDisbursedAmount(newDisbursed);
        summary.setOutstandingPrincipal(summary.getOutstandingPrincipal().add(trancheAmount));

        log.info("Tranche disbursement for {}: tranche {}/{}, amount={}, totalDisbursed={}",
                applicationNumber, trancheNumber, totalTranches, trancheAmount, newDisbursed);

        return Map.of(
                "applicationNumber", applicationNumber,
                "trancheNumber", trancheNumber,
                "totalTranches", totalTranches,
                "trancheAmount", trancheAmount,
                "totalDisbursed", newDisbursed,
                "sanctionedAmount", summary.getSanctionedAmount(),
                "remainingToDisburse", summary.getSanctionedAmount().subtract(newDisbursed),
                "status", trancheNumber >= totalTranches ? "FULLY_DISBURSED" : "PARTIALLY_DISBURSED"
        );
    }

    /**
     * Generate amortization schedule using reducing balance method.
     */
    private List<RepaymentScheduleEntry> generateRepaymentSchedule(
            BigDecimal principal, BigDecimal annualRate, int tenureMonths) {

        List<RepaymentScheduleEntry> schedule = new ArrayList<>();
        MathContext mc = new MathContext(10);

        BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(1200), mc);
        // EMI = P * r * (1+r)^n / ((1+r)^n - 1)
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
