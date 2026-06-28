package com.los.core.service.borrower;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceDiscountingFlowRulesTest {

    @Test
    void purchaseFlow_eligibleIsAcceptableNotFinanceable() {
        assertThat(InvoiceDiscountingFlowRules.acceptable("ELIGIBLE", "PURCHASE_BILL_DISCOUNTING")).isTrue();
        assertThat(InvoiceDiscountingFlowRules.financeable("ELIGIBLE", "PURCHASE_BILL_DISCOUNTING")).isFalse();
    }

    @Test
    void purchaseFlow_borrowerAcceptedIsFinanceable() {
        assertThat(InvoiceDiscountingFlowRules.acceptable("BORROWER_ACCEPTED", "PURCHASE_BILL_DISCOUNTING")).isFalse();
        assertThat(InvoiceDiscountingFlowRules.financeable("BORROWER_ACCEPTED", "PURCHASE_BILL_DISCOUNTING")).isTrue();
    }

    @Test
    void salesFlow_eligibleIsFinanceableWithoutAccept() {
        assertThat(InvoiceDiscountingFlowRules.acceptable("ELIGIBLE", "SALES_BILL_DISCOUNTING")).isFalse();
        assertThat(InvoiceDiscountingFlowRules.financeable("ELIGIBLE", "SALES_BILL_DISCOUNTING")).isTrue();
    }

    @Test
    void purchaseOrderFlow_eligibleIsFinanceableWithoutAccept() {
        assertThat(InvoiceDiscountingFlowRules.acceptable("ELIGIBLE", "PURCHASE_ORDER_DISCOUNTING")).isFalse();
        assertThat(InvoiceDiscountingFlowRules.financeable("ELIGIBLE", "PURCHASE_ORDER_DISCOUNTING")).isTrue();
    }

    @Test
    void sellerInitiatedFlow_partiallyDiscountedIsFinanceable() {
        assertThat(InvoiceDiscountingFlowRules.financeable("PARTIALLY_DISCOUNTED", "PURCHASE_ORDER_DISCOUNTING")).isTrue();
    }
}
