package com.los.core.creditintelligence.policystudio.runtime.canonicalconfig;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Exact calculation-authority pin for one participating parameter.
 * Replay must use this identity — never {@code latestFor()} at execution time.
 */
public record CanonicalCalculationPin(
        String parameterId,
        String calculationType,
        String authority,
        UUID calculationDefinitionId,
        Integer calculationDefinitionVersion,
        boolean replayable,
        boolean fallbackRequired
) {
    public static final String BUILT_IN_CODE = "BUILT_IN_CODE";
    public static final String AUTHORED_EXPRESSION = "AUTHORED_EXPRESSION";
    public static final String PROVIDER_DERIVED = "PROVIDER_DERIVED";
    public static final String RAW = "RAW";
    /** GACAT SOURCE_NOT_PROVEN — retained on the policy, not executable, no definition to pin. */
    public static final String SOURCE_NOT_PROVEN = "SOURCE_NOT_PROVEN";

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("parameterId", parameterId);
        m.put("calculationType", calculationType);
        m.put("authority", authority);
        m.put("calculationDefinitionId", calculationDefinitionId == null ? null : calculationDefinitionId.toString());
        m.put("calculationDefinitionVersion", calculationDefinitionVersion);
        m.put("replayable", replayable);
        m.put("fallbackRequired", fallbackRequired);
        return m;
    }

    public static CanonicalCalculationPin fromMap(Map<String, Object> m) {
        if (m == null) {
            return null;
        }
        UUID defId = null;
        Object rawId = m.get("calculationDefinitionId");
        if (rawId != null && !String.valueOf(rawId).isBlank()) {
            defId = UUID.fromString(String.valueOf(rawId));
        }
        Integer ver = null;
        Object rawVer = m.get("calculationDefinitionVersion");
        if (rawVer instanceof Number n) {
            ver = n.intValue();
        } else if (rawVer != null && !String.valueOf(rawVer).isBlank()) {
            ver = Integer.parseInt(String.valueOf(rawVer));
        }
        return new CanonicalCalculationPin(
                str(m.get("parameterId")),
                str(m.get("calculationType")),
                str(m.get("authority")),
                defId,
                ver,
                bool(m.get("replayable")),
                bool(m.get("fallbackRequired")));
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static boolean bool(Object o) {
        if (o instanceof Boolean b) {
            return b;
        }
        return o != null && Boolean.parseBoolean(String.valueOf(o));
    }
}
