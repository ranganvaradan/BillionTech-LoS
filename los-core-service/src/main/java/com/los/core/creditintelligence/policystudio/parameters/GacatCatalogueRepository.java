package com.los.core.creditintelligence.policystudio.parameters;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GACAT-PERSISTENCE-1 — JDBC load of active canonical parameter definitions + related rows.
 */
@Repository
public class GacatCatalogueRepository {

    private final JdbcTemplate jdbc;

    public GacatCatalogueRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean tablesPresent() {
        try {
            Integer n = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'ci_gacat_canonical_parameter'",
                    Integer.class);
            return n != null && n > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public int parameterCount() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM ci_gacat_canonical_parameter WHERE active = TRUE", Integer.class);
        return n == null ? 0 : n;
    }

    public String meta(String key) {
        List<String> rows = jdbc.query(
                "SELECT meta_value FROM ci_gacat_catalogue_meta WHERE meta_key = ?",
                (rs, i) -> rs.getString(1), key);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<CanonicalParameterDefinition> loadActiveDefinitions() {
        String sql = """
                SELECT p.id AS param_uuid, p.canonical_id, p.display_name, p.description, p.source_family,
                       p.parameter_kind, p.unit, p.period, p.availability,
                       p.source_available, p.normalized, p.derivation_defined, p.implemented, p.production_ready,
                       p.manual_input_allowed, p.live_rule_parameter, p.live_scorecard_parameter,
                       p.capability_schema, p.cardinality,
                       v.version_no, v.calculation_definition, v.missing_data_treatment, v.period_definition,
                       v.implementation_binding, v.filters_eligibility, v.transformation, v.aggregation
                FROM ci_gacat_canonical_parameter p
                JOIN ci_gacat_canonical_parameter_version v ON v.parameter_id = p.id
                 AND v.definition_status = 'ACTIVE'
                 AND v.effective_to IS NULL
                WHERE p.active = TRUE AND p.status = 'ACTIVE'
                ORDER BY p.canonical_id
                """;
        List<Row> rows = jdbc.query(sql, (rs, i) -> mapRow(rs));
        Map<UUID, List<String>> aliases = loadAliases();
        Map<UUID, List<String>> lineageRefs = loadLineageInputRefs();

        List<CanonicalParameterDefinition> out = new ArrayList<>();
        for (Row r : rows) {
            List<String> prims = lineageRefs.getOrDefault(r.paramUuid, List.of());
            if (prims.isEmpty() && r.description != null) {
                // keep empty — do not invent
            }
            CanonicalParameterDefinition.Capability cap = CanonicalParameterDefinition.Capability.of(
                    r.capabilitySchema,
                    r.sourceAvailable,
                    r.normalized,
                    r.derivationDefined,
                    r.implemented,
                    r.productionReady,
                    r.cardinality,
                    null,
                    r.missingDataTreatment,
                    r.filtersEligibility,
                    r.transformation,
                    r.aggregation);
            // Attach provider path from bindings if present
            String providerPath = loadPrimaryProviderPath(r.paramUuid);
            if (providerPath != null) {
                cap = CanonicalParameterDefinition.Capability.of(
                        r.capabilitySchema, r.sourceAvailable, r.normalized, r.derivationDefined,
                        r.implemented, r.productionReady, r.cardinality, providerPath,
                        r.missingDataTreatment, r.filtersEligibility, r.transformation, r.aggregation);
            }
            out.add(new CanonicalParameterDefinition(
                    r.canonicalId,
                    r.displayName,
                    r.sourceFamily,
                    r.parameterKind,
                    r.unit,
                    r.period != null ? r.period : r.periodDefinition,
                    r.availability,
                    r.calculationDefinition != null ? r.calculationDefinition : r.description,
                    prims,
                    r.implementationBinding,
                    aliases.getOrDefault(r.paramUuid, List.of()),
                    r.liveRuleParameter,
                    r.liveScorecardParameter,
                    cap));
        }
        return out;
    }

    public Map<String, Object> integrityReport() {
        Map<String, Object> out = new LinkedHashMap<>();
        int params = parameterCount();
        out.put("parameterCount", params);
        out.put("empty", params == 0);
        Integer dup = jdbc.queryForObject("""
                SELECT COUNT(*) FROM (
                  SELECT canonical_id FROM ci_gacat_canonical_parameter GROUP BY canonical_id HAVING COUNT(*) > 1
                ) d
                """, Integer.class);
        out.put("duplicateCanonicalIds", dup == null ? 0 : dup);
        Integer missingActiveVersion = jdbc.queryForObject("""
                SELECT COUNT(*) FROM ci_gacat_canonical_parameter p
                WHERE p.active = TRUE AND NOT EXISTS (
                  SELECT 1 FROM ci_gacat_canonical_parameter_version v
                  WHERE v.parameter_id = p.id AND v.definition_status = 'ACTIVE' AND v.effective_to IS NULL
                )
                """, Integer.class);
        out.put("missingActiveVersion", missingActiveVersion == null ? 0 : missingActiveVersion);
        Integer multiActive = jdbc.queryForObject("""
                SELECT COUNT(*) FROM (
                  SELECT parameter_id FROM ci_gacat_canonical_parameter_version
                  WHERE definition_status = 'ACTIVE' AND effective_to IS NULL
                  GROUP BY parameter_id HAVING COUNT(*) > 1
                ) x
                """, Integer.class);
        out.put("multipleActiveVersions", multiActive == null ? 0 : multiActive);
        Integer orphanLineage = jdbc.queryForObject("""
                SELECT COUNT(*) FROM ci_gacat_canonical_parameter_lineage l
                WHERE l.input_parameter_id IS NOT NULL
                  AND NOT EXISTS (SELECT 1 FROM ci_gacat_canonical_parameter p WHERE p.id = l.input_parameter_id)
                """, Integer.class);
        out.put("orphanLineage", orphanLineage == null ? 0 : orphanLineage);
        Integer badAllowed = jdbc.queryForObject("""
                SELECT COUNT(*) FROM ci_gacat_canonical_parameter_allowed_value a
                WHERE NOT EXISTS (SELECT 1 FROM ci_gacat_canonical_parameter p WHERE p.id = a.parameter_id)
                """, Integer.class);
        out.put("invalidAllowedValues", badAllowed == null ? 0 : badAllowed);
        Integer badBinding = jdbc.queryForObject("""
                SELECT COUNT(*) FROM ci_gacat_canonical_parameter_source_binding b
                WHERE NOT EXISTS (SELECT 1 FROM ci_gacat_canonical_parameter p WHERE p.id = b.parameter_id)
                """, Integer.class);
        out.put("invalidSourceBindings", badBinding == null ? 0 : badBinding);
        out.put("inventoryVersion", meta("inventory_version"));
        out.put("seedCount", meta("seed_count"));
        boolean structuralOk = params > 0
                && (dup == null || dup == 0)
                && (missingActiveVersion == null || missingActiveVersion == 0)
                && (multiActive == null || multiActive == 0)
                && (orphanLineage == null || orphanLineage == 0);
        out.put("structuralOk", structuralOk);
        return out;
    }

    public Map<String, Object> parameterVersionView(String canonicalId) {
        List<Map<String, Object>> rows = jdbc.query("""
                SELECT p.canonical_id, p.display_name, p.source_family, p.parameter_kind,
                       p.implemented, p.production_ready, v.version_no, v.effective_from,
                       v.calculation_definition, v.implementation_binding, v.missing_data_treatment,
                       v.period_definition
                FROM ci_gacat_canonical_parameter p
                JOIN ci_gacat_canonical_parameter_version v ON v.parameter_id = p.id
                WHERE p.canonical_id = ? AND v.definition_status = 'ACTIVE' AND v.effective_to IS NULL
                """, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("canonicalId", rs.getString("canonical_id"));
            m.put("displayName", rs.getString("display_name"));
            m.put("sourceFamily", rs.getString("source_family"));
            m.put("parameterKind", rs.getString("parameter_kind"));
            m.put("implemented", rs.getBoolean("implemented"));
            m.put("productionReady", rs.getBoolean("production_ready"));
            m.put("definitionVersion", rs.getInt("version_no"));
            m.put("effectiveFrom", rs.getTimestamp("effective_from"));
            m.put("calculationDefinition", rs.getString("calculation_definition"));
            m.put("implementationBinding", rs.getString("implementation_binding"));
            m.put("missingDataTreatment", rs.getString("missing_data_treatment"));
            m.put("periodDefinition", rs.getString("period_definition"));
            return m;
        }, canonicalId);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    public List<Map<String, Object>> lineageRows(String canonicalId) {
        return jdbc.query("""
                SELECT COALESCE(ip.canonical_id, l.input_ref) AS input_ref,
                       l.relationship_type, l.sequence_no, l.required
                FROM ci_gacat_canonical_parameter_lineage l
                JOIN ci_gacat_canonical_parameter dp ON dp.id = l.derived_parameter_id
                LEFT JOIN ci_gacat_canonical_parameter ip ON ip.id = l.input_parameter_id
                WHERE dp.canonical_id = ?
                ORDER BY l.sequence_no
                """, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("input", rs.getString("input_ref"));
            m.put("relationshipType", rs.getString("relationship_type"));
            m.put("sequenceNo", rs.getInt("sequence_no"));
            m.put("required", rs.getBoolean("required"));
            return m;
        }, canonicalId);
    }

