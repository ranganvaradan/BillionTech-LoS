package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * EQUIFAX-DERIVED-CALCULATION-CLOSURE-1 — catalogue increment + BUILT_IN_CODE definitions
 * for BureauMetricService emitted IDs. Does not rewrite calculators.
 */
public class V146__equifax_retail_derived_gacat_and_calc_defs extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();
        addCalculationTypeColumn(conn);

        Class<?> seedClass = Class.forName(
                "com.los.core.creditintelligence.policystudio.parameters.GacatCatalogueSeed");
        Method all = seedClass.getDeclaredMethod("all");
        all.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<?> defs = (List<?>) all.invoke(null);

        Class<?> authoring = Class.forName(
                "com.los.core.creditintelligence.policystudio.parameters.AuthoringValueTypes");
        Method allowedValues = authoring.getMethod("allowedValues", String.class);

        Class<?> producer = Class.forName(
                "com.los.core.creditintelligence.policystudio.parameters.execution.BuiltInBureauMetricProducer");
        Field emittedField = producer.getField("EMITTED_IDS");
        @SuppressWarnings("unchecked")
        Set<String> emitted = new TreeSet<>((Set<String>) emittedField.get(null));

        Set<String> existing = new HashSet<>();
        Map<String, UUID> idByCanonical = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, canonical_id FROM ci_gacat_canonical_parameter")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID id = (UUID) rs.getObject("id");
                    String cid = rs.getString("canonical_id");
                    existing.add(cid);
                    idByCanonical.put(cid, id);
                }
            }
        }

        Timestamp now = Timestamp.from(Instant.now());
        int inserted = insertMissingParams(conn, defs, existing, idByCanonical, allowedValues, now);
        int updated = updateBureauRetailDerived(conn, defs, idByCanonical, now);
        refreshBureauRetailLineage(conn, defs, idByCanonical);
        int calcInserted = insertBuiltInCodeDefinitions(conn, defs, emitted, now);

        upsertMeta(conn, "equifax_derived_gacat_inserted", String.valueOf(inserted));
        upsertMeta(conn, "equifax_derived_gacat_updated", String.valueOf(updated));
        upsertMeta(conn, "equifax_builtin_calc_defs_inserted", String.valueOf(calcInserted));
        upsertMeta(conn, "equifax_builtin_calc_defs_target", String.valueOf(emitted.size()));
        upsertMeta(conn, "seed_count", String.valueOf(defs.size()));
        upsertMeta(conn, "inventory_version", "EQUIFAX-DERIVED-CALC-1");
    }

    private static void addCalculationTypeColumn(Connection conn) throws Exception {
        try (var st = conn.createStatement()) {
            st.execute("""
                    ALTER TABLE ci_gacat_derived_calculation_definition
                      ADD COLUMN IF NOT EXISTS calculation_type VARCHAR(40) NOT NULL DEFAULT 'AUTHORED_EXPRESSION'
                    """);
            st.execute("""
                    DO $$ BEGIN
                      ALTER TABLE ci_gacat_derived_calculation_definition
                        ADD CONSTRAINT ck_derived_calc_calculation_type
                        CHECK (calculation_type IN ('AUTHORED_EXPRESSION','BUILT_IN_CODE'));
                    EXCEPTION WHEN duplicate_object THEN NULL;
                    END $$
                    """);
        }
    }

    private static int insertMissingParams(
            Connection conn, List<?> defs, Set<String> existing, Map<String, UUID> idByCanonical,
            Method allowedValues, Timestamp now) throws Exception {
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
        String insertBinding = """
                INSERT INTO ci_gacat_canonical_parameter_source_binding
                  (id, parameter_id, source_family, provider_code, provider_field_path,
                   normalizer_binding, active, metadata_json)
                VALUES (?,?,?,?,?,?,TRUE,NULL)
                """;

        int inserted = 0;
        try (PreparedStatement psParam = conn.prepareStatement(insertParam);
             PreparedStatement psVer = conn.prepareStatement(insertVersion);
             PreparedStatement psAlias = conn.prepareStatement(insertAlias);
             PreparedStatement psAllowed = conn.prepareStatement(insertAllowed);
             PreparedStatement psBind = conn.prepareStatement(insertBinding)) {

            for (Object defObj : defs) {
                String canonicalId = str(invoke(defObj, "id"));
                if (canonicalId == null || existing.contains(canonicalId)) {
                    continue;
                }
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
                existing.add(canonicalId);
                inserted++;

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
                psVer.setString(i, "V146-EQUIFAX-DERIVED");
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
                    psBind.setString(4, "EQUIFAX");
                    psBind.setString(5, providerPath);
                    psBind.setString(6, "EquifaxBureauAccountExtractor");
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
        }
        return inserted;
    }

    private static int updateBureauRetailDerived(
            Connection conn, List<?> defs, Map<String, UUID> idByCanonical, Timestamp now) throws Exception {
        String updateParam = """
                UPDATE ci_gacat_canonical_parameter
                   SET display_name = ?, description = ?, unit = ?, period = ?, availability = ?,
                       source_available = ?, normalized = ?, derivation_defined = ?, implemented = ?,
                       production_ready = ?, live_rule_parameter = ?, live_scorecard_parameter = ?,
                       capability_schema = ?, cardinality = ?, updated_at = ?
                 WHERE id = ?
                """;
        String updateVer = """
                UPDATE ci_gacat_canonical_parameter_version
                   SET calculation_definition = ?, missing_data_treatment = ?, period_definition = ?,
                       implementation_binding = ?, filters_eligibility = ?, transformation = ?, aggregation = ?
                 WHERE parameter_id = ? AND definition_status = 'ACTIVE' AND effective_to IS NULL
                """;
        int updated = 0;
        try (PreparedStatement psParam = conn.prepareStatement(updateParam);
             PreparedStatement psVer = conn.prepareStatement(updateVer)) {
            for (Object defObj : defs) {
                String canonicalId = str(invoke(defObj, "id"));
                String kind = str(invoke(defObj, "type"));
                String source = str(invoke(defObj, "evaluatedFrom"));
                if (!"DERIVED".equalsIgnoreCase(kind) || !"Bureau Retail".equals(source)) {
                    continue;
                }
                UUID paramId = idByCanonical.get(canonicalId);
                if (paramId == null) continue;

                String displayName = str(invoke(defObj, "businessName"));
                String unit = str(invoke(defObj, "unit"));
                String period = str(invoke(defObj, "period"));
                String availability = str(invoke(defObj, "availability"));
                String calc = str(invoke(defObj, "calculationSummary"));
                String binding = str(invoke(defObj, "existingImplementationBinding"));
                String liveRule = str(invoke(defObj, "liveRuleParameter"));
                String liveScore = str(invoke(defObj, "liveScorecardParameter"));
                Object cap = invoke(defObj, "capability");
                boolean sourceAvailable = bool(invoke(cap, "sourceAvailable"));
                boolean normalized = bool(invoke(cap, "normalized"));
                boolean derivationDefined = bool(invoke(cap, "derivationDefined"));
                boolean implemented = bool(invoke(cap, "implemented"));
                boolean productionReady = bool(invoke(cap, "productionReady"));
                String schema = str(invoke(cap, "schema"));
                String cardinality = str(invoke(cap, "cardinality"));
                String missing = str(invoke(cap, "missingDataTreatment"));
                String filters = str(invoke(cap, "filtersEligibility"));
                String transformation = str(invoke(cap, "transformation"));
                String aggregation = str(invoke(cap, "aggregation"));

                int i = 1;
                psParam.setString(i++, displayName);
                psParam.setString(i++, calc);
                psParam.setString(i++, unit);
                psParam.setString(i++, period);
                psParam.setString(i++, availability);
                psParam.setBoolean(i++, sourceAvailable);
                psParam.setBoolean(i++, normalized);
                psParam.setBoolean(i++, derivationDefined);
                psParam.setBoolean(i++, implemented);
                psParam.setBoolean(i++, productionReady);
                psParam.setString(i++, liveRule);
                psParam.setString(i++, liveScore);
                psParam.setString(i++, schema);
                psParam.setString(i++, cardinality);
                psParam.setTimestamp(i++, now);
                psParam.setObject(i, paramId);
                psParam.addBatch();

                i = 1;
                psVer.setString(i++, calc);
                psVer.setString(i++, missing);
                psVer.setString(i++, period);
                psVer.setString(i++, binding);
                psVer.setString(i++, filters);
                psVer.setString(i++, transformation);
                psVer.setString(i++, aggregation);
                psVer.setObject(i, paramId);
                psVer.addBatch();
                updated++;
            }
            psParam.executeBatch();
            psVer.executeBatch();
        }
        return updated;
    }

    private static void refreshBureauRetailLineage(
            Connection conn, List<?> defs, Map<String, UUID> idByCanonical) throws Exception {
        List<UUID> derivedIds = new ArrayList<>();
        Map<UUID, List<String>> primitivesByDerived = new LinkedHashMap<>();
        for (Object defObj : defs) {
            String kind = str(invoke(defObj, "type"));
            String source = str(invoke(defObj, "evaluatedFrom"));
            if (!"DERIVED".equalsIgnoreCase(kind) || !"Bureau Retail".equals(source)) {
                continue;
            }
            String canonicalId = str(invoke(defObj, "id"));
            UUID derivedId = idByCanonical.get(canonicalId);
            if (derivedId == null) continue;
            derivedIds.add(derivedId);
            @SuppressWarnings("unchecked")
            List<String> primitives = (List<String>) invoke(defObj, "requiredPrimitives");
            primitivesByDerived.put(derivedId, primitives == null ? List.of() : primitives);
        }
        if (derivedIds.isEmpty()) return;

        StringBuilder in = new StringBuilder();
        for (int i = 0; i < derivedIds.size(); i++) {
            if (i > 0) in.append(',');
            in.append('?');
        }
        try (PreparedStatement del = conn.prepareStatement(
                "DELETE FROM ci_gacat_canonical_parameter_lineage WHERE derived_parameter_id IN (" + in + ")")) {
            int i = 1;
            for (UUID id : derivedIds) {
                del.setObject(i++, id);
            }
            del.executeUpdate();
        }

        String insertLineage = """
                INSERT INTO ci_gacat_canonical_parameter_lineage
                  (id, derived_parameter_id, input_parameter_id, input_ref, relationship_type,
                   sequence_no, required, metadata_json)
                VALUES (?,?,?,?,?,?,TRUE,NULL)
                """;
        try (PreparedStatement psLin = conn.prepareStatement(insertLineage)) {
            for (Map.Entry<UUID, List<String>> e : primitivesByDerived.entrySet()) {
                int seq = 0;
                for (String prim : e.getValue()) {
                    if (prim == null || prim.isBlank()) continue;
                    UUID inputId = idByCanonical.get(prim);
                    psLin.setObject(1, UUID.randomUUID());
                    psLin.setObject(2, e.getKey());
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
    }

    private static int insertBuiltInCodeDefinitions(
            Connection conn, List<?> defs, Set<String> emitted, Timestamp now) throws Exception {
        Map<String, Object> byId = new LinkedHashMap<>();
        for (Object defObj : defs) {
            String id = str(invoke(defObj, "id"));
            if (id != null) byId.put(id, defObj);
        }

        Set<String> already = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT DISTINCT canonical_parameter_id
                  FROM ci_gacat_derived_calculation_definition
                 WHERE status IS NULL OR status <> 'RETIRED'
                """)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    already.add(rs.getString(1));
                }
            }
        }

        String insert = """
                INSERT INTO ci_gacat_derived_calculation_definition (
                  id, tenant_id, canonical_parameter_id, scope, status, calculation_type,
                  result_type, unit, description, expression_json, dependency_ids,
                  version_no, created_by, created_at, updated_at, metadata
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """;
        int inserted = 0;
        try (PreparedStatement ps = conn.prepareStatement(insert)) {
            for (String id : emitted) {
                if (already.contains(id)) continue;
                Object defObj = byId.get(id);
                String description = defObj == null ? null : str(invoke(defObj, "calculationSummary"));
                String unit = defObj == null ? null : str(invoke(defObj, "unit"));
                @SuppressWarnings("unchecked")
                List<String> primitives = defObj == null
                        ? List.of()
                        : (List<String>) invoke(defObj, "requiredPrimitives");
                String expr = "{\"type\":\"BUILT_IN_CODE\",\"executor\":\"BureauMetricService\","
                        + "\"calculationType\":\"BUILT_IN_CODE\",\"metricCode\":\"" + jsonEscape(id) + "\"}";
                String deps = jsonStringArray(primitives);
                String meta = "{\"executionAuthority\":\"BureauMetricService\","
                        + "\"producer\":\"BuiltInBureauMetricProducer\"}";

                int i = 1;
                ps.setObject(i++, UUID.randomUUID());
                ps.setObject(i++, null);
                ps.setString(i++, id);
                ps.setString(i++, "PLATFORM");
                ps.setString(i++, "TESTED");
                ps.setString(i++, "BUILT_IN_CODE");
                ps.setString(i++, "NUMBER");
                ps.setString(i++, unit);
                ps.setString(i++, description);
                ps.setObject(i++, expr, Types.OTHER);
                ps.setObject(i++, deps, Types.OTHER);
                ps.setInt(i++, 1);
                ps.setString(i++, "V146-EQUIFAX-DERIVED");
                ps.setTimestamp(i++, now);
                ps.setTimestamp(i++, now);
                ps.setObject(i, meta, Types.OTHER);
                ps.addBatch();
                inserted++;
            }
            ps.executeBatch();
        }
        return inserted;
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

    private static String jsonStringArray(List<String> ids) {
        if (ids == null || ids.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String id : ids) {
            if (id == null || id.isBlank()) continue;
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(jsonEscape(id)).append('"');
        }
        return sb.append(']').toString();
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
