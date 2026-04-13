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
