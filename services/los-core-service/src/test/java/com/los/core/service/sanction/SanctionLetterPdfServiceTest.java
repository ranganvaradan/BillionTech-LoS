package com.los.core.service.sanction;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.SanctionRecord;
import com.los.core.model.enums.BorrowerType;
import com.los.core.service.kfs.KfsPdfGenerationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SanctionLetterPdfServiceTest {

    @Mock
    private KfsPdfGenerationService kfsPdfGenerationService;

    @InjectMocks
    private SanctionLetterPdfService svc;

    @Test
    void render_returnsNonEmptyPdf() {
        UUID appId = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
                .id(appId)
                .applicationNumber("T-1")
                .customerId(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("PERSONAL")
                .build();
        SanctionRecord r = SanctionRecord.builder()
                .id(UUID.randomUUID())
                .applicationId(appId)
                .approvedAmount(new BigDecimal("100000.00"))
                .approvedTenure(36)
                .interestRate(new BigDecimal("12.5"))
                .processingFee(new BigDecimal("2000.00"))
                .conditionsText("Standard conditions")
                .remarks("OK")
                .approvedBy("CM1")
                .build();

        byte[] pdf = svc.render(app, r);

        assertTrue(pdf != null && pdf.length > 100);
        verifyNoInteractions(kfsPdfGenerationService);
    }
}
