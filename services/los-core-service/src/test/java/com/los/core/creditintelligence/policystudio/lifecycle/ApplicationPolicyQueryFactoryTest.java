package com.los.core.creditintelligence.policystudio.lifecycle;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.BorrowerType;
import com.los.core.model.enums.IntakeSegment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationPolicyQueryFactoryTest {

    @Test
    void mapsSupportedLoanApplicationFieldsOnly() {
        LoanApplication app = LoanApplication.builder()
                .id(UUID.randomUUID())
                .applicationNumber("LA-100")
                .loanProduct("DIGILEAP")
                .requestedAmount(new BigDecimal("700000"))
                .borrowerType(BorrowerType.INDIVIDUAL)
                .intakeSegment(IntakeSegment.BORROWER)
                .submittedAt(Instant.parse("2026-09-03T10:00:00Z"))
                .build();

        ApplicationPolicyQuery q = ApplicationPolicyQueryFactory.fromLoanApplication(app, null);
        assertThat(q.productCode()).isEqualTo("DIGILEAP");
        assertThat(q.loanAmount()).isEqualByComparingTo("700000");
        assertThat(q.borrowerType()).isEqualTo("INDIVIDUAL");
        assertThat(q.customerSegment()).isEqualTo("BORROWER");
        assertThat(q.facilityType()).isNull();
        assertThat(q.securedUnsecured()).isNull();
        assertThat(q.evaluationDate()).isEqualTo(LocalDate.of(2026, 9, 3));
    }

    @Test
    void explicitEvaluationAsOfWins() {
        LoanApplication app = LoanApplication.builder()
                .id(UUID.randomUUID())
                .loanProduct("DIGILEAP")
                .submittedAt(Instant.parse("2026-08-01T10:00:00Z"))
                .build();
        LocalDate asOf = LocalDate.of(2026, 9, 3);
        assertThat(ApplicationPolicyQueryFactory.resolveEvaluationBusinessDate(app, asOf)).isEqualTo(asOf);
    }
}
