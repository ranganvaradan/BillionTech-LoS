package com.los.core.model.dto.response;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class BorrowerTimelineStepResponse {
    String id;
    String label;
    /** completed | in_progress | pending | locked */
    String state;
    String description;
    Instant completedAt;
}
