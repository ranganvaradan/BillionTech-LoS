package com.los.core.service.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.model.entity.WorkflowConfig;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Content hash for Workflow definition mutation detection (W1).
 */
public final class WorkflowContentHash {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WorkflowContentHash() {}

    public static String of(WorkflowConfig cfg) {
        if (cfg == null) {
            return sha256("{}");
        }
        Map<String, Object> basis = new LinkedHashMap<>();
        basis.put("id", cfg.getId() != null ? cfg.getId().toString() : null);
        basis.put("version", cfg.getVersion());
        basis.put("steps", cfg.getSteps());
        basis.put("intakeConfig", cfg.getIntakeConfig());
        basis.put("parallelGroups", cfg.getParallelGroups());
        basis.put("vkycTriggerCondition", cfg.getVkycTriggerCondition());
        basis.put("bureauEnabled", cfg.isBureauEnabled());
        try {
            return sha256(MAPPER.writeValueAsString(basis));
        } catch (Exception e) {
            return sha256(String.valueOf(cfg.getId()) + "|" + cfg.getVersion());
        }
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
