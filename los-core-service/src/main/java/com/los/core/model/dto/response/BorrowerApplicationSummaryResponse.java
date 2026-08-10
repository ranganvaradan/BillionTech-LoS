package com.los.core.model.dto.response;

import com.los.core.model.enums.ApplicationStatus;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class BorrowerApplicationSummaryResponse {
    UUID applicationId;
    String applicationNumber;
    String product;
    String friendlyStatus;
    ApplicationStatus status;
    Instant createdAt;
    Instant updatedAt;
}
