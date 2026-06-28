package com.los.core.service.borrower;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BorrowerDocumentVisibilityTest {

    @Test
    void hidesLenderOnlyUploadTypes() {
        assertThat(BorrowerDocumentVisibility.isVisibleUploadType("BUREAU_STATEMENT_EVIDENCE")).isFalse();
        assertThat(BorrowerDocumentVisibility.isVisibleUploadType("MANUAL_CREDIT_EVIDENCE")).isFalse();
        assertThat(BorrowerDocumentVisibility.isVisibleUploadType("PHYSICAL_KYC_EVIDENCE")).isFalse();
        assertThat(BorrowerDocumentVisibility.isVisibleUploadType("CAM_REPORT")).isFalse();
    }

    @Test
    void showsKycAndSignedUploadTypes() {
        assertThat(BorrowerDocumentVisibility.isVisibleUploadType("PAN_CARD")).isTrue();
        assertThat(BorrowerDocumentVisibility.isVisibleUploadType("BANK_STATEMENT")).isTrue();
        assertThat(BorrowerDocumentVisibility.isVisibleUploadType("OTHER_PARTNERSHIP_DEED")).isTrue();
        assertThat(BorrowerDocumentVisibility.isVisibleUploadType("SIGNED_AGREEMENT")).isTrue();
    }

    @Test
    void categorizesSignedUploads() {
        assertThat(BorrowerDocumentVisibility.categoryForUploadType("SIGNED_AGREEMENT")).isEqualTo("SIGNED");
        assertThat(BorrowerDocumentVisibility.categoryForUploadType("PAN_CARD")).isEqualTo("KYC");
    }
}
