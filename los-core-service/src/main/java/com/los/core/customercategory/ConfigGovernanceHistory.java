package com.los.core.customercategory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Append-only governance history helpers for Category / Policy Set.
 */
final class ConfigGovernanceHistory {

    private ConfigGovernanceHistory() {}

    @SuppressWarnings("unchecked")
    static void append(Map<String, Object> gov, String event, CustomerCategoryDtos.Actor actor, String remarks) {
        if (gov == null) {
            return;
        }
        List<Map<String, Object>> history = (List<Map<String, Object>>) gov.computeIfAbsent(
                "history", k -> new ArrayList<>());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("event", event);
        row.put("at", Instant.now().toString());
        row.put("userId", actor == null ? null : actor.userId());
        row.put("displayName", actor == null ? null : actor.displayName());
        row.put("role", actor == null ? null : actor.role());
        if (remarks != null && !remarks.isBlank()) {
            row.put("remarks", remarks.trim());
        }
        history.add(row);
        gov.put("lastEvent", event);
        gov.put("lastEventAt", row.get("at"));
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> historyView(Map<String, Object> gov) {
        if (gov == null || !(gov.get("history") instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                out.add(new LinkedHashMap<>((Map<String, Object>) m));
            }
        }
        return out;
    }
}
