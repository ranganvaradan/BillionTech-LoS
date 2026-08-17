package com.los.core.creditintelligence.policystudio.sourceintegration;

import java.util.Set;

/**
 * Platform fields known to be produced by real connector normalization paths.
 * Not inferred from GACAT catalogue membership alone.
 *
 * <p>Evidence: EquifaxBureauAccountExtractor + BureauNormalizationService persist
 * tradelines / payment history / inquiries with these semantic fields.
 */
public final class PlatformNormalizedRawFieldCatalog {

    private PlatformNormalizedRawFieldCatalog() {}

    /**
     * Exact canonical IDs for which Equifax retail normalization has a durable field path.
     * Structural readiness may be READY while a given applicant still has valueAvailable=false.
     */
    public static final Set<String> EQUIFAX_RETAIL_NORMALIZED_FIELDS = Set.of(
            "bureau.score",
            "bureau.tradelines",
            "bureau.inquiries",
            "bureau.inquiry",
            "bureau.inquiry.date",
            "bureau.inquiry.purpose",
            "bureau.inquiry.amount",
            "bureau.inquiry.member",
            "bureau.tradeline.account_type",
            "bureau.tradeline.ownership",
            "bureau.tradeline.lender",
            "bureau.tradeline.account_open_date",
            "bureau.tradeline.account_close_date",
            "bureau.tradeline.date_reported",
            "bureau.tradeline.sanction_amount",
            "bureau.tradeline.current_balance",
            "bureau.tradeline.overdue_amount",
            "bureau.tradeline.credit_limit",
            "bureau.tradeline.emi",
            "bureau.tradeline.tenure_months",
            "bureau.tradeline.interest_rate",
            "bureau.tradeline.account_status",
            "bureau.tradeline.write_off_amount",
            "bureau.tradeline.settlement_amount",
            "bureau.tradeline.suit_filed",
            "bureau.tradeline.wilful_default",
            "bureau.tradeline.asset_classification",
            "bureau.tradeline.collateral_type",
            "bureau.tradeline.collateral_value",
            "bureau.tradeline.payment_history",
            "bureau.tradeline.payment_status_month",
            "bureau.tradeline.dpd_month",
            "bureau.tradeline.secured_flag"
    );

    public static boolean isStructurallyMapped(String canonicalParameterId) {
        if (canonicalParameterId == null || canonicalParameterId.isBlank()) {
            return false;
        }
        return EQUIFAX_RETAIL_NORMALIZED_FIELDS.contains(canonicalParameterId.trim());
    }

    /** Merge into RawFactProducer registration — CPES capability tracks structural mapping. */
    public static Set<String> mergeIntoRawFactIds(Set<String> existing) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        if (existing != null) out.addAll(existing);
        out.addAll(EQUIFAX_RETAIL_NORMALIZED_FIELDS);
        return Set.copyOf(out);
    }
}
