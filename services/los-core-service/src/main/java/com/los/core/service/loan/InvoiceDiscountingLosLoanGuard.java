package com.los.core.service.loan;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.IntakeSegment;
import com.los.core.repository.LoanApplicationRepository;
import com.los.plp.repository.ProgramMasterRepository;
import com.los.plp.repository.SubProgramMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Invoice discounting borrower onboarding must not create LOS term-loan records
 * ({@code lms_loan_handovers} / {@code lms_account_summaries}) at sanction or disbursement.
 */
@Component
@RequiredArgsConstructor
public class InvoiceDiscountingLosLoanGuard {

    private final SubProgramMasterRepository subProgramMasterRepository;
    private final ProgramMasterRepository programMasterRepository;
    private final LoanApplicationRepository loanApplicationRepository;

    public boolean skipsLosTermLoanCreation(LoanApplication app) {
        if (app == null) {
            return false;
        }
        if (InvoiceDiscountingApplicationRules.isBorrowerFlow(app)) {
            return true;
        }
        if (app.getSubProgramId() == null || app.getIntakeSegment() == IntakeSegment.ANCHOR) {
            return false;
        }
        return subProgramMasterRepository.findById(app.getSubProgramId())
                .flatMap(sp -> programMasterRepository.findById(sp.getProgramId()))
                .map(p -> "INVOICE_DISCOUNTING".equalsIgnoreCase(p.getProductType()))
                .orElse(false);
    }

    public boolean skipsLosTermLoanCreation(UUID applicationId) {
        if (applicationId == null) {
            return false;
        }
        return loanApplicationRepository.findById(applicationId)
                .map(this::skipsLosTermLoanCreation)
                .orElse(false);
    }

    public boolean skipsLosTermLoanCreation(String applicationNumber) {
        if (applicationNumber == null || applicationNumber.isBlank()) {
            return false;
        }
        return loanApplicationRepository.findByApplicationNumber(applicationNumber)
                .map(this::skipsLosTermLoanCreation)
                .orElse(false);
    }
}
