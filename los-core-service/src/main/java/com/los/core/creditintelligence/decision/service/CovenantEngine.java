package com.los.core.creditintelligence.decision.service;

import com.los.core.creditintelligence.decision.domain.CiCovenantRecommendation;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CovenantEngine {

    public record CovenantResult(
            List<CiCovenantRecommendation> covenants,
            List<Object> covenantPayload,
            List<String> reasonCodes
    ) {}

    public CovenantResult compute(Map<String, Object> covenantStrategy, Map<String, Object> limitParams) {
        Map<String, Object> s = covenantStrategy == null ? Map.of() : covenantStrategy;
        @SuppressWarnings("unchecked")
        List<String> defaults = s.get("defaults") instanceof List<?> l
                ? l.stream().map(String::valueOf).toList()
                : List.of();

        List<CiCovenantRecommendation> covenants = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        for (String code : defaults) {
            Map<String, Object> threshold = thresholdFor(code, limitParams);
            covenants.add(CiCovenantRecommendation.builder()
                    .covenantCode(code)
                    .description(descriptionFor(code))
                    .thresholdJson(threshold)
                    .reason("DEFAULT_COVENANT")
                    .evidenceRefs(List.of(Map.of("kind", "STRATEGY", "reference", code)))
                    .build());
            reasons.add("COVENANT:" + code);
        }

        List<Object> payload = new ArrayList<>();
        for (CiCovenantRecommendation c : covenants) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", c.getCovenantCode());
            row.put("description", c.getDescription());
            row.put("threshold", c.getThresholdJson());
            row.put("reason", c.getReason());
            payload.add(row);
        }
        return new CovenantResult(covenants, payload, reasons);
    }

    private Map<String, Object> thresholdFor(String code, Map<String, Object> params) {
        Map<String, Object> t = new LinkedHashMap<>();
        if ("MIN_DSCR".equals(code)) {
            t.put("minDscr", params.getOrDefault("minDscr", 1.25));
        } else if ("GST_FILING_TIMELINESS".equals(code)) {
            t.put("maxFilingLagDays", 20);
        } else if ("MAX_LEVERAGE".equals(code)) {
            t.put("maxLeverage", params.getOrDefault("maxLeverage", 3.0));
        }
        return t;
    }

    private String descriptionFor(String code) {
        return switch (code) {
            case "MIN_DSCR" -> "Maintain minimum DSCR throughout tenure.";
            case "GST_FILING_TIMELINESS" -> "File GST returns within policy lag.";
            case "MAX_LEVERAGE" -> "Do not exceed maximum leverage.";
            case "MAX_DPD" -> "No material DPD beyond policy threshold.";
            default -> "Covenant: " + code;
        };
    }
}
