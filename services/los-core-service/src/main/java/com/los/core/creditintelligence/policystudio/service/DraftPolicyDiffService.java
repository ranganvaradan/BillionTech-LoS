package com.los.core.creditintelligence.policystudio.service;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyDraftDiff;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyDraftPackage;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.support.ContentHasher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class DraftPolicyDiffService {

    private final PolicyStudioPersistenceService persistenceService;
    private final ContentHasher hasher = new ContentHasher();

    public DraftPolicyDiffService(PolicyStudioPersistenceService persistenceService) {
        this.persistenceService = persistenceService != null ? persistenceService : new PolicyStudioPersistenceService();
    }

    public DraftPolicyDiffService() {
        this(new PolicyStudioPersistenceService());
    }

    public Map<String, Object> diff(PolicyStudioSession session, CiPolicyDraftPackage from, CiPolicyDraftPackage to) {
        Map<String, Object> fromContent = from.getContent() == null ? Map.of() : from.getContent();
        Map<String, Object> toContent = to.getContent() == null ? Map.of() : to.getContent();

        List<Object> addedRules = new ArrayList<>();
        List<Object> removedRules = new ArrayList<>();
        List<Object> changedThresholds = new ArrayList<>();
        List<Object> changedScope = new ArrayList<>();
        List<Object> changedMetricMapping = new ArrayList<>();
        List<Object> changedAmbiguityResolution = new ArrayList<>();
        List<Object> changedExpectedTestOutcome = new ArrayList<>();

        Map<String, Map<String, Object>> fromRules = indexRules(fromContent.get("rules"));
        Map<String, Map<String, Object>> toRules = indexRules(toContent.get("rules"));
        for (String id : toRules.keySet()) {
            if (!fromRules.containsKey(id)) {
                addedRules.add(id);
            } else if (!Objects.equals(fromRules.get(id).get("expression"), toRules.get(id).get("expression"))) {
                changedThresholds.add(Map.of("rule", id, "from", fromRules.get(id).get("expression"),
                        "to", toRules.get(id).get("expression")));
            }
            if (fromRules.containsKey(id)
                    && !Objects.equals(fromRules.get(id).get("lineage"), toRules.get(id).get("lineage"))) {
                changedScope.add(id);
            }
        }
        for (String id : fromRules.keySet()) {
            if (!toRules.containsKey(id)) {
                removedRules.add(id);
            }
        }

        if (!Objects.equals(fromContent.get("metricDefinitions"), toContent.get("metricDefinitions"))) {
            changedMetricMapping.add("metricDefinitions");
        }
        if (!Objects.equals(fromContent.get("unresolvedAmbiguities"), toContent.get("unresolvedAmbiguities"))) {
            changedAmbiguityResolution.add(Map.of(
                    "from", fromContent.get("unresolvedAmbiguities"),
                    "to", toContent.get("unresolvedAmbiguities")));
        }
        if (!Objects.equals(fromContent.get("approvedTests"), toContent.get("approvedTests"))) {
            changedExpectedTestOutcome.add(Map.of(
                    "from", fromContent.get("approvedTests"),
                    "to", toContent.get("approvedTests")));
        }

        Map<String, Object> diffJson = new LinkedHashMap<>();
        diffJson.put("fromPackageId", from.getId().toString());
        diffJson.put("toPackageId", to.getId().toString());
        diffJson.put("fromVersion", from.getPackageVersion());
        diffJson.put("toVersion", to.getPackageVersion());
        diffJson.put("addedRule", addedRules);
        diffJson.put("removedRule", removedRules);
        diffJson.put("changedThreshold", changedThresholds);
        diffJson.put("changedScope", changedScope);
        diffJson.put("changedMetricMapping", changedMetricMapping);
        diffJson.put("changedAmbiguityResolution", changedAmbiguityResolution);
        diffJson.put("changedExpectedTestOutcome", changedExpectedTestOutcome);
        diffJson.put("contentHashFrom", from.getContentHash());
        diffJson.put("contentHashTo", to.getContentHash());
        diffJson.put("diffHash", hasher.hashMap(diffJson));

        CiPolicyDraftDiff entity = CiPolicyDraftDiff.builder()
                .id(UUID.randomUUID())
                .tenantId(session.getDocument().getTenantId())
                .fromPackageId(from.getId())
                .toPackageId(to.getId())
                .diffJson(diffJson)
                .build();
        persistenceService.saveDraftDiff(session, entity);
        return diffJson;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> indexRules(Object rulesObj) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        if (!(rulesObj instanceof List<?> list)) {
            return out;
        }
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                Object id = m.get("systemRuleId");
                if (id != null) {
                    out.put(String.valueOf(id), (Map<String, Object>) m);
                }
            }
        }
        return out;
    }
}
