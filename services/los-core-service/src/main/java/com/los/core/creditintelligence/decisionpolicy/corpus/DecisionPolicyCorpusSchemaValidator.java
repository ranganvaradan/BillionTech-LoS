package com.los.core.creditintelligence.decisionpolicy.corpus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Validates DP-V1 corpus application JSON. Rejects obvious raw PII; preserves missing values.
 * Does not coerce missing → zero.
 */
public final class DecisionPolicyCorpusSchemaValidator {

    public static final String SCHEMA_VERSION = "DP_V1_CORPUS_SCHEMA_V1";

    private static final Pattern PAN = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]$");
    private static final Pattern AADHAAR = Pattern.compile("^[0-9]{12}$");
    private static final Pattern PHONE = Pattern.compile("^[6-9][0-9]{9}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private DecisionPolicyCorpusSchemaValidator() {}

    public static Map<String, Object> validate(Map<String, Object> record) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        out.put("schemaVersion", SCHEMA_VERSION);

        if (record == null || record.isEmpty()) {
            errors.add("Record is empty");
            out.put("ok", false);
            out.put("errors", errors);
            return out;
        }

        require(record, "applicationToken", errors);
        require(record, "originClassification", errors);
        require(record, "productCode", errors);
        require(record, "evaluationBusinessDate", errors);

        DecisionPolicyCorpusOrigin origin = DecisionPolicyCorpusOrigin.parse(
                String.valueOf(record.get("originClassification")));
        out.put("originClassification", origin.name());
        out.put("countsTowardRealStored", origin.countsTowardRealStoredCertification());

        if (origin == DecisionPolicyCorpusOrigin.REPRESENTATIVE_FIXTURE
                || origin == DecisionPolicyCorpusOrigin.SYNTHETIC) {
            warnings.add("Origin " + origin + " does not count toward REAL_CORPUS_SHADOW_VALIDATED");
        }

        // Reject obvious raw PII in common fields
        rejectPii(record, "applicantName", errors);
        rejectPii(record, "fullName", errors);
        rejectPii(record, "pan", errors);
        rejectPii(record, "aadhaar", errors);
        rejectPii(record, "phone", errors);
        rejectPii(record, "email", errors);
        rejectPii(record, "address", errors);
        rejectPii(record, "bankAccountNumber", errors);

        Object appCore = record.get("applicationCore");
        if (appCore instanceof Map<?, ?> core) {
            core.forEach((k, v) -> {
                String key = String.valueOf(k).toLowerCase(Locale.ROOT);
                if (key.contains("pan") || key.contains("aadhaar") || key.contains("phone")
                        || key.contains("email") || key.contains("name") || key.contains("address")) {
                    if (looksLikeRawPii(v)) {
                        errors.add("Raw PII suspected in applicationCore." + k + " — anonymise before import");
                    }
                }
            });
        }

        // Missing values must remain absent — flag if zeros appear as fillers for known DI families
        Object metrics = record.get("metrics");
        if (metrics instanceof Map<?, ?> m) {
            for (String filler : List.of("bureau.score", "banking.avg_daily_balance_3m", "turnover.annual")) {
                if (m.containsKey(filler) && isZeroFiller(m.get(filler))
                        && Boolean.TRUE.equals(record.get("legacyDefaultSuspected"))) {
                    warnings.add("Possible silent zero filler for " + filler);
                }
            }
        }

        boolean usable = errors.isEmpty()
                && record.get("applicationToken") != null
                && record.get("productCode") != null;
        out.put("ok", errors.isEmpty());
        out.put("usable", usable && errors.isEmpty());
        out.put("errors", errors);
        out.put("warnings", warnings);
        out.put("allowCanonicalAuthority", false);
        return out;
    }

    private static void require(Map<String, Object> record, String key, List<String> errors) {
        if (record.get(key) == null || String.valueOf(record.get(key)).isBlank()) {
            errors.add("Missing required field: " + key);
        }
    }

    private static void rejectPii(Map<String, Object> record, String key, List<String> errors) {
        Object v = record.get(key);
        if (v == null) {
            return;
        }
        if (looksLikeRawPii(v)) {
            errors.add("Raw PII in field '" + key + "' — use stable token instead");
        }
    }

    static boolean looksLikeRawPii(Object v) {
        if (v == null) {
            return false;
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty() || s.startsWith("TOK_") || s.startsWith("ANON_") || s.startsWith("APP_")) {
            return false;
        }
        if (PAN.matcher(s.toUpperCase(Locale.ROOT)).matches()) {
            return true;
        }
        if (AADHAAR.matcher(s.replace(" ", "")).matches()) {
            return true;
        }
        if (PHONE.matcher(s.replaceAll("[\\s\\-]", "")).matches()) {
            return true;
        }
        if (EMAIL.matcher(s).matches()) {
            return true;
        }
        // free-text name heuristic: two+ alphabetic words without TOK_
        if (s.matches("(?i)^[a-z]+(\\s+[a-z]+){1,3}$") && s.length() > 5) {
            return true;
        }
        return false;
    }

    private static boolean isZeroFiller(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue() == 0.0;
        }
        return "0".equals(String.valueOf(v));
    }
}
