package com.los.core.creditintelligence.aiunderwriter.service;

import com.los.core.creditintelligence.aiunderwriter.domain.CiAiPromptTemplate;
import com.los.core.creditintelligence.aiunderwriter.store.AiUnderwritingStore;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AiPromptTemplateService {

    private final AiUnderwritingStore store;

    public AiPromptTemplateService() {
        this(new AiUnderwritingStore());
    }

    public AiPromptTemplateService(AiUnderwritingStore store) {
        this.store = store != null ? store : new AiUnderwritingStore();
    }

    public CiAiPromptTemplate require(String templateCode, String version) {
        Optional<CiAiPromptTemplate> found = version == null || version.isBlank()
                ? store.findLatestTemplate(templateCode)
                : store.findTemplate(templateCode, version);
        return found.orElseThrow(() -> new IllegalArgumentException(
                "Prompt template not found: " + templateCode + " v" + version));
    }

    public Map<String, Object> resolveVersions(List<String> requestedTypes) {
        Map<String, Object> versions = new LinkedHashMap<>();
        Map<String, String> typeToTemplate = Map.of(
                "NARRATIVE", "UNDERWRITING_SUMMARY_V1",
                "EXPLANATION", "POLICY_EXPLANATION_V1",
                "CAM_DRAFT", "CAM_DRAFT_V1",
                "CREDIT_NOTE_DRAFT", "CAM_DRAFT_V1",
                "QUESTION", "INVESTIGATION_QUESTIONS_V1",
                "ALTERNATE_STRUCTURE_SUGGESTION", "ALTERNATE_STRUCTURE_V1",
                "SCENARIO", "ALTERNATE_STRUCTURE_V1",
                "ANOMALY", "UNDERWRITING_SUMMARY_V1",
                "POLICY_CLARIFICATION_SUGGESTION", "POLICY_EXPLANATION_V1"
        );
        List<String> types = requestedTypes == null || requestedTypes.isEmpty()
                ? List.of("NARRATIVE", "EXPLANATION", "QUESTION")
                : requestedTypes;
        for (String type : types) {
            String code = typeToTemplate.getOrDefault(type, "UNDERWRITING_SUMMARY_V1");
            CiAiPromptTemplate t = require(code, "1");
            versions.put(type, Map.of(
                    "templateCode", t.getTemplateCode(),
                    "version", t.getVersion(),
                    "modelProvider", t.getModelProvider() == null ? "stub" : t.getModelProvider(),
                    "modelName", t.getModelName() == null ? "stub-grounded-v1" : t.getModelName()
            ));
        }
        return versions;
    }

    public List<CiAiPromptTemplate> listAll() {
        return store.listTemplates();
    }
}
