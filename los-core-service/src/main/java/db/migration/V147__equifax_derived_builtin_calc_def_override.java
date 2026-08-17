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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * EQUIFAX-DERIVED-CALCULATION-CLOSURE-1 follow-up:
 * Ensure every BureauMetricService emitted ID has a latest PLATFORM BUILT_IN_CODE TESTED
 * definition. V146 skipped IDs that already had a non-retired authored row.
 */
public class V147__equifax_derived_builtin_calc_def_override extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();
        Timestamp now = Timestamp.from(Instant.now());

        Class<?> seedClass = Class.forName(
                "com.los.core.creditintelligence.policystudio.parameters.GacatCatalogueSeed");
        Method all = seedClass.getDeclaredMethod("all");
        all.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<?> defs = (List<?>) all.invoke(null);
        Map<String, Object> byId = new LinkedHashMap<>();
        for (Object defObj : defs) {
            Method idM = defObj.getClass().getMethod("id");
            Object id = idM.invoke(defObj);
            if (id != null) byId.put(String.valueOf(id), defObj);
        }

        Class<?> producer = Class.forName(
                "com.los.core.creditintelligence.policystudio.parameters.execution.BuiltInBureauMetricProducer");
        Field emittedField = producer.getField("EMITTED_IDS");
        @SuppressWarnings("unchecked")
        Set<String> emitted = new TreeSet<>((Set<String>) emittedField.get(null));

        int retired = 0;
        int inserted = 0;
        for (String id : emitted) {
            Latest latest = latestNonRetired(conn, id);
            boolean ok = latest != null
                    && "BUILT_IN_CODE".equalsIgnoreCase(nullToEmpty(latest.calculationType))
                    && ("TESTED".equalsIgnoreCase(latest.status)
                    || "PRODUCTION_READY".equalsIgnoreCase(latest.status));
            if (ok) {
                continue;
            }
            retired += retireActive(conn, id, now);
            int nextVer = maxVersion(conn, id) + 1;
            insertBuiltIn(conn, byId.get(id), id, nextVer, now);
            inserted++;
        }
        upsertMeta(conn, "equifax_v147_retired", String.valueOf(retired));
        upsertMeta(conn, "equifax_v147_inserted", String.valueOf(inserted));
    }

    private record Latest(UUID id, String status, String calculationType, int versionNo) {}

    private static Latest latestNonRetired(Connection conn, String canonicalId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT id, status, calculation_type, version_no
                  FROM ci_gacat_derived_calculation_definition
                 WHERE canonical_parameter_id = ?
                   AND tenant_id IS NULL
                   AND (status IS NULL OR status <> 'RETIRED')
                 ORDER BY version_no DESC
                 LIMIT 1
                """)) {
            ps.setString(1, canonicalId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new Latest(
                        (UUID) rs.getObject("id"),
                        rs.getString("status"),
                        rs.getString("calculation_type"),
                        rs.getInt("version_no"));
            }
        }
    }

    private static int maxVersion(Connection conn, String canonicalId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT COALESCE(MAX(version_no), 0)
                  FROM ci_gacat_derived_calculation_definition
                 WHERE canonical_parameter_id = ?
                   AND tenant_id IS NULL
                """)) {
            ps.setString(1, canonicalId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static int retireActive(Connection conn, String canonicalId, Timestamp now) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE ci_gacat_derived_calculation_definition
                   SET status = 'RETIRED', updated_at = ?
                 WHERE canonical_parameter_id = ?
                   AND tenant_id IS NULL
                   AND (status IS NULL OR status <> 'RETIRED')
                """)) {
            ps.setTimestamp(1, now);
            ps.setString(2, canonicalId);
            return ps.executeUpdate();
        }
    }

    private static void insertBuiltIn(
            Connection conn, Object defObj, String id, int versionNo, Timestamp now) throws Exception {
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
                + "\"producer\":\"BuiltInBureauMetricProducer\",\"source\":\"V147\"}";
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO ci_gacat_derived_calculation_definition (
                  id, tenant_id, canonical_parameter_id, scope, status, calculation_type,
                  result_type, unit, description, expression_json, dependency_ids,
                  version_no, created_by, created_at, updated_at, metadata
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
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
            ps.setInt(i++, versionNo);
            ps.setString(i++, "V147-EQUIFAX-DERIVED");
            ps.setTimestamp(i++, now);
            ps.setTimestamp(i++, now);
            ps.setObject(i, meta, Types.OTHER);
            ps.executeUpdate();
        }
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
        return target.getClass().getMethod(method).invoke(target);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
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