    public List<Map<String, String>> allowedValues(String canonicalId) {
        return jdbc.query("""
                SELECT a.value_code, a.display_value, a.sort_order
                FROM ci_gacat_canonical_parameter_allowed_value a
                JOIN ci_gacat_canonical_parameter p ON p.id = a.parameter_id
                WHERE p.canonical_id = ? AND a.active = TRUE
                ORDER BY a.sort_order
                """, (rs, i) -> Map.of(
                "value", rs.getString("value_code"),
                "label", rs.getString("display_value")), canonicalId);
    }

    private Map<UUID, List<String>> loadAliases() {
        Map<UUID, List<String>> out = new LinkedHashMap<>();
        jdbc.query("SELECT parameter_id, alias FROM ci_gacat_canonical_parameter_alias WHERE active = TRUE", rs -> {
            UUID id = (UUID) rs.getObject("parameter_id");
            out.computeIfAbsent(id, k -> new ArrayList<>()).add(rs.getString("alias"));
        });
        return out;
    }

    private Map<UUID, List<String>> loadLineageInputRefs() {
        Map<UUID, List<String>> out = new LinkedHashMap<>();
        jdbc.query("""
                SELECT l.derived_parameter_id, COALESCE(p.canonical_id, l.input_ref) AS ref
                FROM ci_gacat_canonical_parameter_lineage l
                LEFT JOIN ci_gacat_canonical_parameter p ON p.id = l.input_parameter_id
                ORDER BY l.sequence_no
                """, rs -> {
            UUID id = (UUID) rs.getObject("derived_parameter_id");
            String ref = rs.getString("ref");
            if (ref != null) {
                out.computeIfAbsent(id, k -> new ArrayList<>()).add(ref);
            }
        });
        return out;
    }

