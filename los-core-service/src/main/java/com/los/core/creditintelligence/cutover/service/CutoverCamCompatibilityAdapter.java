package com.los.core.creditintelligence.cutover.service;

import com.los.core.creditintelligence.decision.domain.CiCreditRecommendation;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps recommendation fields to a CAM-shaped read model. No CAM write.
 */
@Service
public class CutoverCamCompatibilityAdapter {

    public Map<String, Object> toCamReadModel(CiCreditRecommendation recommendation) {
        Map<String, Object> cam = new LinkedHashMap<>();
        if (recommendation == null) {
            cam.put("available", false);
            return cam;
        }
        cam.put("available", true);
        cam.put("readOnly", true);
        cam.put("writesCam", false);
        cam.put("amount", recommendation.getRecommendedAmount());
        cam.put("tenure", recommendation.getRecommendedTenureMonths());
        cam.put("pricing", recommendation.getRecommendedFinalRate());
        cam.put("conditions", recommendation.getConditionsPrecedent() == null
                ? List.of() : recommendation.getConditionsPrecedent());
        cam.put("reasonCodes", recommendation.getReasonCodes() == null
                ? List.of() : recommendation.getReasonCodes());
        cam.put("authorityLevel", recommendation.getApprovalAuthorityLevel());
        cam.put("outcome", recommendation.getRecommendationOutcome());
        cam.put("source", "CANONICAL_SHADOW_RECOMMENDATION");
        cam.put("authoritative", false);
        return cam;
    }

    public Map<String, Object> toCamReadModel(Map<String, Object> recommendationFields) {
        Map<String, Object> cam = new LinkedHashMap<>();
        if (recommendationFields == null || recommendationFields.isEmpty()) {
            cam.put("available", false);
            return cam;
        }
        cam.put("available", true);
        cam.put("readOnly", true);
        cam.put("writesCam", false);
        cam.put("amount", recommendationFields.get("amount"));
        cam.put("tenure", recommendationFields.get("tenure"));
        cam.put("pricing", recommendationFields.get("pricing"));
        cam.put("conditions", recommendationFields.getOrDefault("conditions", List.of()));
        cam.put("reasonCodes", recommendationFields.getOrDefault("reasonCodes", List.of()));
        cam.put("authorityLevel", recommendationFields.get("authority"));
        cam.put("outcome", recommendationFields.get("outcome"));
        cam.put("source", "DUAL_RUN_SNAPSHOT");
        cam.put("authoritative", false);
        return cam;
    }
}
