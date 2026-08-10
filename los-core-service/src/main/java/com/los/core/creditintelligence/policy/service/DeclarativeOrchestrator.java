package com.los.core.creditintelligence.policy.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Executes stageDefinitions from package content JSON.
 * Default stages 1–14; P1 packages may include a subset.
 */
@Component
public class DeclarativeOrchestrator {

    public static final String ORCHESTRATION_VERSION = "ORCHESTRATION_V1";

    public static final List<String> DEFAULT_STAGE_CODES = List.of(
            "DATA_READINESS",
            "IDENTITY_KYC",
            "FRAUD_SANCTIONS",
            "BUREAU",
            "BANKING",
            "TAX",
            "FINANCIAL",
            "OBLIGATIONS",
            "WORKING_CAPITAL",
            "COLLATERAL",
            "SCORECARD",
            "ELIGIBILITY",
            "LIMIT",
            "PRICING"
    );

    public static final Set<String> P1_COMMON_STAGES = Set.of(
            "DATA_READINESS", "BUREAU", "BANKING", "RECONCILIATION",
            "SCORECARD", "ELIGIBILITY", "RECOMMENDATION"
    );

    public record StageDefinition(
            String stageCode,
            int sequence,
            List<String> dependsOn,
            List<String> rules,
            boolean continueOnFail,
            boolean continueOnRefer,
            boolean parallelizable,
            boolean required
    ) {}

    @SuppressWarnings("unchecked")
    public List<StageDefinition> resolveStages(Map<String, Object> packageContent) {
        Object raw = packageContent == null ? null : packageContent.get("stageDefinitions");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return defaultStageDefs(packageContent);
        }
        List<StageDefinition> out = new ArrayList<>();
        int i = 0;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) {
                continue;
            }
            Map<String, Object> sm = (Map<String, Object>) m;
            String code = String.valueOf(sm.getOrDefault("stageCode", "STAGE_" + i));
            int seq = sm.get("sequence") instanceof Number n ? n.intValue() : i + 1;
            List<String> depends = toStrList(sm.get("dependsOn"));
            List<String> rules = toStrList(sm.get("rules"));
            boolean continueOnFail = sm.get("continueOnFail") == null || Boolean.TRUE.equals(sm.get("continueOnFail"));
            boolean continueOnRefer = sm.get("continueOnRefer") == null || Boolean.TRUE.equals(sm.get("continueOnRefer"));
            boolean parallel = Boolean.TRUE.equals(sm.get("parallelizable"));
            boolean required = sm.get("required") == null || Boolean.TRUE.equals(sm.get("required"));
            out.add(new StageDefinition(code, seq, depends, rules, continueOnFail, continueOnRefer, parallel, required));
            i++;
        }
        out.sort(Comparator.comparingInt(StageDefinition::sequence));
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<StageDefinition> defaultStageDefs(Map<String, Object> packageContent) {
        Map<String, List<String>> rulesByStage = new LinkedHashMap<>();
        if (packageContent != null && packageContent.get("rules") instanceof List<?> rules) {
            for (Object r : rules) {
                if (!(r instanceof Map<?, ?> rawRule)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> rm = (Map<String, Object>) rawRule;
                String stage = String.valueOf(rm.getOrDefault("stageCode", "ELIGIBILITY"));
                String id = String.valueOf(rm.getOrDefault("ruleId", rm.get("id")));
                rulesByStage.computeIfAbsent(stage, k -> new ArrayList<>()).add(id);
            }
        }
        List<StageDefinition> out = new ArrayList<>();
        int seq = 1;
        for (String code : DEFAULT_STAGE_CODES) {
            List<String> rules = rulesByStage.getOrDefault(code, List.of());
            if (rules.isEmpty() && packageContent != null && packageContent.containsKey("rules")) {
                // skip empty default stages when package defines rules with stage codes
                if (rulesByStage.keySet().stream().anyMatch(P1_COMMON_STAGES::contains)
                        || !rulesByStage.isEmpty()) {
                    if (!rulesByStage.containsKey(code)) {
                        continue;
                    }
                }
            }
            out.add(new StageDefinition(
                    code, seq++, List.of(), rules,
                    true, true, false, P1_COMMON_STAGES.contains(code)));
        }
        // Include RECONCILIATION / RECOMMENDATION if rules present
        for (String extra : List.of("RECONCILIATION", "RECOMMENDATION")) {
            if (rulesByStage.containsKey(extra)) {
                out.add(new StageDefinition(
                        extra, seq++, List.of(), rulesByStage.get(extra),
                        true, true, false, true));
            }
        }
        out.sort(Comparator.comparingInt(StageDefinition::sequence));
        return out;
    }

    public boolean shouldContinue(StageDefinition stage, String stageOutcome) {
        if ("FAIL".equals(stageOutcome) && !stage.continueOnFail()) {
            return false;
        }
        if ("REFER".equals(stageOutcome) && !stage.continueOnRefer()) {
            return false;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private List<String> toStrList(Object o) {
        if (o == null) {
            return List.of();
        }
        if (o instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                out.add(String.valueOf(item));
            }
            return out;
        }
        return List.of(String.valueOf(o));
    }
}
