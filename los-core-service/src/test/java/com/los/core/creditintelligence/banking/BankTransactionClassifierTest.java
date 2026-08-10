package com.los.core.creditintelligence.banking;

import com.los.core.creditintelligence.banking.domain.TxnCategory;
import com.los.core.creditintelligence.banking.domain.TxnDirection;
import com.los.core.creditintelligence.banking.domain.TxnMode;
import com.los.core.creditintelligence.banking.service.BankTransactionClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BankTransactionClassifierTest {

    private BankTransactionClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new BankTransactionClassifier();
    }

    @Test
    void classifiesEmiNachDebits() {
        var r = classifier.classify("NACH DEBIT HDFC BANK EMI", TxnDirection.DEBIT, TxnMode.NACH);
        assertThat(r.category()).isEqualTo(TxnCategory.EMI);
        assertThat(r.emiFlag()).isTrue();
        assertThat(r.lenderFlag()).isTrue();
    }

    @Test
    void classifiesChequeReturn() {
        var r = classifier.classify("CHEQUE RETURN INSUFFICIENT FUNDS", TxnDirection.DEBIT, TxnMode.CHEQUE);
        assertThat(r.category()).isEqualTo(TxnCategory.CHEQUE_RETURN);
        assertThat(r.bounceFlag()).isTrue();
    }

    @Test
    void goodsReturnIsNotChequeBounce() {
        var r = classifier.classify("GOODS RETURN FROM CUSTOMER", TxnDirection.DEBIT, TxnMode.NEFT);
        assertThat(r.category()).isNotEqualTo(TxnCategory.CHEQUE_RETURN);
        assertThat(r.bounceFlag()).isFalse();
    }

    @Test
    void classifiesCashDepositSelfTransferLoanDisbursementGstInterestCharge() {
        assertThat(classifier.classify("CASH DEPOSIT BY CASH", TxnDirection.CREDIT, TxnMode.CASH).category())
                .isEqualTo(TxnCategory.CASH_DEPOSIT);
        assertThat(classifier.classify("NEFT SELF TRANSFER OWN A/C", TxnDirection.DEBIT, TxnMode.NEFT).category())
                .isEqualTo(TxnCategory.SELF_TRANSFER);
        assertThat(classifier.classify("LOAN DISBURSEMENT TERM LOAN CREDIT", TxnDirection.CREDIT, TxnMode.NEFT).category())
                .isEqualTo(TxnCategory.LOAN_DISBURSEMENT);
        assertThat(classifier.classify("GST PAYMENT CGST", TxnDirection.DEBIT, TxnMode.NEFT).category())
                .isEqualTo(TxnCategory.GST_PAYMENT);
        assertThat(classifier.classify("INTEREST CREDIT SB INT", TxnDirection.CREDIT, TxnMode.INTEREST).category())
                .isEqualTo(TxnCategory.INTEREST_CREDIT);
        assertThat(classifier.classify("BANK CHARGE SMS CHARGE", TxnDirection.DEBIT, TxnMode.BANK_CHARGE).category())
                .isEqualTo(TxnCategory.BANK_CHARGE);
    }

    @Test
    void unknownStaysUnknown() {
        var r = classifier.classify("RANDOM XYZ 123", TxnDirection.CREDIT, TxnMode.UNKNOWN);
        assertThat(r.category()).isEqualTo(TxnCategory.UNKNOWN);
    }
}
