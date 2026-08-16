package com.los.core.creditintelligence.policystudio.parameters.semantic;

/**
 * Wave-4 GACAT semantic taxonomy. Descriptive metadata only — not CPES capability.
 */
public final class GacatSemanticTaxonomy {

    private GacatSemanticTaxonomy() {}

    public enum ParameterClass {
        INGREDIENT,
        BUSINESS_PARAMETER,
        MANUAL_INPUT,
        CONFIGURATION,
        DECISION_OUTPUT,
        UNKNOWN
    }

    public enum Cardinality {
        SCALAR,
        COLLECTION,
        HISTORY,
        UNKNOWN
    }

    /** Semantic calculation mode — does not imply CPES executability. */
    public enum CalculationMode {
        RAW,
        BUILT_IN,
        AUTHORED,
        MANUAL,
        CONFIG,
        OUTPUT,
        UNKNOWN
    }

    public enum ValueType {
        INTEGER,
        DECIMAL,
        MONEY,
        PERCENT,
        BOOLEAN,
        STRING,
        ENUM,
        DATE,
        YEAR_MONTH,
        RECORD,
        COLLECTION,
        HISTORY,
        UNKNOWN
    }

    public enum SemanticUnit {
        COUNT,
        MONEY,
        DAYS,
        MONTHS,
        PERCENT,
        RATIO,
        BOOLEAN,
        SCORE,
        NONE,
        HISTORY,
        CODE,
        TEXT,
        DATE,
        UNKNOWN
    }

    public enum OverlapRelation {
        TRUE_ALIAS,
        OVERLAPPING_CONCEPT,
        SEMANTICALLY_DISTINCT,
        DEPRECATED_ALIAS,
        NEEDS_REVIEW,
        NONE
    }

    /** Catalogue semantic contract version for future decision-package pinning. */
    public static final String SEMANTIC_VERSION = "GACAT-SEMANTIC-4.0.0";
}
