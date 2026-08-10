package com.los.core.service.loan;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.BorrowerType;
import com.los.core.model.enums.IntakeSegment;
import com.los.core.model.catalog.StandardLoanProduct;
import com.los.plp.model.entity.AnchorMaster;
import com.los.plp.model.entity.ProgramMaster;
import com.los.plp.model.entity.SubProgramMaster;
import com.los.plp.repository.AnchorMasterRepository;
import com.los.plp.repository.ProgramMasterRepository;
import com.los.plp.repository.SubProgramMasterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceDiscountingSanctionDefaultsServiceTest {

    @Mock
    private AnchorMasterRepository anchorMasterRepository;
    @Mock
    private SubProgramMasterRepository subProgramMasterRepository;
    @Mock
    private ProgramMasterRepository programMasterRepository;

    @InjectMocks
    private InvoiceDiscountingSanctionDefaultsService service;

    @Test
    void applyAnchorProgramDefaults_fillsLimitRateAndTenureFromPlpProgram() {
        UUID appId = UUID.randomUUID();
        UUID anchorId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
                .id(appId)
                .borrowerType(BorrowerType.COMPANY)
                .loanProduct(StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)
                .intakeSegment(IntakeSegment.ANCHOR)
                .build();
        AnchorMaster anchor = AnchorMaster.builder().id(anchorId).sourceAnchorApplicationId(appId).build();
        SubProgramMaster sub = SubProgramMaster.builder()
                .id(UUID.randomUUID())
                .anchorId(anchorId)
                .programId(programId)
                .createdAt(Instant.now())
                .build();
        ProgramMaster program = ProgramMaster.builder()
                .id(programId)
                .programLimit(new BigDecimal("600000"))
                .interestRate(new BigDecimal("12.5"))
                .tenureDays(360)
                .build();

        when(anchorMasterRepository.findBySourceAnchorApplicationId(appId)).thenReturn(Optional.of(anchor));
        when(subProgramMasterRepository.findByAnchorId(anchorId)).thenReturn(List.of(sub));
        when(programMasterRepository.findById(programId)).thenReturn(Optional.of(program));

        service.applyAnchorProgramDefaults(app);

        assertEquals(new BigDecimal("600000"), app.getSanctionedAmount());
        assertEquals(new BigDecimal("12.5"), app.getApprovedRate());
        assertEquals(12, app.getTenureMonths());
    }
}
