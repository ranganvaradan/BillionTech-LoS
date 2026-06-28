package com.los.core.model.dto.request;

import java.util.UUID;

/**
 * Provisional identity fields to check for duplicate use before final submit.
 */
public record ValidateIdentityRequest(
        UUID applicationId,
        String email,
        String mobile,
        String panNumber,
        String gstin
) {
}
