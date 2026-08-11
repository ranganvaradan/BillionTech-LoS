package com.los.core.service.underwriting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.model.entity.UnderwritingScorecard;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/** Fingerprint of execution-affecting scorecard content for preview/approval freeze. */
public final class ScorecardContentFingerprint {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ScorecardContentFingerprint() {}

    public static String of(UnderwritingScorecard card) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scorecardJson", card.getScorecardJson());
        payload.put("thresholdsJson", card.getThresholdsJson());
        payload.put("hardRulesJson", card.getHardRulesJson());
        Map<String, Object> safety = card.getSafetyJson() == null ? Map.of() : card.getSafetyJson();
        payload.put("factorPolicies", safety.get("factorPolicies"));
        payload.put("borrowerType", card.getBorrowerType());
        payload.put("loanProduct", card.getLoanProduct());
        payload.put("priority", card.getPriority());
        payload.put("minAmount", card.getMinAmount());
        payload.put("maxAmount", card.getMaxAmount());
        payload.put("geography", card.getGeography());
        try {
            byte[] json = MAPPER.writeValueAsBytes(payload);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(json));
        } catch (Exception e) {
            return Integer.toHexString(payload.hashCode());
        }
    }

    public static boolean matches(UnderwritingScorecard card, String expected) {
        if (expected == null || expected.isBlank()) return false;
        return expected.equalsIgnoreCase(of(card));
    }
}
