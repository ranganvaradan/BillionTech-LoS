package com.los.core.customercategory;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Exact approved Day-1 matrix from CUSTOMER-CATEGORY-DAY1-SEED-PREVIEW-2.
 * Business authority for apply — not derived from the 36-row raw seed.
 */
public final class Day1ApprovedSeedCatalog {

    public static final String LEGACY_PROP_TL_RS = "c1000000-0000-0000-0000-000000000004";
    public static final String MSME_PROP_SC = "630c977a-ce56-41cb-8515-d91c3b6899b6";
    public static final String SME_COMPANY_SC = "3487a0ab-8712-4245-b0f9-73e270bb1fa6";

    public static final List<String> EXCLUDED_PRODUCTS = List.of(
            "PERSONAL_LOAN",
            "BUSINESS_WC_OD",
            "LOAN_AGAINST_GOLD",
            "LOAN_AGAINST_PROPERTY",
            "LOAN_AGAINST_SECURITIES");

    public record Day1PolicySetSpec(
            String code,
            String name,
            UUID primaryRuleSetId,
            UUID scorecardId
    ) {}

    public record Day1CategorySpec(
            String code,
            String name,
            String borrowerType,
            String loanProduct,
            String intakeSegment,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String policySetCode,
            UUID workflowId
    ) {}

    private static final BigDecimal A50K = new BigDecimal("50000.00");
    private static final BigDecimal A5CR = new BigDecimal("50000000.00");
    private static final BigDecimal A1CR = new BigDecimal("10000000.00");

    /** 12 Policy Sets — invoice BORROWER+ANCHOR share one PS per borrower type. */
    public static final List<Day1PolicySetSpec> POLICY_SETS = List.of(
            ps("PS_DAY1_INDIVIDUAL_TL", "Day-1 Policy Set · INDIVIDUAL · TERM_LOAN",
                    "c3320000-0000-4000-a000-000000000005", "d3320000-0000-4000-a000-000000000005"),
            ps("PS_DAY1_INDIVIDUAL_BTL", "Day-1 Policy Set · INDIVIDUAL · BUSINESS_TERM_LOAN",
                    "c3320000-0000-4000-a000-000000000002", "d3320000-0000-4000-a000-000000000002"),
            ps("PS_DAY1_INDIVIDUAL_INV", "Day-1 Policy Set · INDIVIDUAL · Invoice Discounting",
                    "1ebe7b66-e4ee-4908-a0db-9e8da51a6431", "631723b7-8e8c-4be1-ada7-4d388b947cf6"),
            ps("PS_DAY1_PROPRIETOR_TL", "Day-1 Policy Set · PROPRIETOR · TERM_LOAN",
                    "c3320000-0000-4000-a000-000000000013", "d3320000-0000-4000-a000-000000000013"),
            ps("PS_DAY1_PROPRIETOR_BTL", "Day-1 Policy Set · PROPRIETOR · BUSINESS_TERM_LOAN",
                    "c3320000-0000-4000-a000-000000000010", "d3320000-0000-4000-a000-000000000010"),
            ps("PS_DAY1_PROPRIETOR_INV", "Day-1 Policy Set · PROPRIETOR · Invoice Discounting",
                    "1942e7db-3772-48d8-ac04-5a3264b28751", "19f93da1-24a7-4903-a1bd-3316bf24dc8b"),
            ps("PS_DAY1_PARTNERSHIP_TL", "Day-1 Policy Set · PARTNERSHIP · TERM_LOAN",
                    "c3320000-0000-4000-a000-000000000021", "d3320000-0000-4000-a000-000000000021"),
            ps("PS_DAY1_PARTNERSHIP_BTL", "Day-1 Policy Set · PARTNERSHIP · BUSINESS_TERM_LOAN",
                    "c3320000-0000-4000-a000-000000000018", "d3320000-0000-4000-a000-000000000018"),
            ps("PS_DAY1_PARTNERSHIP_INV", "Day-1 Policy Set · PARTNERSHIP · Invoice Discounting",
                    "a4f13aa6-35a6-4785-a6d6-d7f3668de6b2", "f3c906b5-1c61-4d2a-904e-084f81bb37cb"),
            ps("PS_DAY1_COMPANY_TL", "Day-1 Policy Set · COMPANY · TERM_LOAN",
                    "c3320000-0000-4000-a000-000000000029", "358c83f1-420b-4bdb-be40-78259367238e"),
            ps("PS_DAY1_COMPANY_BTL", "Day-1 Policy Set · COMPANY · BUSINESS_TERM_LOAN",
                    "c3320000-0000-4000-a000-000000000026", "d3320000-0000-4000-a000-000000000026"),
            ps("PS_DAY1_COMPANY_INV", "Day-1 Policy Set · COMPANY · Invoice Discounting",
                    "ddcfd859-6ecb-40b6-aa82-c7bfaa6e49a7", "00270be1-5224-4874-999c-d297d224e357")
    );

