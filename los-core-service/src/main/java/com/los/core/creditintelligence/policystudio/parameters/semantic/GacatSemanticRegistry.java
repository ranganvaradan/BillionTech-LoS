package com.los.core.creditintelligence.policystudio.parameters.semantic;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.GacatCatalogueSeed;
import com.los.core.creditintelligence.policystudio.parameters.execution.BuiltInBankingMetricProducer;
import com.los.core.creditintelligence.policystudio.parameters.execution.BuiltInBureauMetricProducer;
import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalFactMaterializer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Wave-4 semantic registry — classifies all GACAT catalogue IDs without changing IDs or CPES.
 * Authority for semantics only; execution capability remains Wave-1 spine.
 */
public final class GacatSemanticRegistry {

    private static final class Holder {
        static final GacatSemanticRegistry INSTANCE = loadFromSeed();
    }

    /** Shared immutable registry for production/read paths. */
    public static GacatSemanticRegistry shared() {
        return Holder.INSTANCE;
    }

    private static final Set<String> BUILT_IN_IDS = Set.copyOf(union(
            BuiltInBureauMetricProducer.EMITTED_IDS,
            Set.of(
                    BuiltInBankingMetricProducer.EMI_BOUNCE,
                    BuiltInBankingMetricProducer.ADB_3M,
                    "gst.turnover.trailing_12m",
                    "gst.turnover.trailing_3m",
                    "gst.turnover.trailing_6m",
                    "gst.filing.timeliness_score",
                    "gst.gstr1_gstr3b_turnover_variance",
                    "gst.return.missing_count_12m",
                    "gst.return.late_count_12m",
                    "kyc.quality",
                    "obligation.ratio",
                    "bureau.inquiries.current_month",
                    "bureau.inquiries.last_3m"
            )));

    /** Authored design intent (may or may not have a definition). */
    private static final Set<String> AUTHORED_INTENT = Set.of(
            "bureau.thin_file_indicator",
            "bureau.commercial.total_facility_exposure",
            "bureau.commercial.total_sanction",
            "bureau.commercial.utilisation",
            "banking.negative_balance_days_3m",
            "banking.cash_intensity_3m",
            "banking.od_utilisation",
            "banking.monthly_credits_3m",
            "banking.cheque_return_count_3m",
            "banking.settlement.count_monthly_avg_3m",
            "banking.settlement.avg_daily_3m",
            "banking.transaction_count.average_monthly_3m",
            "banking.inward_return.ratio_3m",
            "banking.adjusted_business_credits_12m",
            "banking.monthly_obligation",
            "financial.dscr",
            "financial.interest_coverage",
            "financial.debt_equity",
            "financial.tol_tnw",
            "financial.pat_positive",
            "collateral.ltv"
    );

    private final Map<String, GacatSemanticEntry> byId;

    private GacatSemanticRegistry(Map<String, GacatSemanticEntry> byId) {
        this.byId = Map.copyOf(byId);
    }

    public static GacatSemanticRegistry loadFromSeed() {
        return classify(GacatCatalogueSeed.all());
    }

    public static GacatSemanticRegistry classify(List<CanonicalParameterDefinition> defs) {
        Map<String, GacatSemanticEntry> out = new LinkedHashMap<>();
        for (CanonicalParameterDefinition d : defs) {
            out.put(d.id(), classifyOne(d));
        }
        return new GacatSemanticRegistry(out);
    }

    public String semanticVersion() {
        return GacatSemanticTaxonomy.SEMANTIC_VERSION;
    }

    public Optional<GacatSemanticEntry> find(String canonicalId) {
        if (canonicalId == null) return Optional.empty();
        return Optional.ofNullable(byId.get(canonicalId.trim()));
    }

    public List<GacatSemanticEntry> all() {
        return List.copyOf(byId.values());
    }

    public int size() {
        return byId.size();
    }

    public Map<String, Long> countByParameterClass() {
        return count(e -> e.parameterClass().name());
    }

    public Map<String, Long> countByCardinality() {
        return count(e -> e.cardinality().name());
    }

    public Map<String, Long> countByCalculationMode() {
        return count(e -> e.calculationMode().name());
    }

    public Map<String, Long> countByValueType() {
        return count(e -> e.valueType().name());
    }

    public Map<String, Long> countByFamily() {
        return count(GacatSemanticEntry::family);
    }

