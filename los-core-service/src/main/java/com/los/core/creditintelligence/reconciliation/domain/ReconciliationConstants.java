package com.los.core.creditintelligence.reconciliation.domain;

/**
 * Versioned method identifiers for Phase C5 reconciliation engine.
 */
public final class ReconciliationConstants {

    public static final String PERIOD_ALIGNMENT_V1 = "PERIOD_ALIGNMENT_V1";
    public static final String RECON_EXPLANATION_V1 = "RECON_EXPLANATION_V1";
    public static final String TURNOVER_TRIANGULATION_V1 = "TURNOVER_TRIANGULATION_V1";
    public static final String EVIDENCE_STRENGTH_V1 = "EVIDENCE_STRENGTH_V1";
    public static final String CONFIDENCE_METHOD_V1 = "CONFIDENCE_METHOD_V1";
    public static final String CREDIT_EVIDENCE_SUMMARY_V1 = "CREDIT_EVIDENCE_SUMMARY_V1";

    public static final String XSRC_GST_ITR_TURNOVER = "XSRC_GST_ITR_TURNOVER";
    public static final String XSRC_GST_BANK_TURNOVER = "XSRC_GST_BANK_TURNOVER";
    public static final String XSRC_ITR_BANK_TURNOVER = "XSRC_ITR_BANK_TURNOVER";
    public static final String XSRC_BUREAU_BANK_OBLIGATION = "XSRC_BUREAU_BANK_OBLIGATION";
    public static final String XSRC_DECLARED_BUREAU_OBLIGATION = "XSRC_DECLARED_BUREAU_OBLIGATION";
    public static final String XSRC_DECLARED_BANK_OBLIGATION = "XSRC_DECLARED_BANK_OBLIGATION";
    public static final String XSRC_ITR_AIS_INCOME = "XSRC_ITR_AIS_INCOME";
    public static final String XSRC_ITR_26AS_TDS = "XSRC_ITR_26AS_TDS";
    public static final String XSRC_GSTR1_GSTR3B_TURNOVER = "XSRC_GSTR1_GSTR3B_TURNOVER";
    public static final String TURNOVER_TRIANGULATION = "TURNOVER_TRIANGULATION";

    public static final String METRIC_EVIDENCE_STRENGTH = "credit.evidence_strength_score";
    public static final String METRIC_TURNOVER_TRIANGULATION = "reconciliation.turnover_triangulation";

    private ReconciliationConstants() {
    }
}
