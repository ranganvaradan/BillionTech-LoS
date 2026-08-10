package com.los.core.model.dto.request;

import java.util.UUID;

/**
 * Provisional identity fields to check for duplicate use before final submit.
 *
 * <p>When {@code asCoApplicant} is true, the application's primary {@code customerId} is not used
 * for "same borrower" allowance — co-applicant contacts must be unique across applications
 * (excluding the current application id when provided).
 */
public record ValidateIdentityRequest(
        UUID applicationId,
        String email,
        String mobile,
        String panNumber,
        String gstin,
        Boolean asCoApplicant
) {
    public ValidateIdentityRequest(
            UUID applicationId, String email, String mobile, String panNumber, String gstin) {
        this(applicationId, email, mobile, panNumber, gstin, null);
    }
}