    public long policySelectableTrueCount() {
        return byId.values().stream().filter(GacatSemanticEntry::policySelectableDefault).count();
    }

    public long unknownSemanticCount() {
        return byId.values().stream()
                .filter(e -> e.parameterClass() == GacatSemanticTaxonomy.ParameterClass.UNKNOWN
                        || e.cardinality() == GacatSemanticTaxonomy.Cardinality.UNKNOWN
                        || e.calculationMode() == GacatSemanticTaxonomy.CalculationMode.UNKNOWN)
                .count();
    }

    public List<GacatSemanticEntry> unknownOrNeedsReview() {
        return byId.values().stream()
                .filter(e -> e.parameterClass() == GacatSemanticTaxonomy.ParameterClass.UNKNOWN
                        || (e.semanticIssue() != null && e.semanticIssue().contains("NEEDS_SEMANTIC_REVIEW"))
                        || e.overlapRelation() == GacatSemanticTaxonomy.OverlapRelation.NEEDS_REVIEW)
                .toList();
    }

    public Map<String, Object> snapshotArtifact() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("semanticVersion", GacatSemanticTaxonomy.SEMANTIC_VERSION);
        root.put("wave", "WAVE_4");
        root.put("gacatTotal", byId.size());
        root.put("parameterClassCounts", countByParameterClass());
        root.put("cardinalityCounts", countByCardinality());
        root.put("calculationModeCounts", countByCalculationMode());
        root.put("valueTypeCounts", countByValueType());
        root.put("familyCounts", countByFamily());
        root.put("policySelectableDefaultTrue", policySelectableTrueCount());
        root.put("policySelectableDefaultFalse", byId.size() - policySelectableTrueCount());
        root.put("unknownSemanticCount", unknownSemanticCount());
        root.put("legacyFlagsAuthority", "LEGACY_EXECUTION_METADATA");
        root.put("canonicalIdsChanged", 0);
        root.put("parameters", byId.values().stream().map(GacatSemanticEntry::toMap).toList());
        root.put("collectionElementContracts", GacatCollectionElementContracts.allContracts());
        root.put("knownOverlaps", knownOverlaps());
        return root;
    }

    private Map<String, Long> count(java.util.function.Function<GacatSemanticEntry, String> keyFn) {
        return byId.values().stream()
                .collect(Collectors.groupingBy(keyFn, LinkedHashMap::new, Collectors.counting()));
    }

    static GacatSemanticEntry classifyOne(CanonicalParameterDefinition d) {
        String id = d.id();
        String family = familyOf(id, d.evaluatedFrom());
        GacatSemanticTaxonomy.Cardinality card = mapCardinality(id, d);
        GacatSemanticTaxonomy.ParameterClass cls = mapParameterClass(id, d, card);
        GacatSemanticTaxonomy.CalculationMode mode = mapCalculationMode(id, d, cls);
        GacatSemanticTaxonomy.ValueType vt = mapValueType(id, d, card);
        GacatSemanticTaxonomy.SemanticUnit unit = mapUnit(d.unit(), vt);
        boolean selectable = defaultSelectable(cls);
        String issue = null;
        String aliasGroup = null;
        GacatSemanticTaxonomy.OverlapRelation overlap = GacatSemanticTaxonomy.OverlapRelation.NONE;
        String overlapNote = null;

        // --- known audit issues (metadata only) ---
        if ("bureau.status_ntc".equals(id) || "bureau.thin_file_indicator".equals(id)) {
            aliasGroup = "bureau.ntc_thin_file";
            overlap = GacatSemanticTaxonomy.OverlapRelation.OVERLAPPING_CONCEPT;
            overlapNote = "NTC/thin-file concepts overlap; IDs remain distinct";
            issue = "OVERLAPPING_NTC_THIN_FILE";
        }
        if ("bureau.recent_inquiries_90d".equals(id)
                || "bureau.inquiries.last_3m".equals(id)
                || "bureau.inquiries.current_month".equals(id)) {
            aliasGroup = "bureau.inquiry_windows";
            overlap = "bureau.recent_inquiries_90d".equals(id)
                    ? GacatSemanticTaxonomy.OverlapRelation.SEMANTICALLY_DISTINCT
                    : GacatSemanticTaxonomy.OverlapRelation.SEMANTICALLY_DISTINCT;
            overlapNote = "90d rolling ≠ calendar 3m ≠ current month; ENQUIRIES_3M alias is DANGEROUS";
        }
        if ("bureau.cc_overdue_amount".equals(id) || "bureau.overdue.amount".equals(id)) {
            aliasGroup = "bureau.overdue_amount";
            overlap = GacatSemanticTaxonomy.OverlapRelation.SEMANTICALLY_DISTINCT;
            overlapNote = "CC overdue ≠ generic overdue aggregate";
        }
        if ("bureau.credit_after_overdue.clean_history_months".equals(id)
                || "bureau.months_since_last_delinquency".equals(id)) {
            aliasGroup = "bureau.clean_vs_months_since_dq";
            overlap = GacatSemanticTaxonomy.OverlapRelation.SEMANTICALLY_DISTINCT;
            overlapNote = "Post-overdue clean streak ≠ months since any delinquency";
        }
        if ("kyc.pan.verified".equals(id)) {
            aliasGroup = "kyc.pan_verified_path";
            overlap = GacatSemanticTaxonomy.OverlapRelation.TRUE_ALIAS;
            overlapNote = "Runtime path kyc.pan_verified is TRUE_COMPAT alias of kyc.pan.verified";
        }
        if ("aa.transport.note".equals(id)) {
            cls = GacatSemanticTaxonomy.ParameterClass.CONFIGURATION;
            mode = GacatSemanticTaxonomy.CalculationMode.CONFIG;
            selectable = false;
            issue = "AA_TRANSPORT_META_NOT_BORROWER_FACT";
        }
        if (id.startsWith("product.") || id.startsWith("program.")) {
            cls = GacatSemanticTaxonomy.ParameterClass.CONFIGURATION;
            mode = GacatSemanticTaxonomy.CalculationMode.CONFIG;
            selectable = false;
        }
        if ("itr.income.total".equals(id) || id.startsWith("financial.")) {
            if (id.startsWith("financial.") && d.type().equals(CanonicalParameterDefinition.DERIVED)) {
                // ratios stay BUSINESS_PARAMETER
            } else if (card != GacatSemanticTaxonomy.Cardinality.SCALAR) {
                issue = issueOr(issue, "PERIOD_SCOPED_INGREDIENT_OR_PARAMETER");
            }
        }
        if (id.startsWith("bureau.tradeline.") && !"bureau.tradeline.payment_history".equals(id)
                && !"bureau.tradeline.suit_filed".equals(id)) {
            // field ingredients inside tradeline collection
            if (cls == GacatSemanticTaxonomy.ParameterClass.INGREDIENT) {
                issue = issueOr(issue, "FIELD_OF_TRADELINE_COLLECTION");
            }
        }
        if ("bureau.tradeline.suit_filed".equals(id)) {
            // Often used as ANY(tradeline.suit_filed) business flag in policies
            cls = GacatSemanticTaxonomy.ParameterClass.BUSINESS_PARAMETER;
            card = GacatSemanticTaxonomy.Cardinality.SCALAR;
            selectable = true;
            issue = issueOr(issue, "PER_TRADELINE_SOURCE_OFTEN_AGGREGATED_ANY; retained as policy-facing scalar");
        }
        if ("bureau.tradeline.payment_history".equals(id)) {
            cls = GacatSemanticTaxonomy.ParameterClass.INGREDIENT;
            card = GacatSemanticTaxonomy.Cardinality.HISTORY;
            mode = GacatSemanticTaxonomy.CalculationMode.RAW;
            vt = GacatSemanticTaxonomy.ValueType.HISTORY;
            unit = GacatSemanticTaxonomy.SemanticUnit.HISTORY;
            selectable = false;
        }
        if (id.startsWith("bank.transaction.") || id.startsWith("bank.account.")) {
            cls = GacatSemanticTaxonomy.ParameterClass.INGREDIENT;
            selectable = false;
            issue = issueOr(issue, "BANK_FIELD_INGREDIENT; collection bank.transactions not yet a GACAT catalogue row");
        }
        if (id.startsWith("bureau.inquiry")) {
            cls = GacatSemanticTaxonomy.ParameterClass.INGREDIENT;
            selectable = false;
            card = GacatSemanticTaxonomy.Cardinality.COLLECTION;
            issue = issueOr(issue, "INQUIRY_FIELD_OR_EVENT; use bureau.inquiries collection at execution boundary");
        }
        if (id.startsWith("bureau.scoring_element.")) {
            cls = GacatSemanticTaxonomy.ParameterClass.INGREDIENT;
            selectable = false;
            card = GacatSemanticTaxonomy.Cardinality.COLLECTION;
            issue = issueOr(issue, "SCORING_ELEMENT_FIELD; Equifax ScoringElements not CIBIL ReasonCode");
        }
        if ("bureau.recent.inquiries_90d".equals(id) || "bureau.recent_inquiries_90d".equals(id)) {
            aliasGroup = "bureau.recent_inquiries_raw_vs_derived";
            overlap = GacatSemanticTaxonomy.OverlapRelation.SEMANTICALLY_DISTINCT;
            overlapNote = "Equifax RecentActivities/TotalInquiries RAW ≠ LOS trailing-90d DERIVED count";
        }
        if ("bureau.summary.age_of_oldest_trade_months".equals(id)
                || "bureau.oldest_tradeline_vintage_months".equals(id)
                || "bureau.summary.oldest_account_narrative".equals(id)) {
            aliasGroup = "bureau.oldest_trade_raw_vs_derived";
            overlap = GacatSemanticTaxonomy.OverlapRelation.SEMANTICALLY_DISTINCT;
            overlapNote = "Equifax AgeOfOldestTrade / OldestAccount narrative RAW ≠ LOS derived vintage months";
        }
        if ("bureau.enquiry.summary.purpose".equals(id) || "bureau.inquiry.purpose".equals(id)) {
            aliasGroup = "bureau.enquiry_purpose_summary_vs_row";
            overlap = GacatSemanticTaxonomy.OverlapRelation.SEMANTICALLY_DISTINCT;
            overlapNote = "EnquirySummary/Purpose filter ≠ per-enquiry Enquiries/RequestPurpose";
        }
        if (d.unit() != null && "FLAG".equalsIgnoreCase(d.unit()) && vt == GacatSemanticTaxonomy.ValueType.BOOLEAN) {
            // normalized FLAG→BOOLEAN OK
        } else if (d.unit() != null && ambiguousUnit(d.unit())) {
            issue = issueOr(issue, "SEMANTIC_GAP_UNIT:" + d.unit());
        }

        boolean implemented = d.capability() != null && d.capability().implemented();
        boolean prodReady = d.capability() != null && d.capability().productionReady();

        return new GacatSemanticEntry(
                id,
                family,
                cls,
                card,
                mode,
                vt,
                unit,
                selectable,
                d.evaluatedFrom(),
                d.requiredPrimitives() == null ? List.of() : List.copyOf(d.requiredPrimitives()),
                d.type(),
                implemented,
                prodReady,
                issue,
                aliasGroup,
                overlap,
                overlapNote
        );
    }

    private static GacatSemanticTaxonomy.ParameterClass mapParameterClass(
            String id, CanonicalParameterDefinition d, GacatSemanticTaxonomy.Cardinality card) {
        if (id.startsWith("product.") || id.startsWith("program.") || "aa.transport.note".equals(id)) {
            return GacatSemanticTaxonomy.ParameterClass.CONFIGURATION;
        }
        if (CanonicalParameterDefinition.MANUAL.equals(d.type()) && id.startsWith("application.")) {
            return GacatSemanticTaxonomy.ParameterClass.MANUAL_INPUT;
        }
        if (isFieldIngredient(id) || card == GacatSemanticTaxonomy.Cardinality.HISTORY
                && "bureau.tradeline.payment_history".equals(id)) {
            if ("bureau.tradeline.payment_history".equals(id)) {
                return GacatSemanticTaxonomy.ParameterClass.INGREDIENT;
            }
        }
        if (isFieldIngredient(id)) {
            return GacatSemanticTaxonomy.ParameterClass.INGREDIENT;
        }
        if (CanonicalParameterDefinition.RAW.equals(d.type())
                && (card == GacatSemanticTaxonomy.Cardinality.COLLECTION
                || card == GacatSemanticTaxonomy.Cardinality.HISTORY)
                && !isBusinessFacingRaw(id)) {
            return GacatSemanticTaxonomy.ParameterClass.INGREDIENT;
        }
        // Derived aggregates, scores, KYC quality, FOIR, etc.
        return GacatSemanticTaxonomy.ParameterClass.BUSINESS_PARAMETER;
    }

    private static boolean isBusinessFacingRaw(String id) {
        return "bureau.score".equals(id)
                || "bureau.score.name".equals(id)
                || "bureau.commercial.score".equals(id)
                || "bureau.report.date".equals(id)
                || "bureau.hit_code".equals(id)
                || "bureau.success_code".equals(id)
                || "bureau.report_order_no".equals(id)
                || id.startsWith("bureau.summary.")
                || id.startsWith("bureau.enquiry.summary.")
                || id.startsWith("bureau.recent.")
                || id.startsWith("kyc.");
    }

    private static boolean isFieldIngredient(String id) {
        return id.startsWith("bureau.tradeline.")
                || id.startsWith("bureau.inquiry")
                || id.startsWith("bureau.scoring_element.")
                || id.startsWith("bureau.commercial.facility.")
                || id.startsWith("bureau.commercial.relationship.")
                || id.startsWith("bank.transaction.")
                || id.startsWith("bank.account.")
                || id.startsWith("gst.return.")
                || id.startsWith("gst.registration.")
                || "bureau.reason_code".equals(id)
                || "bureau.commercial.enquiry".equals(id)
                || "aa.account.metadata".equals(id)
                || "aa.consent.fi_types".equals(id)
                || "itr.income.total".equals(id)
                || "itr.taxable_income".equals(id)
                || id.equals("financial.revenue")
                || id.equals("financial.pat")
                || id.equals("financial.interest")
                || id.equals("financial.debt")
                || id.equals("financial.net_worth");
    }

    private static GacatSemanticTaxonomy.Cardinality mapCardinality(String id, CanonicalParameterDefinition d) {
        if ("bureau.tradeline.payment_history".equals(id)) {
            return GacatSemanticTaxonomy.Cardinality.HISTORY;
        }
        String c = d.capability() != null ? d.capability().cardinality() : null;
        if (c == null || c.isBlank() || "SCALAR".equalsIgnoreCase(c)) {
            return GacatSemanticTaxonomy.Cardinality.SCALAR;
        }
        String u = c.toUpperCase(Locale.ROOT);
        if (u.contains("PER_MONTH") || u.contains("HISTORY") || u.contains("PER_TRADELINE_PER_MONTH")) {
            return GacatSemanticTaxonomy.Cardinality.HISTORY;
        }
        if (u.startsWith("PER_") || u.equals("LIST") || u.equals("EVENT") || u.equals("OBJECT")) {
            return GacatSemanticTaxonomy.Cardinality.COLLECTION;
        }
        return GacatSemanticTaxonomy.Cardinality.SCALAR;
    }

    private static GacatSemanticTaxonomy.CalculationMode mapCalculationMode(
            String id, CanonicalParameterDefinition d, GacatSemanticTaxonomy.ParameterClass cls) {
        if (cls == GacatSemanticTaxonomy.ParameterClass.CONFIGURATION) {
            return GacatSemanticTaxonomy.CalculationMode.CONFIG;
        }
        if (cls == GacatSemanticTaxonomy.ParameterClass.DECISION_OUTPUT) {
            return GacatSemanticTaxonomy.CalculationMode.OUTPUT;
        }
        if (CanonicalParameterDefinition.MANUAL.equals(d.type())) {
            return GacatSemanticTaxonomy.CalculationMode.MANUAL;
        }
        if (CanonicalParameterDefinition.RAW.equals(d.type())) {
            return GacatSemanticTaxonomy.CalculationMode.RAW;
        }
        if (BUILT_IN_IDS.contains(id)) {
            return GacatSemanticTaxonomy.CalculationMode.BUILT_IN;
        }
        if (AUTHORED_INTENT.contains(id)) {
            return GacatSemanticTaxonomy.CalculationMode.AUTHORED;
        }
        if (CanonicalParameterDefinition.DERIVED.equals(d.type())) {
            // Default derived without known built-in → authored design intent
            return GacatSemanticTaxonomy.CalculationMode.AUTHORED;
        }
        return GacatSemanticTaxonomy.CalculationMode.UNKNOWN;
    }

    private static GacatSemanticTaxonomy.ValueType mapValueType(
            String id, CanonicalParameterDefinition d, GacatSemanticTaxonomy.Cardinality card) {
        if (card == GacatSemanticTaxonomy.Cardinality.HISTORY
                || "HISTORY".equalsIgnoreCase(d.unit())) {
            return GacatSemanticTaxonomy.ValueType.HISTORY;
        }
        if (card == GacatSemanticTaxonomy.Cardinality.COLLECTION
                && (d.unit() == null || "EVENT".equalsIgnoreCase(d.unit()) || "OBJECT".equalsIgnoreCase(d.unit())
                || "LIST".equalsIgnoreCase(d.unit()) || "TEXT".equalsIgnoreCase(d.unit()))) {
            if ("OBJECT".equalsIgnoreCase(d.unit())) return GacatSemanticTaxonomy.ValueType.RECORD;
            if (isFieldIngredient(id) && d.unit() != null) {
                return unitToValueType(d.unit());
            }
            return GacatSemanticTaxonomy.ValueType.COLLECTION;
        }
        return unitToValueType(d.unit());
    }

    private static GacatSemanticTaxonomy.ValueType unitToValueType(String unit) {
        if (unit == null) return GacatSemanticTaxonomy.ValueType.UNKNOWN;
        String u = unit.toUpperCase(Locale.ROOT);
        return switch (u) {
            case "INR", "MONEY", "AMOUNT" -> GacatSemanticTaxonomy.ValueType.MONEY;
            case "PERCENT", "RATIO" -> GacatSemanticTaxonomy.ValueType.PERCENT;
            case "COUNT", "INTEGER", "N", "SCORE", "DAYS", "MONTHS" -> GacatSemanticTaxonomy.ValueType.INTEGER;
            case "BOOLEAN", "FLAG" -> GacatSemanticTaxonomy.ValueType.BOOLEAN;
            case "DATE" -> GacatSemanticTaxonomy.ValueType.DATE;
            case "CODE", "ENUM" -> GacatSemanticTaxonomy.ValueType.ENUM;
            case "TEXT", "STRING" -> GacatSemanticTaxonomy.ValueType.STRING;
            case "HISTORY" -> GacatSemanticTaxonomy.ValueType.HISTORY;
            case "EVENT", "OBJECT", "LIST" -> GacatSemanticTaxonomy.ValueType.COLLECTION;
            case "DECIMAL" -> GacatSemanticTaxonomy.ValueType.DECIMAL;
            default -> GacatSemanticTaxonomy.ValueType.UNKNOWN;
        };
    }

    private static GacatSemanticTaxonomy.SemanticUnit mapUnit(String raw, GacatSemanticTaxonomy.ValueType vt) {
        if (raw == null) {
            return switch (vt) {
                case BOOLEAN -> GacatSemanticTaxonomy.SemanticUnit.BOOLEAN;
                case HISTORY -> GacatSemanticTaxonomy.SemanticUnit.HISTORY;
                case MONEY -> GacatSemanticTaxonomy.SemanticUnit.MONEY;
                default -> GacatSemanticTaxonomy.SemanticUnit.UNKNOWN;
            };
        }
        String u = raw.toUpperCase(Locale.ROOT);
        return switch (u) {
            case "INR", "MONEY", "AMOUNT" -> GacatSemanticTaxonomy.SemanticUnit.MONEY;
            case "COUNT", "N", "INTEGER" -> GacatSemanticTaxonomy.SemanticUnit.COUNT;
            case "DAYS" -> GacatSemanticTaxonomy.SemanticUnit.DAYS;
            case "MONTHS" -> GacatSemanticTaxonomy.SemanticUnit.MONTHS;
            case "PERCENT" -> GacatSemanticTaxonomy.SemanticUnit.PERCENT;
            case "RATIO" -> GacatSemanticTaxonomy.SemanticUnit.RATIO;
            case "BOOLEAN", "FLAG" -> GacatSemanticTaxonomy.SemanticUnit.BOOLEAN;
            case "SCORE" -> GacatSemanticTaxonomy.SemanticUnit.SCORE;
            case "HISTORY" -> GacatSemanticTaxonomy.SemanticUnit.HISTORY;
            case "CODE", "ENUM" -> GacatSemanticTaxonomy.SemanticUnit.CODE;
            case "TEXT", "STRING" -> GacatSemanticTaxonomy.SemanticUnit.TEXT;
            case "DATE" -> GacatSemanticTaxonomy.SemanticUnit.DATE;
            case "EVENT", "OBJECT", "LIST" -> GacatSemanticTaxonomy.SemanticUnit.NONE;
            default -> GacatSemanticTaxonomy.SemanticUnit.UNKNOWN;
        };
    }

    private static boolean defaultSelectable(GacatSemanticTaxonomy.ParameterClass cls) {
        return cls == GacatSemanticTaxonomy.ParameterClass.BUSINESS_PARAMETER
                || cls == GacatSemanticTaxonomy.ParameterClass.MANUAL_INPUT;
    }

    private static boolean ambiguousUnit(String unit) {
        String u = unit.toUpperCase(Locale.ROOT);
        return "EVENT".equals(u) || "OBJECT".equals(u) || "LIST".equals(u) || "CODE".equals(u);
    }

    private static String familyOf(String id, String evaluatedFrom) {
        if (id.startsWith("bureau.commercial")) return "BUREAU_COMMERCIAL";
        if (id.startsWith("bureau.")) return "BUREAU_RETAIL";
        if (id.startsWith("bank.") || id.startsWith("banking.")) return "BANKING";
        if (id.startsWith("gst.")) return "GST";
        if (id.startsWith("itr.") || id.startsWith("financial.")) return "FINANCIAL_ITR";
        if (id.startsWith("kyc.")) return "KYC";
        if (id.startsWith("application.")) return "APPLICATION";
        if (id.startsWith("product.") || id.startsWith("program.")) return "PROGRAM_PRODUCT";
        if (id.startsWith("aa.")) return "ACCOUNT_AGGREGATOR";
        if (id.startsWith("obligation.") || id.startsWith("collateral.")) return "COMPUTED";
        if (evaluatedFrom != null) return evaluatedFrom.toUpperCase(Locale.ROOT).replace(' ', '_');
        return "OTHER";
    }

    private static String issueOr(String existing, String next) {
        if (existing == null || existing.isBlank()) return next;
        return existing + ";" + next;
    }

    @SafeVarargs
    private static Set<String> union(Set<String>... sets) {
        java.util.HashSet<String> s = new java.util.HashSet<>();
        for (Set<String> x : sets) s.addAll(x);
        return s;
    }

    public static List<Map<String, Object>> knownOverlaps() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(overlap("bureau.status_ntc", "bureau.thin_file_indicator",
                GacatSemanticTaxonomy.OverlapRelation.OVERLAPPING_CONCEPT,
                "NTC vs thin-file indicator"));
        rows.add(overlap("bureau.recent_inquiries_90d", "bureau.inquiries.last_3m",
                GacatSemanticTaxonomy.OverlapRelation.SEMANTICALLY_DISTINCT,
                "90d rolling window ≠ last 3 calendar months"));
        rows.add(overlap("bureau.recent_inquiries_90d", "BUREAU_ENQUIRIES_3M",
                GacatSemanticTaxonomy.OverlapRelation.DEPRECATED_ALIAS,
                "Dangerous legacy alias — rejected for reverse materialization"));
        rows.add(overlap("bureau.cc_overdue_amount", "bureau.overdue.amount",
                GacatSemanticTaxonomy.OverlapRelation.SEMANTICALLY_DISTINCT,
                "Credit-card overdue ≠ total overdue"));
        rows.add(overlap("bureau.credit_after_overdue.clean_history_months",
                "bureau.months_since_last_delinquency",
                GacatSemanticTaxonomy.OverlapRelation.SEMANTICALLY_DISTINCT,
                "Clean streak after overdue ≠ months since last DQ"));
        rows.add(overlap("kyc.pan.verified", "kyc.pan_verified",
                GacatSemanticTaxonomy.OverlapRelation.TRUE_ALIAS,
                "Snake vs dotted path — TRUE_COMPAT"));
        rows.add(overlap("bureau.tradeline.*", CanonicalFactMaterializer.TRADELINES,
                GacatSemanticTaxonomy.OverlapRelation.NEEDS_REVIEW,
                "Field IDs vs Wave-3 collection fact key bureau.tradelines (execution, not catalogue rename)"));
        rows.add(overlap("itr.income.total", "financial.revenue",
                GacatSemanticTaxonomy.OverlapRelation.OVERLAPPING_CONCEPT,
                "ITR total income vs financial statement revenue — related but not identical"));
        rows.add(overlap("aa.transport.note", "aa.*",
                GacatSemanticTaxonomy.OverlapRelation.NEEDS_REVIEW,
                "AA is transport/consent — not borrower observation facts"));
        return rows;
    }

    private static Map<String, Object> overlap(String a, String b,
                                               GacatSemanticTaxonomy.OverlapRelation rel, String note) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("left", a);
        m.put("right", b);
        m.put("relation", rel.name());
        m.put("note", note);
        return m;
    }
}
