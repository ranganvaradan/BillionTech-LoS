package com.los.core.creditintelligence.policystudio.certification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Append-oriented in-memory certification ledger (unit tests + non-Spring callers).
 * Durable store: {@code ci_production_certification} / V142.
 */
public final class InMemoryProductionCertificationLedger {

    public record Event(
            UUID eventId,
            UUID certificationId,
            String eventType,
            CertificationStatus fromStatus,
            CertificationStatus toStatus,
            String actor,
            String reason,
            Map<String, Object> evidenceJson,
            Instant occurredAt
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("eventId", eventId.toString());
            m.put("certificationId", certificationId.toString());
            m.put("eventType", eventType);
            m.put("fromStatus", fromStatus == null ? null : fromStatus.name());
            m.put("toStatus", toStatus.name());
            m.put("actor", actor);
            m.put("reason", reason);
            m.put("evidenceJson", evidenceJson == null ? Map.of() : evidenceJson);
            m.put("occurredAt", occurredAt.toString());
            return m;
        }
    }

    private final ConcurrentHashMap<String, CertificationRecord> byKey = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CertificationRecord> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<Event>> events = new ConcurrentHashMap<>();

    public static String key(
            CertifiableArtifactType type, String artifactId, String version,
            CertificationScopeType scopeType, String scopeId) {
        return type.name() + "|" + nullToEmpty(artifactId) + "|" + nullToEmpty(version)
                + "|" + scopeType.name() + "|" + nullToEmpty(scopeId);
    }

    public Optional<CertificationRecord> find(
            CertifiableArtifactType type, String artifactId, String version,
            CertificationScopeType scopeType, String scopeId) {
        return Optional.ofNullable(byKey.get(key(type, artifactId, version, scopeType, scopeId)));
    }

    public Optional<CertificationRecord> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<Event> eventsFor(UUID certificationId) {
        CopyOnWriteArrayList<Event> list = events.get(certificationId);
        return list == null ? List.of() : List.copyOf(list);
    }

    public synchronized CertificationRecord upsert(CertificationRecord record, Event event) {
        Objects.requireNonNull(record, "record");
        String k = key(record.artifactType(), record.artifactId(), record.artifactVersion(),
                record.scopeType(), record.scopeId());
        byKey.put(k, record);
        byId.put(record.certificationId(), record);
        if (event != null) {
            events.computeIfAbsent(record.certificationId(), id -> new CopyOnWriteArrayList<>()).add(event);
        }
        return record;
    }

    public List<CertificationRecord> all() {
        return new ArrayList<>(byId.values());
    }

    public void clear() {
        byKey.clear();
        byId.clear();
        events.clear();
    }

    public long countByTypeAndStatus(CertifiableArtifactType type, CertificationStatus status) {
        return byId.values().stream()
                .filter(r -> r.artifactType() == type && r.status() == status)
                .count();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
