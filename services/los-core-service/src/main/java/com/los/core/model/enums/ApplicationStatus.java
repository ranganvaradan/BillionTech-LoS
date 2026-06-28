package com.los.core.model.enums;

public enum ApplicationStatus {
    DRAFT,
    CONSENT_PENDING,
    KYC_IN_PROGRESS,
    KYC_FAILED,
    UNDERWRITING,
    /** @deprecated use {@link #CAM_READY} after post–credit-appraisal flow */
    APPROVED,
    REJECTED,
    /** @deprecated new flow issues {@link #KFS_GENERATED} */
    SANCTION_ISSUED,
    /** Credit approved; CAM generated / awaiting review */
    UNDERWRITING_COMPLETED,
    CAM_READY,
    CAM_REVIEWED,
    SANCTION_PENDING,
    SANCTIONED,
    KFS_GENERATED,
    ESIGN_PENDING,
    ESIGN_COMPLETED,
    /** eSign and KFS done; pre-disbursement gate */
    READY_FOR_DISBURSEMENT,
    DISBURSEMENT_PENDING,
    DISBURSED,
    WITHDRAWN,
    ON_HOLD
}
