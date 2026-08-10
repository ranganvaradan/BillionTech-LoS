package com.los.core.service.kfs.edi;

import com.los.core.config.EdiKfsProperties;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.BorrowerType;
import com.los.lms.service.LmsApplicationConfigResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EdiKfsLoanDetectorTest {

    @Mock
    private LmsApplicationConfigResolver lmsApplicationConfigResolver;

    @Test
    void isEdiLoan_trueWhenTenureUnitDay() {
        EdiKfsProperties props = new EdiKfsProperties();
        props.setEnabled(true);
        EdiKfsLoanDetector detector = new EdiKfsLoanDetector(props, lmsApplicationConfigResolver);
        LoanApplication app = LoanApplication.builder()
                .applicationNumber("A")
                .customerId(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("P")
                .build();
        when(lmsApplicationConfigResolver.resolveTenureUnit(app)).thenReturn("Day");
        assertThat(detector.isEdiLoan(app)).isTrue();
    }

    @Test
    void isEdiLoan_falseWhenMonthTenure() {
        EdiKfsProperties props = new EdiKfsProperties();
        props.setEnabled(true);
        EdiKfsLoanDetector detector = new EdiKfsLoanDetector(props, lmsApplicationConfigResolver);
        LoanApplication app = LoanApplication.builder()
                .applicationNumber("A")
                .customerId(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("P")
                .build();
        when(lmsApplicationConfigResolver.resolveTenureUnit(app)).thenReturn("Month");
        assertThat(detector.isEdiLoan(app)).isFalse();
    }
}
