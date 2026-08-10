package com.los.core.model.dto.response;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Unified application timeline entry (status, domain action, or process step).
 */
public record ApplicationTimelineEntryView(
        String id,
        Instant occurredAt,
        Instant completedAt,
        String category,
        String title,
        String actor,
        String result,
        String summary,
        Map<String, Object> details
) {
    public static ApplicationTimelineEntryView of(
            UUID id,
            Instant occurredAt,
            Instant completedAt,
            String category,
            String title,
            String actor,
            String result,
            String summary,
            Map<String, Object> details) {
        return new ApplicationTimelineEntryView(
                id == null ? null : id.toString(),
                occurredAt,
                completedAt,
                category,
                title,
                actor,
                result,
                summary,
                details == null ? Map.of() : details);
    }
}
