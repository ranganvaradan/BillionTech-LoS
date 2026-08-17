package com.los.core.creditintelligence.policystudio.parameters.semantic;

import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalFactMaterializer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wave-4 semantic element contracts for Wave-3 materialized collections.
 * Describes calculation/authoring contracts — not a duplicate of JPA DTOs.
 */
public final class GacatCollectionElementContracts {

    private GacatCollectionElementContracts() {}

    public static List<Map<String, Object>> allContracts() {
        List<Map<String, Object>> out = new ArrayList<>();
        out.add(tradelines());
        out.add(paymentHistory());
        out.add(inquiries());
        out.add(assessed("bank.transactions", "COLLECTION", "CiBankTransaction / banking normalization",
                false, "Normalized model exists; not emitted as GACAT catalogue row in Wave 4"));
        out.add(assessed("bank.accounts", "COLLECTION", "CiBankAccount",
                false, "Account-level fields exist as bank.account.* ingredients; collection key not catalalogued"));
        out.add(assessed("gst.periods", "COLLECTION", "GST return periods",
                false, "gst.return.* are period-scoped ingredients; period collection not a catalogue ID"));
        out.add(assessed("itr.tax_periods", "COLLECTION", "ITR / tax periods",
                false, "itr.* / financial.* period ingredients; collection not catalalogued"));
        return out;
    }

    public static Map<String, Object> tradelines() {
        Map<String, Object> m = base(
                CanonicalFactMaterializer.TRADELINES,
                "COLLECTION",
                "CiBureauTradeline",
                true);
        m.put("elementName", "tradeline");
        m.put("recordIdentityFields", List.of("tradelineId", "tradeline_ref", "providerTradelineRef"));
        m.put("orderingSemantics", "Unordered for aggregation; opened_date available for vintage calcs");
        m.put("missingSemantics",
                "Key absent = bureau report/source not present; key present with [] = report present, zero tradelines");
        m.put("fields", List.of(
                field("tradelineId", "STRING", "Record id"),
                field("account_type", "ENUM", "productCategory / accountTypeRaw"),
                field("productCategory", "ENUM", null),
                field("status", "ENUM", "accountStatus"),
                field("secured", "BOOLEAN", null),
                field("overdue_amount", "MONEY", null),
                field("current_balance", "MONEY", null),
                field("credit_limit", "MONEY", "highCredit or sanctionedAmount"),
                field("high_credit", "MONEY", null),
                field("opened_date", "DATE", null),
                field("closed_date", "DATE", null),
                field("suit_filed", "BOOLEAN", null),
                field("written_off", "BOOLEAN", null),
                field("settled", "BOOLEAN", null),
                field("is_live", "BOOLEAN", null),
                field("lender", "STRING", null),
                field("ownership", "ENUM", null),
                field("last_payment_amount", "MONEY", null),
                field("last_payment_date", "DATE", null),
                field("term_frequency", "ENUM", null),
                field("closure_reason", "STRING", null)
        ));
        return m;
    }

    public static Map<String, Object> paymentHistory() {
        Map<String, Object> m = base(
                CanonicalFactMaterializer.PAYMENT_HISTORY,
                "HISTORY",
                "CiBureauPaymentHistory",
                true);
        m.put("elementName", "payment_history_observation");
        m.put("periodField", "month");
        m.put("periodFieldAliases", List.of("period"));
        m.put("periodFormat", "YYYY-MM");
        m.put("recordIdentityFields", List.of("observationId", "tradelineId", "month"));
        m.put("orderingSemantics", "Prefer month ascending for trailing windows; materializer may load desc");
        m.put("missingSemantics",
                "Key absent = PH source not present; key present with [] = source claimed/loaded but no observations");
        m.put("fields", List.of(
                field("month", "YEAR_MONTH", "Required for TRAILING_WINDOW"),
                field("period", "YEAR_MONTH", "Alias of month"),
                field("dpd", "INTEGER", "Days past due"),
                field("paymentStatus", "ENUM", "status"),
                field("status", "ENUM", null),
                field("tradelineId", "STRING", null),
                field("tradeline_ref", "STRING", null),
                field("observationId", "STRING", null),
                field("sourceReference", "STRING", null),
                field("estimated", "BOOLEAN", null),
                field("suit_filed_status", "ENUM", null),
                field("asset_classification_status", "ENUM", null)
        ));
        return m;
    }

    public static Map<String, Object> inquiries() {
        Map<String, Object> m = base(
                CanonicalFactMaterializer.INQUIRIES,
                "COLLECTION",
                "CiBureauInquiry",
                true);
        m.put("elementName", "inquiry");
        m.put("periodField", "inquiry_date");
        m.put("recordIdentityFields", List.of("inquiryId"));
        m.put("orderingSemantics", "Typically by inquiry_date desc");
        m.put("missingSemantics",
                "Key absent = report absent; key present with [] = report present, zero inquiries");
        m.put("fields", List.of(
                field("inquiryId", "STRING", null),
                field("inquiry_date", "DATE", null),
                field("date", "DATE", "Alias"),
                field("member_name", "STRING", null),
                field("purpose", "ENUM", null),
                field("amount", "MONEY", null),
                field("inquiry_time", "STRING", null),
                field("time", "STRING", "Alias")
        ));
        return m;
    }

    /** Field schema for SafeDerived validation (lowercase keys). */
    public static Map<String, String> rowSchemaFor(String collectionId) {
        Map<String, Object> contract = allContracts().stream()
                .filter(c -> collectionId.equals(c.get("collectionId")))
                .findFirst()
                .orElse(null);
        if (contract == null) return Map.of();
        Map<String, String> schema = new LinkedHashMap<>();
        Object fields = contract.get("fields");
        if (fields instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> f && f.get("name") != null) {
                    Object vt = f.get("valueType");
                    schema.put(String.valueOf(f.get("name")).toLowerCase(),
                            vt == null ? "UNKNOWN" : String.valueOf(vt));
                }
            }
        }
        return schema;
    }

    private static Map<String, Object> base(String id, String cardinality, String sourceEntity, boolean materialized) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("collectionId", id);
        m.put("cardinality", cardinality);
        m.put("sourceEntity", sourceEntity);
        m.put("wave3Materialized", materialized);
        m.put("gacatCatalogueRow", false);
        m.put("note", "Execution fact key / semantic contract — not a new GACAT ID");
        return m;
    }

    private static Map<String, Object> assessed(
            String id, String cardinality, String sourceEntity, boolean materialized, String note) {
        Map<String, Object> m = base(id, cardinality, sourceEntity, materialized);
        m.put("wave3Materialized", materialized);
        m.put("assessment", note);
        m.put("fields", List.of());
        return m;
    }

    private static Map<String, Object> field(String name, String valueType, String note) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("name", name);
        f.put("valueType", valueType);
        if (note != null) f.put("note", note);
        return f;
    }
}
