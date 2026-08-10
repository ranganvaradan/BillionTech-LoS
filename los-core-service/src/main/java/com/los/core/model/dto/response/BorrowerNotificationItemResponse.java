package com.los.core.model.dto.response;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class BorrowerNotificationItemResponse {
    UUID id;
    String title;
    String message;
    String kind;
    Instant createdAt;
    UUID applicationId;
}
