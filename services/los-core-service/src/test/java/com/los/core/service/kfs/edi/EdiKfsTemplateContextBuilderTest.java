package com.los.core.service.kfs.edi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.config.EdiKfsProperties;
import com.los.core.model.entity.KfsDocument;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.BorrowerType;
import com.los.lms.repository.LmsLoanHandoverRepository;
import com.los.lms.service.LmsApplicationConfigResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EdiKfsTemplateContextBuilderTest {

    @Mock
    private EdiKfsScheduleResolver scheduleResolver;
    @Mock
    private LmsLoanHandoverRepository handoverRepository;
    @Mock
    private LmsApplicationConfigResolver lmsApplicationConfigResolver;

  private final EdiKfsProperties properties = new EdiKfsProperties();

    @Test
    void build_populatesCorePlaceholdersFromKfsAndApplication() {
        when(lmsApplicationConfigResolver.resolveTenureUnit(any())).thenReturn("Day");
        when(handoverRepository.findByApplicationNumber(any())).thenReturn(java.util.Optional.empty());
        when(scheduleResolver.resolve(any(), any())).thenReturn(java.util.List.of());

        EdiKfsTemplateContextBuilder builder = new EdiKfsTemplateContextBuilder(
                scheduleResolver, handoverRepository, lmsApplicationConfigResolver, properties);

        LoanApplication app = LoanApplication.builder()
                .id(UUID.randomUUID())
                .applicationNumber("APP-EDI-001")
                .customerId(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("Personal")
                .lmsTenureUnit("Day")
                .personalInfo(Map.of("fullName", "Ravi Kumar", "city", "Coimbatore", "state", "Tamil Nadu"))
                .build();

        KfsDocument kfs = KfsDocument.builder()
                .applicationId(app.getId())
                .sanctionedAmount(new BigDecimal("50000"))
                .interestRate(new BigDecimal("18"))
                .apr(new BigDecimal("20.5"))
                .tenureMonths(90)
                .emiAmount(new BigDecimal("600"))
                .additionalTerms(Map.of())
                .build();

        EdiKfsTemplateContext ctx = builder.build(app, kfs);

        assertThat(ctx.fullName()).isEqualTo("Ravi Kumar");
        assertThat(ctx.tenor()).isEqualTo("90");
        assertThat(ctx.typeOfInstalment()).isEqualTo("Daily");
        assertThat(ctx.repaymentFrequency()).isEqualTo("Day");
        assertThat(ctx.appRefNo()).isEqualTo("APP-EDI-001");
        assertThat(ctx.loanAmountPlain()).isEqualTo("50000");
        assertThat(ctx.annualPercentage()).isEqualTo("20.50");
        assertThat(ctx.amountOfEachInstalmentRs()).isEqualTo("Rs.600");
    }
}
