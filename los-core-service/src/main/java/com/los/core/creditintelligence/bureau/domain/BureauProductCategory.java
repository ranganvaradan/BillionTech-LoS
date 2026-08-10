package com.los.core.creditintelligence.bureau.domain;

/**
 * Canonical bureau product taxonomy (EQUIFAX_TAXONOMY_V1 + UNKNOWN).
 * UNKNOWN must not count as unsecured.
 */
public enum BureauProductCategory {
    AUTO_LOAN,
    HOME_LOAN,
    LAP,
    PERSONAL_LOAN,
    CONSUMER_DURABLE,
    GOLD_LOAN,
    EDUCATION_LOAN,
    CREDIT_CARD,
    OVERDRAFT,
    CASH_CREDIT,
    BUSINESS_LOAN_SECURED,
    BUSINESS_LOAN_UNSECURED,
    WORKING_CAPITAL_SECURED,
    WORKING_CAPITAL_UNSECURED,
    TERM_LOAN_SECURED,
    TERM_LOAN_UNSECURED,
    COMMERCIAL_VEHICLE,
    AGRICULTURE_LOAN,
    MICROFINANCE,
    GUARANTEE,
    OTHER_SECURED,
    OTHER_UNSECURED,
    UNKNOWN
}
