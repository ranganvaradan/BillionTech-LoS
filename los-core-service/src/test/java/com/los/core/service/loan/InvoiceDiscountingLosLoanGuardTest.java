package com.los.core.service.loan;

import com.los.core.model.catalog.StandardLoanProduct;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.BorrowerType;
import com.los.core.model.enums.IntakeSegment;
import com.los.core.repository.LoanApplicationRepository;
import com.los.plp.model.entity.ProgramMaster;
import com.los.plp.model.entity.SubProgramMaster;
import com.los.plp.repository.ProgramMasterRepository;
import com.los.plp.repository.SubProgramMasterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceDiscountingLosLoanGuardTest {

    @Mock
    SubProgramMasterRepository subProgramMasterRepository;
    @Mock
    ProgramMasterRepository programMasterRepository;
    @Mock
    LoanApplicationRepository loanApplicationRepository;

    @InjectMocks
    InvoiceDiscountingLosLoanGuard guard;

    @Test
    void skipsLosTermLoanCreation_forInvoiceDiscountingBorrowerProduct() {
        LoanApplication app = LoanApplication.builder()
                .loanProduct(StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)
                .intakeSegment(IntakeSegment.BORROWER)
                .build();

        assertThat(guard.skipsLosTermLoanCreation(app)).isTrue();
    }

    @Test
    void skipsLosTermLoanCreation_whenSubProgramLinkedToInvoiceDiscountingProgram() {
        UUID subProgramId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
                .loanProduct("BUSINESS_WC_OD")
                .intakeSegment(IntakeSegment.BORROWER)
                .subProgramId(subProgramId)
                .build();

        when(subProgramMasterRepository.findById(subProgramId))
                .thenReturn(Optional.of(SubProgramMaster.builder().programId(programId).build()));
        when(programMasterRepository.findById(programId))
                .thenReturn(Optional.of(ProgramMaster.builder().productType("INVOICE_DISCOUNTING").build()));

        assertThat(guard.skipsLosTermLoanCreation(app)).isTrue();
    }

    @Test
    void doesNotSkip_standardWorkingCapitalBorrower() {
        LoanApplication app = LoanApplication.builder()
                .loanProduct(StandardLoanProduct.BUSINESS_WC_OD)
                .borrowerType(BorrowerType.INDIVIDUAL)
                .intakeSegment(IntakeSegment.BORROWER)
                .build();

        assertThat(guard.skipsLosTermLoanCreation(app)).isFalse();
    }
}
