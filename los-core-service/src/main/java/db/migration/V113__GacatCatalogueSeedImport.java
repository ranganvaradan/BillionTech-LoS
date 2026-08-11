package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GACAT-PERSISTENCE-1 — one-time import of the current Java catalogue inventory into DB.
 * Uses reflection so Flyway classpath does not hard-couple migration compile order;
 * source of import data is {@code GacatCatalogueSeed} at migrate time only.
 * Runtime production reads never call this class.
 */
public class V113__GacatCatalogueSeedImport extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();
        try (PreparedStatement count = conn.prepareStatement(
                "SELECT COUNT(*) FROM ci_gacat_canonical_parameter")) {
            try (ResultSet rs = count.executeQuery()) {
                rs.next();
                if (rs.getInt(1) > 0) {
                    return; // already seeded
                }
            }
        }

        Class<?> seedClass = Class.forName(
                "com.los.core.creditintelligence.policystudio.parameters.GacatCatalogueSeed");
        Method all = seedClass.getDeclaredMethod("all");
        all.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<?> defs = (List<?>) all.invoke(null);

        Class<?> authoring = Class.forName(
                "com.los.core.creditintelligence.policystudio.parameters.AuthoringValueTypes");
        Method allowedValues = authoring.getMethod("allowedValues", String.class);

        Timestamp now = Timestamp.from(Instant.now());
        Map<String, UUID> idByCanonical = new LinkedHashMap<>();

        String insertParam = """
                INSERT INTO ci_gacat_canonical_parameter (
                  id, canonical_id, display_name, description, source_family, parameter_kind,
                  value_type, unit, period, availability, status,
                  source_available, normalized, derivation_defined, implemented, production_ready,
                  manual_input_allowed, active, live_rule_parameter, live_scorecard_parameter,
                  capability_schema, cardinality, created_at, updated_at
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """;
        String insertVersion = """
                INSERT INTO ci_gacat_canonical_parameter_version (
                  id, parameter_id, version_no, effective_from, effective_to, definition_status,
                  calculation_definition, missing_data_treatment, period_definition,
                  implementation_binding, filters_eligibility, transformation, aggregation,
                  metadata_json, created_at, created_by
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """;
        String insertAlias = """
                INSERT INTO ci_gacat_canonical_parameter_alias (id, parameter_id, alias, alias_type, active)
                VALUES (?,?,?,?,TRUE)
                """;
        String insertAllowed = """
                INSERT INTO ci_gacat_canonical_parameter_allowed_value
                  (id, parameter_id, parameter_version_id, value_code, display_value, sort_order, active)
                VALUES (?,?,?,?,?,?,TRUE)
                """;
        String insertLineage = """
                INSERT INTO ci_gacat_canonical_parameter_lineage
                  (id, derived_parameter_id, input_parameter_id, input_ref, relationship_type,
                   sequence_no, required, metadata_json)
                VALUES (?,?,?,?,?,?,TRUE,NULL)
                """;
        String insertBinding = """
                INSERT INTO ci_gacat_canonical_parameter_source_binding
                  (id, parameter_id, source_family, provider_code, provider_field_path,
                   normalizer_binding, active, metadata_json)
                VALUES (?,?,?,?,?,?,TRUE,NULL)
                """;

        try (PreparedStatement psParam = conn.prepareStatement(insertParam);
             PreparedStatement psVer = conn.prepareStatement(insertVersion);
             PreparedStatement psAlias = conn.prepareStatement(insertAlias);
             PreparedStatement psAllowed = conn.prepareStatement(insertAllowed);
             PreparedStatement psLin = conn.prepareStatement(insertLineage);
             PreparedStatement psBind = conn.prepareStatement(insertBinding)) {

            for (Object defObj : defs) {
                Class<?> defClass = defObj.getClass();
                String canonicalId = str(invoke(defObj, "id"));
                String displayName = str(invoke(defObj, "businessName"));
                String source = str(invoke(defObj, "evaluatedFrom"));
                String kind = str(invoke(defObj, "type"));
                String unit = str(invoke(defObj, "unit"));
                String period = str(invoke(defObj, "period"));
                String availability = str(invoke(defObj, "availability"));
                String calc = str(invoke(defObj, "calculationSummary"));
                String binding = str(invoke(defObj, "existingImplementationBinding"));
                String liveRule = str(invoke(defObj, "liveRuleParameter"));
                String liveScore = str(invoke(defObj, "liveScorecardParameter"));
                @SuppressWarnings("unchecked")
                List<String> aliases = (List<String>) invoke(defObj, "aliases");
                @SuppressWarnings("unchecked")
                List<String> primitives = (List<String>) invoke(defObj, "requiredPrimitives");
                Object cap = invoke(defObj, "capability");

                boolean sourceAvailable = bool(invoke(cap, "sourceAvailable"));
                boolean normalized = bool(invoke(cap, "normalized"));
                boolean derivationDefined = bool(invoke(cap, "derivationDefined"));
                boolean implemented = bool(invoke(cap, "implemented"));
                boolean productionReady = bool(invoke(cap, "productionReady"));
                String schema = str(invoke(cap, "schema"));
                String cardinality = str(invoke(cap, "cardinality"));
                String providerPath = str(invoke(cap, "providerFieldPath"));
                String missing = str(invoke(cap, "missingDataTreatment"));
                String filters = str(invoke(cap, "filtersEligibility"));
                String transformation = str(invoke(cap, "transformation"));
                String aggregation = str(invoke(cap, "aggregation"));

                UUID paramId = UUID.randomUUID();
                UUID versionId = UUID.randomUUID();
                idByCanonical.put(canonicalId, paramId);

                boolean manual = "MANUAL".equalsIgnoreCase(kind);

                int i = 1;
                psParam.setObject(i++, paramId);
                psParam.setString(i++, canonicalId);
                psParam.setString(i++, displayName);
                psParam.setString(i++, calc);
                psParam.setString(i++, source == null ? "UNKNOWN" : source);
                psParam.setString(i++, kind);
                psParam.setString(i++, unit);
                psParam.setString(i++, unit);
                psParam.setString(i++, period);
                psParam.setString(i++, availability);
                psParam.setString(i++, "ACTIVE");
                psParam.setBoolean(i++, sourceAvailable);
                psParam.setBoolean(i++, normalized);
                psParam.setBoolean(i++, derivationDefined);
                psParam.setBoolean(i++, implemented);
                psParam.setBoolean(i++, productionReady);
                psParam.setBoolean(i++, manual);
                psParam.setBoolean(i++, true);
                psParam.setString(i++, liveRule);
                psParam.setString(i++, liveScore);
                psParam.setString(i++, schema);
                psParam.setString(i++, cardinality);
                psParam.setTimestamp(i++, now);
                psParam.setTimestamp(i, now);
                psParam.addBatch();

                i = 1;
                psVer.setObject(i++, versionId);
                psVer.setObject(i++, paramId);
                psVer.setInt(i++, 1);
                psVer.setTimestamp(i++, now);
                psVer.setTimestamp(i++, null);
                psVer.setString(i++, "ACTIVE");
                psVer.setString(i++, calc);
                psVer.setString(i++, missing);
                psVer.setString(i++, period);
                psVer.setString(i++, binding);
                psVer.setString(i++, filters);
                psVer.setString(i++, transformation);
                psVer.setString(i++, aggregation);
                psVer.setString(i++, null);
                psVer.setTimestamp(i++, now);
                psVer.setString(i, "GACAT-PERSISTENCE-1");
                psVer.addBatch();

                if (aliases != null) {
                    for (String a : aliases) {
                        if (a == null || a.isBlank()) continue;
                        psAlias.setObject(1, UUID.randomUUID());
                        psAlias.setObject(2, paramId);
                        psAlias.setString(3, a.trim());
                        psAlias.setString(4, "SYNONYM");
                        psAlias.addBatch();
                    }
                }

                if (providerPath != null && !providerPath.isBlank()) {
                    psBind.setObject(1, UUID.randomUUID());
                    psBind.setObject(2, paramId);
                    psBind.setString(3, source);
                    psBind.setString(4, null);
                    psBind.setString(5, providerPath);
                    psBind.setString(6, null);
                    psBind.addBatch();
                }

                @SuppressWarnings("unchecked")
                List<Map<String, String>> allowed =
                        (List<Map<String, String>>) allowedValues.invoke(null, canonicalId);
                if (allowed != null) {
                    int sort = 0;
                    for (Map<String, String> av : allowed) {
                        psAllowed.setObject(1, UUID.randomUUID());
                        psAllowed.setObject(2, paramId);
                        psAllowed.setObject(3, versionId);
                        psAllowed.setString(4, av.get("value"));
                        psAllowed.setString(5, av.get("label"));
                        psAllowed.setInt(6, sort++);
                        psAllowed.addBatch();
                    }
                }
            }

            psParam.executeBatch();
            psVer.executeBatch();
            psAlias.executeBatch();
            psBind.executeBatch();
            psAllowed.executeBatch();

            // Second pass: lineage (requires all parameter ids)
            for (Object defObj : defs) {
                String canonicalId = str(invoke(defObj, "id"));
                String kind = str(invoke(defObj, "type"));
                if (!"DERIVED".equalsIgnoreCase(kind)) continue;
                @SuppressWarnings("unchecked")
                List<String> primitives = (List<String>) invoke(defObj, "requiredPrimitives");
                if (primitives == null || primitives.isEmpty()) continue;
                UUID derivedId = idByCanonical.get(canonicalId);
                int seq = 0;
                for (String prim : primitives) {
                    if (prim == null || prim.isBlank()) continue;
                    UUID inputId = idByCanonical.get(prim);
                    psLin.setObject(1, UUID.randomUUID());
                    psLin.setObject(2, derivedId);
                    if (inputId != null) {
                        psLin.setObject(3, inputId);
                        psLin.setString(4, null);
                    } else {
                        psLin.setObject(3, null);
                        psLin.setString(4, prim);
                    }
                    psLin.setString(5, "REQUIRES");
                    psLin.setInt(6, seq++);
                    psLin.addBatch();
                }
            }
            psLin.executeBatch();
        }

        upsertMeta(conn, "seed_source", "GacatCatalogueSeed");
        upsertMeta(conn, "seed_count", String.valueOf(defs.size()));
        upsertMeta(conn, "inventory_version", "GACAT-PERSISTENCE-1");
    }

    private static void upsertMeta(Connection conn, String key, String value) throws Exception {
        try (PreparedStatement meta = conn.prepareStatement("""
                INSERT INTO ci_gacat_catalogue_meta (meta_key, meta_value, updated_at)
                VALUES (?, ?, NOW())
                ON CONFLICT (meta_key) DO UPDATE
                  SET meta_value = EXCLUDED.meta_value, updated_at = NOW()
                """)) {
            meta.setString(1, key);
            meta.setString(2, value);
            meta.executeUpdate();
        }
    }

    private static Object invoke(Object target, String method) throws Exception {
        if (target == null) return null;
        Method m = target.getClass().getMethod(method);
        return m.invoke(target);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static boolean bool(Object o) {
        return o instanceof Boolean b && b;
    }
}
