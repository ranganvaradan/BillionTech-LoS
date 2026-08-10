package com.los.core.creditintelligence.decisionpolicy.corpus;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Tokenises sensitive fields for DP-V1 corpus. Never commits raw PII. */
public final class DecisionPolicyCorpusAnonymizer {

    public static final String VERSION = "DP_V1_ANON_V1";

    private DecisionPolicyCorpusAnonymizer() {}

    public static String token(String family, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (raw.startsWith("TOK_") || raw.startsWith("ANON_")) {
            return raw;
        }
        UUID id = UUID.nameUUIDFromBytes((family + ":" + raw).getBytes(StandardCharsets.UTF_8));
        return "TOK_" + family.toUpperCase(Locale.ROOT) + "_" + id.toString().substring(0, 8);
    }

    public static Map<String, Object> anonymizeShallow(Map<String, Object> in) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (in == null) {
            return out;
        }
        in.forEach((k, v) -> {
            String key = k == null ? "" : k.toLowerCase(Locale.ROOT);
            if (v == null) {
                // preserve absence — do not insert zero
                return;
            }
            if (key.contains("pan") || key.contains("aadhaar") || key.contains("phone")
                    || key.contains("email") || key.contains("name") || key.contains("address")
                    || key.contains("account") || key.contains("cin")) {
                if (DecisionPolicyCorpusSchemaValidator.looksLikeRawPii(v)
                        || key.contains("pan") || key.contains("aadhaar") || key.contains("phone")
                        || key.contains("email") || key.contains("name") || key.contains("address")) {
                    out.put(k, token(key.replaceAll("[^a-z]", ""), String.valueOf(v)));
                    return;
                }
            }
            out.put(k, v);
        });
        out.put("anonymisationVersion", VERSION);
        return out;
    }
}
