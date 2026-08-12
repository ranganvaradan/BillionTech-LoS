package com.los.core.creditintelligence.policystudio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.los.core.creditintelligence.policystudio.parameters.PolicyResolutionIdentity;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * POLICY-RESOLUTION-PERSISTENCE-P0 — durable resolution overlays that survive
 * service restart. Authoritative for resolution identity bundles keyed by documentId
 * and demo-kind lineage index.
 */
@Component
public class PolicyStudioDurableResolutionStore {

    private static final Logger log = LoggerFactory.getLogger(PolicyStudioDurableResolutionStore.class);

    private final Path root;
    private final ObjectMapper mapper;

    public PolicyStudioDurableResolutionStore(
            @Value("${los.policy-studio.durable-dir:./data/policy-studio-resolutions}") String dir) {
        this.root = Path.of(dir);
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        try {
            Files.createDirectories(root);
        } catch (Exception e) {
            log.warn("policy-studio durable dir unavailable path={} reason={}", root, e.toString());
        }
    }

    public void saveFromSession(PolicyStudioSession session) {
        if (session == null || session.getDocument() == null || session.getDocument().getId() == null) {
            return;
        }
        UUID docId = session.getDocument().getId();
        Map<String, Object> bundle = PolicyResolutionIdentity.extractBundle(session);
        bundle.put("savedAt", Instant.now().toString());
        Map<String, Object> meta = session.getDocument().getMetadata();
        if (meta != null) {
            bundle.put("demoKind", meta.getOrDefault("kind", meta.get("demoKind")));
            bundle.put("policyName", session.getDocument().getName());
            bundle.put("documentVersion", session.getDocument().getDocumentVersion());
        }
        try {
            Files.createDirectories(root);
            Path file = root.resolve(docId + ".json");
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), bundle);
            // Index latest demo kind → documentId for resume
            Object kind = bundle.get("demoKind");
            if (kind != null && !String.valueOf(kind).isBlank()) {
                Path idx = root.resolve("demo-" + kind + ".latest");
                Files.writeString(idx, docId.toString());
            }
        } catch (Exception e) {
            log.warn("durable resolution save failed docId={} reason={}", docId, e.toString());
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> loadBundle(UUID documentId) {
        if (documentId == null) return Map.of();
        Path file = root.resolve(documentId + ".json");
        if (!Files.isRegularFile(file)) return Map.of();
        try {
            return mapper.readValue(file.toFile(), Map.class);
        } catch (Exception e) {
            log.warn("durable resolution load failed docId={} reason={}", documentId, e.toString());
            return Map.of();
        }
    }

    public UUID latestDemoDocumentId(String kind) {
        if (kind == null || kind.isBlank()) return null;
        Path idx = root.resolve("demo-" + kind.trim().toLowerCase() + ".latest");
        if (!Files.isRegularFile(idx)) return null;
        try {
            String raw = Files.readString(idx).trim();
            return raw.isEmpty() ? null : UUID.fromString(raw);
        } catch (Exception e) {
            return null;
        }
    }

    public void clearDemoLatest(String kind) {
        if (kind == null) return;
        try {
            Files.deleteIfExists(root.resolve("demo-" + kind.trim().toLowerCase() + ".latest"));
        } catch (Exception ignored) {
        }
    }

    public List<UUID> listDocumentIds() {
        List<UUID> out = new ArrayList<>();
        if (!Files.isDirectory(root)) return out;
        try (Stream<Path> s = Files.list(root)) {
            s.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .forEach(p -> {
                        String n = p.getFileName().toString().replace(".json", "");
                        try {
                            out.add(UUID.fromString(n));
                        } catch (Exception ignored) {
                        }
                    });
        } catch (Exception ignored) {
        }
        return out;
    }

    public Path root() {
        return root;
    }
}
