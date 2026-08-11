package db.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * SCORECARD-CONVERGENCE-1 — stamp EXACT GACAT liveScorecardParameter bindings onto scorecard rows.
 * Does not change conditions, points, thresholds, or ACTIVE decision semantics.
 */
public class V115__ScorecardCanonicalFactorBindings extends BaseJavaMigration {

    private static final Map<String, String> EXACT = Map.of(
            "BUREAU_SCORE", "bureau.score",
            "AVERAGE_BANK_BALANCE", "banking.avg_daily_balance_3m",
            "GST_INCOME", "gst.turnover.trailing_12m",
            "KYC_QUALITY", "kyc.quality",
            "OBLIGATION_RATIO", "obligation.ratio",
            "REPAYMENT_HISTORY", "bureau.credit_after_overdue.clean_history_months"
    );

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();
        try (Statement st = conn.createStatement()) {
            st.execute("""
                    COMMENT ON COLUMN underwriting_scorecards.scorecard_json IS
                    'Rows may include canonicalParameterId, canonicalDefinitionVersion, mappingStatus, legacyParameterKey (SCORECARD-CONVERGENCE-1)';
                    """);
        }

        try (Statement sel = conn.createStatement();
             ResultSet rs = sel.executeQuery("SELECT id, scorecard_json::text FROM underwriting_scorecards")) {
            while (rs.next()) {
                UUID id = (UUID) rs.getObject(1);
                String json = rs.getString(2);
                String updated = stampExactBindings(json);
                if (updated != null && !updated.equals(json)) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE underwriting_scorecards SET scorecard_json = ?::jsonb WHERE id = ?")) {
                        ps.setString(1, updated);
                        ps.setObject(2, id);
                        ps.executeUpdate();
                    }
                }
            }
        }
    }

    public static String stampExactBindings(String json) throws Exception {
        if (json == null || json.isBlank()) return json;
        JsonNode root = MAPPER.readTree(json);
        if (!(root instanceof ObjectNode obj) || !obj.has("rows") || !obj.get("rows").isArray()) {
            return json;
        }
        boolean changed = false;
        for (JsonNode rowNode : obj.get("rows")) {
            if (!(rowNode instanceof ObjectNode row)) continue;
            String param = row.has("parameter") && !row.get("parameter").isNull()
                    ? row.get("parameter").asText() : null;
            if (param == null) continue;
            String canonical = EXACT.get(param);
            if (canonical == null) continue;
            if (row.has("canonicalParameterId")
                    && canonical.equals(row.get("canonicalParameterId").asText())) {
                continue;
            }
            row.put("legacyParameterKey", param);
            row.put("canonicalParameterId", canonical);
            row.put("canonicalDefinitionVersion", 1);
            row.put("mappingStatus", "EXACT");
            changed = true;
        }
        return changed ? MAPPER.writeValueAsString(root) : json;
    }
}