    /** Exactly 15 categories. */
    public static final List<Day1CategorySpec> CATEGORIES = List.of(
            cat("CC_DAY1_INDIVIDUAL_TERM_LOAN_BORROWER", "INDIVIDUAL · TERM_LOAN · BORROWER",
                    "INDIVIDUAL", "TERM_LOAN", "BORROWER", A50K, A5CR, "PS_DAY1_INDIVIDUAL_TL",
                    "b0000000-0000-0000-0000-000000000001"),
            cat("CC_DAY1_INDIVIDUAL_BUSINESS_TERM_LOAN_BORROWER", "INDIVIDUAL · BUSINESS_TERM_LOAN · BORROWER",
                    "INDIVIDUAL", "BUSINESS_TERM_LOAN", "BORROWER", A50K, A5CR, "PS_DAY1_INDIVIDUAL_BTL",
                    "b0000000-0000-0000-0000-000000000011"),
            cat("CC_DAY1_INDIVIDUAL_BUSINESS_WC_INVOICE_DISCOUNTING_BORROWER",
                    "INDIVIDUAL · BUSINESS_WC_INVOICE_DISCOUNTING · BORROWER",
                    "INDIVIDUAL", "BUSINESS_WC_INVOICE_DISCOUNTING", "BORROWER", A50K, A1CR, "PS_DAY1_INDIVIDUAL_INV",
                    "f3330000-0000-4000-a000-000000000004"),
            cat("CC_DAY1_PROPRIETOR_TERM_LOAN_BORROWER", "PROPRIETOR · TERM_LOAN · BORROWER",
                    "PROPRIETOR", "TERM_LOAN", "BORROWER", A50K, A5CR, "PS_DAY1_PROPRIETOR_TL",
                    "b0000000-0000-0000-0000-000000000021"),
            cat("CC_DAY1_PROPRIETOR_BUSINESS_TERM_LOAN_BORROWER", "PROPRIETOR · BUSINESS_TERM_LOAN · BORROWER",
                    "PROPRIETOR", "BUSINESS_TERM_LOAN", "BORROWER", A50K, A5CR, "PS_DAY1_PROPRIETOR_BTL",
                    "b0000000-0000-0000-0000-000000000002"),
            cat("CC_DAY1_PROPRIETOR_BUSINESS_WC_INVOICE_DISCOUNTING_BORROWER",
                    "PROPRIETOR · BUSINESS_WC_INVOICE_DISCOUNTING · BORROWER",
                    "PROPRIETOR", "BUSINESS_WC_INVOICE_DISCOUNTING", "BORROWER", A50K, A1CR, "PS_DAY1_PROPRIETOR_INV",
                    "f3330000-0000-4000-a000-000000000012"),
            cat("CC_DAY1_PROPRIETOR_BUSINESS_WC_INVOICE_DISCOUNTING_ANCHOR",
                    "PROPRIETOR · BUSINESS_WC_INVOICE_DISCOUNTING · ANCHOR",
                    "PROPRIETOR", "BUSINESS_WC_INVOICE_DISCOUNTING", "ANCHOR", A50K, A1CR, "PS_DAY1_PROPRIETOR_INV",
                    "a0000001-0000-4000-a000-000000000001"),
            cat("CC_DAY1_PARTNERSHIP_TERM_LOAN_BORROWER", "PARTNERSHIP · TERM_LOAN · BORROWER",
                    "PARTNERSHIP", "TERM_LOAN", "BORROWER", A50K, A5CR, "PS_DAY1_PARTNERSHIP_TL",
                    "b0000000-0000-0000-0000-000000000031"),
            cat("CC_DAY1_PARTNERSHIP_BUSINESS_TERM_LOAN_BORROWER", "PARTNERSHIP · BUSINESS_TERM_LOAN · BORROWER",
                    "PARTNERSHIP", "BUSINESS_TERM_LOAN", "BORROWER", A50K, A5CR, "PS_DAY1_PARTNERSHIP_BTL",
                    "b0000000-0000-0000-0000-000000000032"),
            cat("CC_DAY1_PARTNERSHIP_BUSINESS_WC_INVOICE_DISCOUNTING_BORROWER",
                    "PARTNERSHIP · BUSINESS_WC_INVOICE_DISCOUNTING · BORROWER",
                    "PARTNERSHIP", "BUSINESS_WC_INVOICE_DISCOUNTING", "BORROWER", A50K, A1CR, "PS_DAY1_PARTNERSHIP_INV",
                    "f3330000-0000-4000-a000-000000000020"),
            cat("CC_DAY1_PARTNERSHIP_BUSINESS_WC_INVOICE_DISCOUNTING_ANCHOR",
                    "PARTNERSHIP · BUSINESS_WC_INVOICE_DISCOUNTING · ANCHOR",
                    "PARTNERSHIP", "BUSINESS_WC_INVOICE_DISCOUNTING", "ANCHOR", A50K, A1CR, "PS_DAY1_PARTNERSHIP_INV",
                    "a0000001-0000-4000-a000-000000000002"),
            cat("CC_DAY1_COMPANY_TERM_LOAN_BORROWER", "COMPANY · TERM_LOAN · BORROWER",
                    "COMPANY", "TERM_LOAN", "BORROWER", A50K, A5CR, "PS_DAY1_COMPANY_TL",
                    "b0000000-0000-0000-0000-000000000041"),
            cat("CC_DAY1_COMPANY_BUSINESS_TERM_LOAN_BORROWER", "COMPANY · BUSINESS_TERM_LOAN · BORROWER",
                    "COMPANY", "BUSINESS_TERM_LOAN", "BORROWER", A50K, A5CR, "PS_DAY1_COMPANY_BTL",
                    "b0000000-0000-0000-0000-000000000003"),
            cat("CC_DAY1_COMPANY_BUSINESS_WC_INVOICE_DISCOUNTING_BORROWER",
                    "COMPANY · BUSINESS_WC_INVOICE_DISCOUNTING · BORROWER",
                    "COMPANY", "BUSINESS_WC_INVOICE_DISCOUNTING", "BORROWER", A50K, A1CR, "PS_DAY1_COMPANY_INV",
                    "f3330000-0000-4000-a000-000000000028"),
            cat("CC_DAY1_COMPANY_BUSINESS_WC_INVOICE_DISCOUNTING_ANCHOR",
                    "COMPANY · BUSINESS_WC_INVOICE_DISCOUNTING · ANCHOR",
                    "COMPANY", "BUSINESS_WC_INVOICE_DISCOUNTING", "ANCHOR", A50K, A1CR, "PS_DAY1_COMPANY_INV",
                    "a0000001-0000-4000-a000-000000000003")
    );

    private Day1ApprovedSeedCatalog() {}

    private static Day1PolicySetSpec ps(String code, String name, String rs, String sc) {
        return new Day1PolicySetSpec(code, name, UUID.fromString(rs), UUID.fromString(sc));
    }

    private static Day1CategorySpec cat(
            String code, String name, String bt, String lp, String intake,
            BigDecimal min, BigDecimal max, String psCode, String wf) {
        return new Day1CategorySpec(code, name, bt, lp, intake, min, max, psCode, UUID.fromString(wf));
    }
}
