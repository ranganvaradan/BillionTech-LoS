package com.los.core.requirement;

import com.los.core.exception.BusinessRuleException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Dependency-aware wave grouping for W6 parallel acquisition.
 * Fail-closed on cycles. No BPMN.
 */
public final class AcquisitionDependencyGraph {

    private AcquisitionDependencyGraph() {}

    public record Wave(int index, List<RequirementItemEntity> items) {}

    public record Schedule(List<Wave> waves, List<RequirementItemEntity> deferred) {}

    /**
     * @param candidates platform-executable items
     * @param readyParameterIds canonical ids (or item keys) already usable / READY_FOR_POLICY
     */
    public static Schedule schedule(List<RequirementItemEntity> candidates, Set<String> readyParameterIds) {
        Map<String, RequirementItemEntity> byKey = new LinkedHashMap<>();
        for (RequirementItemEntity item : candidates) {
            byKey.put(item.getItemKey(), item);
            if (item.getCanonicalParameterId() != null) {
                byKey.put(item.getCanonicalParameterId(), item);
            }
        }

        // Edges among candidates: itemId → dependency itemIds (only when dep is another candidate)
        Map<UUID, Set<UUID>> edges = new HashMap<>();
        Map<UUID, Set<String>> externalDeps = new HashMap<>();
        for (RequirementItemEntity item : candidates) {
            Set<UUID> edge = new HashSet<>();
            Set<String> external = new HashSet<>();
            for (String depKey : AcquisitionSourceResolver.dependencyParameterIds(item)) {
                if (readyParameterIds.contains(depKey)) {
                    continue;
                }
                RequirementItemEntity depItem = byKey.get(depKey);
                if (depItem != null && !depItem.getId().equals(item.getId())) {
                    edge.add(depItem.getId());
                } else {
                    external.add(depKey);
                }
            }
            edges.put(item.getId(), edge);
            externalDeps.put(item.getId(), external);
        }

        detectCycles(candidates, edges);

        Set<UUID> remaining = new HashSet<>();
        candidates.forEach(c -> remaining.add(c.getId()));
        Map<UUID, RequirementItemEntity> byId = new HashMap<>();
        candidates.forEach(c -> byId.put(c.getId(), c));

        List<Wave> waves = new ArrayList<>();
        Set<String> satisfied = new HashSet<>(readyParameterIds);

        while (!remaining.isEmpty()) {
            List<RequirementItemEntity> wave = new ArrayList<>();
            for (UUID id : List.copyOf(remaining)) {
                RequirementItemEntity item = byId.get(id);
                boolean blockedByCandidate = edges.getOrDefault(id, Set.of()).stream()
                        .anyMatch(remaining::contains);
                boolean blockedExternal = externalDeps.getOrDefault(id, Set.of()).stream()
                        .anyMatch(d -> !satisfied.contains(d));
                if (!blockedByCandidate && !blockedExternal) {
                    wave.add(item);
                }
            }
            if (wave.isEmpty()) {
                // Remaining are waiting on deps that won't complete in this schedule pass
                List<RequirementItemEntity> deferred = remaining.stream().map(byId::get).toList();
                return new Schedule(List.copyOf(waves), List.copyOf(deferred));
            }
            waves.add(new Wave(waves.size(), List.copyOf(wave)));
            for (RequirementItemEntity item : wave) {
                remaining.remove(item.getId());
                // Within-run: mark as satisfied for later waves only after they would execute.
                // For scheduling subsequent waves in the same pass we treat wave members as
                // progressing — derivations in a later wave can follow autos in an earlier wave.
                if (item.getCanonicalParameterId() != null) {
                    satisfied.add(item.getCanonicalParameterId());
                }
                satisfied.add(item.getItemKey());
            }
        }
        return new Schedule(List.copyOf(waves), List.of());
    }

    private static void detectCycles(List<RequirementItemEntity> items, Map<UUID, Set<UUID>> edges) {
        Set<UUID> visiting = new HashSet<>();
        Set<UUID> visited = new HashSet<>();
        Map<UUID, String> labels = new HashMap<>();
        items.forEach(i -> labels.put(i.getId(), i.getItemKey()));
        for (RequirementItemEntity item : items) {
            dfs(item.getId(), edges, labels, visiting, visited, new ArrayList<>());
        }
    }

    private static void dfs(UUID id, Map<UUID, Set<UUID>> edges, Map<UUID, String> labels,
                            Set<UUID> visiting, Set<UUID> visited, List<String> path) {
        if (visited.contains(id)) {
            return;
        }
        if (visiting.contains(id)) {
            throw new BusinessRuleException(
                    "Circular dependency in RequirementPlan acquisition graph: " + path + " → " + labels.get(id),
                    "ACQUISITION_DEPENDENCY_CYCLE", "acquisition-graph", null);
        }
        visiting.add(id);
        path.add(labels.getOrDefault(id, id.toString()));
        for (UUID dep : edges.getOrDefault(id, Set.of())) {
            dfs(dep, edges, labels, visiting, visited, path);
        }
        path.remove(path.size() - 1);
        visiting.remove(id);
        visited.add(id);
    }
}
