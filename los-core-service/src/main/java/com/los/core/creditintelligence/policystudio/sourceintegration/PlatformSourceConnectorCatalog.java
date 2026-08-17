package com.los.core.creditintelligence.policystudio.sourceintegration;

import java.util.List;
import java.util.Locale;

/**
 * GOLDEN-PARAMETER-TRUTH — platform connector catalogue.
 * Code-shipped durable inventory of what BillionTech has as a real connector path.
 * NOT derived from GACAT catalogue parameter existence.
 */
public final class PlatformSourceConnectorCatalog {

    public enum SourceKey {
        BUREAU_RETAIL,
        BUREAU_COMMERCIAL,
        BANK_STATEMENT,
        ACCOUNT_AGGREGATOR,
        GST,
        FINANCIAL_ITR,
        KYC,
        APPLICATION,
        INTERNAL,
        UNKNOWN
    }

    public record PlatformEntry(
            SourceKey key,
            String displayName,
            boolean platformIntegrated,
            boolean notApplicable,
            List<String> evidence) {}

    private PlatformSourceConnectorCatalog() {}

    public static PlatformEntry entry(SourceKey key) {
        return switch (key) {
            case BUREAU_RETAIL -> new PlatformEntry(
                    key, "Equifax Bureau Retail", true, false,
                    List.of(
                            "EquifaxBureauProvider",
                            "BureauNormalizationService",
                            "Workflow step BUREAU_PULL",
                            "aggregator_routing provider EQUIFAX/BUREAU"));
            case BUREAU_COMMERCIAL -> new PlatformEntry(
                    key, "Commercial Bureau", false, false,
                    List.of(
                            "No production commercial-bureau connector certified",
                            "Commercial GACAT rows are catalogue/fixture evidence only"));
            case BANK_STATEMENT -> new PlatformEntry(
                    key, "Bank Statement", true, false,
                    List.of(
                            "BankingMetricService",
                            "BankAverageDailyBalanceCalculator",
                            "BANK_STATEMENT_DOCUMENT acquisition"));
            case ACCOUNT_AGGREGATOR -> new PlatformEntry(
                    key, "Account Aggregator", true, false,
                    List.of("ACCOUNT_AGGREGATOR workflow integration"));
            case GST -> new PlatformEntry(
                    key, "GST", true, false,
                    List.of("GstMetricService", "GST registration/return bindings"));
            case FINANCIAL_ITR -> new PlatformEntry(
                    key, "Financial Statements / ITR", false, false,
                    List.of("No production ITR/financial-statements connector certified"));
            case KYC -> new PlatformEntry(
                    key, "KYC", true, false,
                    List.of("KYC workflow steps", "aggregator_routing / aggregator_configs matrix"));
            case APPLICATION -> new PlatformEntry(
                    key, "Application / Manual", false, true,
                    List.of("Not an external provider integration"));
            case INTERNAL -> new PlatformEntry(
                    key, "Internal / derived", false, true,
                    List.of("Internal computed / obligation / collateral — not a provider source"));
            case UNKNOWN -> new PlatformEntry(
                    key, "Unknown source", false, false,
                    List.of("No platform connector registered for this source family"));
        };
    }

    /**
     * Map a GACAT source-family / evaluatedFrom label to a connector key.
     * Mapping lineage ≠ inventing integration from catalogue membership.
     */
    public static SourceKey resolveKey(String familyOrSource, String sourceType) {
        String f = familyOrSource == null ? "" : familyOrSource.toLowerCase(Locale.ROOT);
        String st = sourceType == null ? "" : sourceType.toUpperCase(Locale.ROOT);
        if (st.contains("APPLICATION") || st.contains("MANUAL")
                || f.contains("application") || f.contains("program") || f.contains("product")
                || f.contains("manual")) {
            return SourceKey.APPLICATION;
        }
        // INTERNAL is a *source family*, not a parameter semantic. Do NOT map type=DERIVED
        // to INTERNAL — bureau.dpd_* remains Equifax/Bureau Retail even when authored/derived.
        if (f.contains("computed") || f.contains("obligation") || f.contains("collateral")
                || f.equals("derived") || f.equals("internal")
                || f.startsWith("derived.") || f.startsWith("internal.")
                || f.contains("computed/") || f.contains("/computed")) {
            return SourceKey.INTERNAL;
        }
        if (f.contains("bureau commercial") || (f.contains("commercial") && f.contains("bureau"))) {
            return SourceKey.BUREAU_COMMERCIAL;
        }
        if (f.contains("bureau") || f.contains("equifax") || f.startsWith("bureau.")) {
            return SourceKey.BUREAU_RETAIL;
        }
        if (f.contains("account aggregator") || f.equals("aa") || f.startsWith("aa.")
                || f.contains("account_aggregator")) {
            return SourceKey.ACCOUNT_AGGREGATOR;
        }
        if (f.contains("bank") || f.contains("banking") || f.startsWith("bank.")
                || f.startsWith("banking.")) {
            return SourceKey.BANK_STATEMENT;
        }
        if (f.contains("gst") || f.startsWith("gst.")) {
            return SourceKey.GST;
        }
        if (f.contains("financial") || f.contains("itr") || f.startsWith("financial.")
                || f.startsWith("itr.")) {
            return SourceKey.FINANCIAL_ITR;
        }
        if (f.contains("kyc") || f.startsWith("kyc.")) {
            return SourceKey.KYC;
        }
        if (f.isBlank()) {
            return SourceKey.UNKNOWN;
        }
        return SourceKey.UNKNOWN;
    }
}
