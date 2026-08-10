package com.los.core.creditintelligence.validation;

import com.los.core.creditintelligence.validation.service.Embedded26AsValidator;
import com.los.core.creditintelligence.validation.service.ItrSemanticsValidator;
import com.los.core.creditintelligence.validation.service.TisSemanticsValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticsValidatorsTest {

    @Test
    void itrSemantics_surePassFixtureDistinct() {
        var report = new ItrSemanticsValidator()
                .validateSurePassFixture("provider-fixtures/surepass/itr/itr_income_heads_distinct.json");
        assertThat(report.distinct()).isTrue();
        assertThat(report.businessTurnover()).isNotNull();
        assertThat(report.totalIncome()).isNotNull();
        assertThat(report.businessTurnover()).isNotEqualByComparingTo(report.totalIncome());
    }

    @Test
    void embedded26As_lineageAndRecon() {
        var report = new Embedded26AsValidator()
                .validate("provider-fixtures/surepass/itr/itr_income_heads_distinct.json");
        assertThat(report.itrPresent()).isTrue();
        assertThat(report.form26AsPresent()).isTrue();
        assertThat(report.sharedLineage()).isTrue();
        assertThat(report.reconciliationCode()).isEqualTo("XSRC_ITR_26AS_TDS");
        assertThat(report.outcome()).isIn(
                "MATCH", "ACCEPTABLE_VARIANCE", "MATERIAL_VARIANCE", "CONFLICT", "DATA_INSUFFICIENT");
    }

    @Test
    void tisSemantics_threeAmountsDistinct() {
        var report = new TisSemanticsValidator()
                .validate("provider-fixtures/surepass/tis/tis_amounts_distinct.json");
        assertThat(report.distinct()).isTrue();
        assertThat(report.acceptedByTaxpayer())
                .isNotEqualByComparingTo(report.reportedBySource());
    }
}