    private String loadPrimaryProviderPath(UUID paramUuid) {
        List<String> paths = jdbc.query("""
                SELECT provider_field_path FROM ci_gacat_canonical_parameter_source_binding
                WHERE parameter_id = ? AND active = TRUE AND provider_field_path IS NOT NULL
                ORDER BY id LIMIT 1
                """, (rs, i) -> rs.getString(1), paramUuid);
        return paths.isEmpty() ? null : paths.get(0);
    }

    private static Row mapRow(ResultSet rs) throws SQLException {
        Row r = new Row();
        r.paramUuid = (UUID) rs.getObject("param_uuid");
        r.canonicalId = rs.getString("canonical_id");
        r.displayName = rs.getString("display_name");
        r.description = rs.getString("description");
        r.sourceFamily = rs.getString("source_family");
        r.parameterKind = rs.getString("parameter_kind");
        r.unit = rs.getString("unit");
        r.period = rs.getString("period");
        r.availability = rs.getString("availability");
        r.sourceAvailable = rs.getBoolean("source_available");
        r.normalized = rs.getBoolean("normalized");
        r.derivationDefined = rs.getBoolean("derivation_defined");
        r.implemented = rs.getBoolean("implemented");
        r.productionReady = rs.getBoolean("production_ready");
        r.liveRuleParameter = rs.getString("live_rule_parameter");
        r.liveScorecardParameter = rs.getString("live_scorecard_parameter");
        r.capabilitySchema = rs.getString("capability_schema");
        r.cardinality = rs.getString("cardinality");
        r.versionNo = rs.getInt("version_no");
        r.calculationDefinition = rs.getString("calculation_definition");
        r.missingDataTreatment = rs.getString("missing_data_treatment");
        r.periodDefinition = rs.getString("period_definition");
        r.implementationBinding = rs.getString("implementation_binding");
        r.filtersEligibility = rs.getString("filters_eligibility");
        r.transformation = rs.getString("transformation");
        r.aggregation = rs.getString("aggregation");
        return r;
    }

    private static final class Row {
        UUID paramUuid;
        String canonicalId;
        String displayName;
        String description;
        String sourceFamily;
        String parameterKind;
        String unit;
        String period;
        String availability;
        boolean sourceAvailable;
        boolean normalized;
        boolean derivationDefined;
        boolean implemented;
        boolean productionReady;
        String liveRuleParameter;
        String liveScorecardParameter;
        String capabilitySchema;
        String cardinality;
        int versionNo;
        String calculationDefinition;
        String missingDataTreatment;
        String periodDefinition;
        String implementationBinding;
        String filtersEligibility;
        String transformation;
        String aggregation;
    }
}
