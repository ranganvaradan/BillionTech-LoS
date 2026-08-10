package com.los.core.creditintelligence.policystudio.lifecycle;

import com.los.core.model.enums.BorrowerType;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Credit Manager–facing policy scope helpers (POLICY-UX-2B).
 * Reuses existing applicability dimensions — no new routing schema.
 */
public final class PolicyScopeSupport {

    private static final Locale EN_IN = Locale.forLanguageTag("en-IN");

    private PolicyScopeSupport() {}

    public static Map<String, Object> scopeOptions() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("includeOnly", true);
        out.put("excludeSupported", false);
        out.put("products", productOptions());
        out.put("borrowerTypes", enumOptions(BorrowerType.values(), Map.of(
                "INDIVIDUAL", "Individual",
                "PROPRIETOR", "Proprietor",
                "PARTNERSHIP", "Partnership",
                "COMPANY", "Company"
        )));
        out.put("customerSegments", List.of(
                Map.of("value", "BORROWER", "label", "Borrower",
                        "note", "Intake relationship — not commercial MSME/Corporate segment"),
                Map.of("value", "ANCHOR", "label", "Anchor",
                        "note", "Intake relationship — invoice-discounting anchor onboarding")
        ));
        out.put("customerSegmentFieldMeaning",
                "Application relationship (IntakeSegment: BORROWER | ANCHOR). "
                        + "Not a commercial customer-segment taxonomy.");
        out.put("advanced", Map.of(
                "facilityType", Map.of(
                        "exposed", false,
                        "reason", "LoanApplication has no reliable facilityType field"),
                "securedUnsecured", Map.of(
                        "exposed", false,
                        "reason", "LoanApplication has no reliable secured/unsecured field"),
                "programScheme", Map.of(
                        "exposed", false,
                        "reason", "subProgramId exists but programScheme code is not joined — leave All")
        ));
        out.put("allowCanonicalAuthority", false);
        return out;
    }

    public static Map<String, Object> mappingReliability() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("PRODUCT", dim("loanProduct", "productCode", "null → product-constrained policies do not match", true));
        out.put("BORROWER_TYPE", dim("borrowerType (enum)", "borrowerType", "null → borrower-constrained policies do not match", true));
        out.put("CUSTOMER_SEGMENT", dim("intakeSegment (BORROWER|ANCHOR)", "customerSegment",
                "null → relationship-constrained policies do not match; field is intake role not MSME/Corporate", true));
        out.put("FACILITY_TYPE", dim("(none)", "facilityType", "always null — do not expose for routing", false));
        out.put("SECURED_UNSECURED", dim("(none)", "securedUnsecured", "always null — do not expose for routing", false));
        out.put("PROGRAM_SCHEME", dim("subProgramId (UUID only)", "programScheme",
                "code not mapped without join — do not expose", false));
        out.put("REQUESTED_AMOUNT", dim("requestedAmount", "loanAmount",
                "null → amount-banded policies do not match", true));
        out.put("allowCanonicalAuthority", false);
        return out;
    }

    public static Map<String, Object> summarize(Map<String, Object> applicability) {
        Map<String, Object> app = applicability == null ? Map.of() : applicability;
        List<String> products = stringList(app.get("products"));
        String productPart = products.isEmpty() || isAllProducts(products)
                ? "All products"
                : products.stream().map(PolicyScopeSupport::productLabel).reduce((a, b) -> a + ", " + b).orElse("All products");

        String borrower = blankToNull(str(app.get("borrowerType")));
        String borrowerPart = borrower == null ? "All borrower types" : borrowerTypeLabel(borrower);

        String segment = blankToNull(str(app.get("customerSegment")));
        String segmentPart = segment == null ? null : relationshipLabel(segment);

        String amountPart = amountSummary(decimal(app.get("minLoanAmount")), decimal(app.get("maxLoanAmount")));

        List<String> parts = new ArrayList<>();
        parts.add(productPart);
        if (segmentPart != null) {
            parts.add(segmentPart);
        }
        if (borrower != null) {
            parts.add(borrowerPart);
        } else if (!"All products".equals(productPart)) {
            parts.add("All borrower types");
        }
        parts.add(amountPart);

        String appliesLine = String.join(" · ", parts);
        String effective = effectiveSummary(date(app.get("effectiveFrom")), date(app.get("effectiveUntil")));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("appliesTo", appliesLine);
        out.put("effective", effective);
        out.put("headline", appliesLine + (effective.isBlank() ? "" : " · " + effective));
        out.put("draftScope", true);
        out.put("activationReadyScope", hasActivationHints(app));
        out.put("coveragePreview", coveragePreview(app));
        out.put("allowCanonicalAuthority", false);
        return out;
    }

    public static Map<String, Object> coveragePreview(Map<String, Object> app) {
        Map<String, Object> cov = new LinkedHashMap<>();
        List<String> products = stringList(app.get("products"));
        cov.put("product", products.isEmpty() || isAllProducts(products)
                ? "All products"
                : products.stream().map(PolicyScopeSupport::productLabel).toList());
        String bt = blankToNull(str(app.get("borrowerType")));
        cov.put("borrowerType", bt == null ? "All" : borrowerTypeLabel(bt));
        String seg = blankToNull(str(app.get("customerSegment")));
        cov.put("applicationRelationship", seg == null ? "All" : relationshipLabel(seg));
        cov.put("amount", amountSummary(decimal(app.get("minLoanAmount")), decimal(app.get("maxLoanAmount"))));
        cov.put("program", "All (program scope not enabled — mapping unreliable)");
        cov.put("populationCounts", "not available — no application census queried");
        return cov;
    }

    /** Normalize blanks → null / empty products; validate amounts. Throws IllegalArgumentException. */
    public static void normalizeAndValidate(Map<String, Object> app) {
        if (app == null) {
            return;
        }
        for (String k : List.of("facilityType", "customerSegment", "borrowerType",
                "securedUnsecured", "programScheme")) {
            if (app.containsKey(k)) {
                String v = blankToNull(str(app.get(k)));
                app.put(k, v);
            }
        }
        if (app.containsKey("products")) {
            List<String> products = stringList(app.get("products"));
            products = products.stream()
                    .filter(Objects::nonNull)
                    .map(s -> s.trim().toUpperCase(Locale.ROOT))
                    .filter(s -> !s.isBlank())
                    .filter(s -> !s.equals("ALL") && !s.equals("*") && !s.equals("ANY"))
                    .distinct()
                    .toList();
            app.put("products", new ArrayList<>(products));
        }
        BigDecimal min = decimal(app.get("minLoanAmount"));
        BigDecimal max = decimal(app.get("maxLoanAmount"));
        if (app.containsKey("minLoanAmount")) {
            app.put("minLoanAmount", min);
        }
        if (app.containsKey("maxLoanAmount")) {
            app.put("maxLoanAmount", max);
        }
        if (min != null && min.signum() < 0) {
            throw new IllegalArgumentException("Minimum requested amount cannot be negative.");
        }
        if (max != null && max.signum() < 0) {
            throw new IllegalArgumentException("Maximum requested amount cannot be negative.");
        }
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new IllegalArgumentException("Minimum requested amount must be less than or equal to maximum.");
        }
        // Clear unused advanced dims so they cannot create false routing precision
        app.put("facilityType", null);
        app.put("securedUnsecured", null);
        app.put("programScheme", null);
    }

    public static String formatInr(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        NumberFormat nf = NumberFormat.getCurrencyInstance(EN_IN);
        nf.setMaximumFractionDigits(0);
        return nf.format(amount);
    }

    private static boolean hasActivationHints(Map<String, Object> app) {
        List<String> products = stringList(app.get("products"));
        return (!products.isEmpty() && !isAllProducts(products))
                || blankToNull(str(app.get("borrowerType"))) != null
                || decimal(app.get("minLoanAmount")) != null
                || decimal(app.get("maxLoanAmount")) != null
                || date(app.get("effectiveFrom")) != null;
    }

    private static List<Map<String, String>> productOptions() {
        List<Map<String, String>> list = new ArrayList<>();
        list.add(Map.of("value", "BUSINESS_TERM_LOAN", "label", "Business Term Loan"));
        list.add(Map.of("value", "BUSINESS_WC_INVOICE_DISCOUNTING", "label", "Invoice Discounting"));
        list.add(Map.of("value", "BUSINESS_WC_OD", "label", "Working Capital Overdraft"));
        list.add(Map.of("value", "PERSONAL_LOAN", "label", "Personal Loan"));
        list.add(Map.of("value", "TERM_LOAN", "label", "Term Loan"));
        list.add(Map.of("value", "LOAN_AGAINST_PROPERTY", "label", "Loan Against Property"));
        list.add(Map.of("value", "LOAN_AGAINST_SECURITIES", "label", "Loan Against Securities"));
        list.add(Map.of("value", "LOAN_AGAINST_GOLD", "label", "Loan Against Gold"));
        // CI demo products used in shadow catalogue fixtures
        list.add(Map.of("value", "DIGILEAP", "label", "DigiLeap (demo)"));
        list.add(Map.of("value", "SMART_SWITCH", "label", "Smart Switch (demo)"));
        return list;
    }

    private static List<Map<String, String>> enumOptions(Enum<?>[] values, Map<String, String> labels) {
        List<Map<String, String>> list = new ArrayList<>();
        for (Enum<?> e : values) {
            list.add(Map.of("value", e.name(), "label", labels.getOrDefault(e.name(), e.name())));
        }
        return list;
    }

    private static Map<String, Object> dim(String source, String query, String nullBehaviour, boolean reliable) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("applicationSourceField", source);
        m.put("queryField", query);
        m.put("nullBehaviour", nullBehaviour);
        m.put("reliable", reliable);
        return m;
    }

    private static String productLabel(String code) {
        if (code == null) {
            return "All products";
        }
        return switch (code.trim().toUpperCase(Locale.ROOT)) {
            case "BUSINESS_TERM_LOAN" -> "Business Term Loan";
            case "BUSINESS_WC_INVOICE_DISCOUNTING" -> "Invoice Discounting";
            case "BUSINESS_WC_OD" -> "Working Capital Overdraft";
            case "PERSONAL_LOAN" -> "Personal Loan";
            case "TERM_LOAN" -> "Term Loan";
            case "LOAN_AGAINST_PROPERTY" -> "Loan Against Property";
            case "LOAN_AGAINST_SECURITIES" -> "Loan Against Securities";
            case "LOAN_AGAINST_GOLD" -> "Loan Against Gold";
            case "DIGILEAP" -> "DigiLeap";
            case "SMART_SWITCH" -> "Smart Switch";
            default -> code;
        };
    }

    private static String borrowerTypeLabel(String code) {
        return switch (code.toUpperCase(Locale.ROOT)) {
            case "INDIVIDUAL" -> "Individual";
            case "PROPRIETOR" -> "Proprietor";
            case "PARTNERSHIP" -> "Partnership";
            case "COMPANY" -> "Company";
            default -> code;
        };
    }

    private static String relationshipLabel(String code) {
        return switch (code.toUpperCase(Locale.ROOT)) {
            case "BORROWER" -> "Borrower applications";
            case "ANCHOR" -> "Anchor applications";
            default -> code;
        };
    }

    static String amountSummary(BigDecimal min, BigDecimal max) {
        if (min == null && max == null) {
            return "Any amount";
        }
        if (min == null) {
            return "Up to " + formatInr(max);
        }
        if (max == null) {
            return "Above " + formatInr(min);
        }
        return formatInr(min) + " – " + formatInr(max);
    }

    private static String effectiveSummary(LocalDate from, LocalDate until) {
        if (from == null && until == null) {
            return "";
        }
        if (from != null && until == null) {
            return "Effective " + from + " onward";
        }
        if (from == null) {
            return "Effective until " + until;
        }
        return "Effective " + from + " to " + until;
    }

    private static boolean isAllProducts(List<String> products) {
        return products.stream().anyMatch(p -> {
            String u = p == null ? "" : p.trim().toUpperCase(Locale.ROOT);
            return u.equals("ALL") || u.equals("*") || u.equals("ANY");
        });
    }

    private static List<String> stringList(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List<?> list) {
            for (Object x : list) {
                if (x != null) {
                    out.add(String.valueOf(x));
                }
            }
        }
        return out;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() || s.equalsIgnoreCase("ALL") || s.equals("*") || s.equalsIgnoreCase("ANY")
                ? null : s.trim();
    }

    private static BigDecimal decimal(Object o) {
        if (o == null || String.valueOf(o).isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(o).replace(",", "").trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDate date(Object o) {
        if (o == null || String.valueOf(o).isBlank()) {
            return null;
        }
        String s = String.valueOf(o);
        return LocalDate.parse(s.length() >= 10 ? s.substring(0, 10) : s);
    }
}
