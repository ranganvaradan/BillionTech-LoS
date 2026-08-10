package com.los.core.model.catalog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StandardCatalogTest {

    @Test
    void combinationCountIs32() {
        assertThat(StandardCatalog.combinationCount()).isEqualTo(32);
    }

    @Test
    void segmentsCoverFourBorrowersByEightProducts() {
        long n = StandardCatalog.segments().count();
        assertThat(n).isEqualTo(32L);
    }

    @Test
    void loanProductCatalogSize() {
        assertThat(StandardLoanProduct.ALL).hasSize(8);
        assertThat(StandardLoanProduct.SECURED).hasSize(3);
    }
}
