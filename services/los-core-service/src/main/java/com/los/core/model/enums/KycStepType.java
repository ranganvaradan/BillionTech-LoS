package com.los.core.model.enums;

public enum KycStepType {
    MOBILE_OTP,
    MNRL,
    EMAIL_OTP,
    AADHAAR_OTP,
    PAN_VERIFY,
    GSTIN_VERIFY,
    VOTER_ID_VERIFY,
    DL_VERIFY,
    BANK_PENNY_DROP,
    FACE_MATCH,
    LIVENESS,
    VIDEO_KYC,
    UDYAM_VERIFY,
    CIN_MCA21,
    AML_SCREENING,
    CKYC_DOWNLOAD,
    CKYC_UPLOAD,
    VEHICLE_RC_VERIFY,
    PROPERTY_EC_VERIFY,
    /**
     * Karza ITR return-forms pull. Credentials are collected on the borrower portal only;
     * staff Run KYC reuses the latest SUCCESS result and never re-sends a password.
     */
    ITR_RETURN_FORMS,
    /**
     * Karza GST PDF analysis (docs-upload-advance). Borrower uploads multiple GST return PDFs
     * + GSTIN + consent on the portal; upload runs at submit; admin generates the report.
     * Staff Run KYC reuses the latest result and never re-calls Karza.
     */
    GST_ANALYSIS,
    BUREAU_PULL,
    ESIGN_KFS,
    ESIGN_AGREEMENT
}
