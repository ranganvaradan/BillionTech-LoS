package com.los.core.creditintelligence.tax.provider;

import com.los.core.creditintelligence.tax.domain.AisCategory;
import com.los.core.creditintelligence.tax.domain.ItrForm;
import com.los.core.creditintelligence.tax.domain.PresumptiveSection;
import com.los.core.creditintelligence.tax.domain.ReturnVersionType;
import com.los.core.creditintelligence.tax.domain.TaxConstants;
import com.los.core.creditintelligence.tax.util.ItrEffectiveReturnSelector;
import com.los.core.creditintelligence.tax.util.ItrFormNormalizer;
import com.los.core.creditintelligence.tax.util.TaxYearUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Extracts canonical ITR / AIS / Form 26AS structures from Karza ITR return-forms payload.
 * Never invents amounts for missing fields. Parser: {@link TaxConstants#KARZA_ITR_PARSER_V1}.
 */
public final class KarzaItrCanonicalExtractor {

    public static final String PARSER_VERSION = TaxConstants.KARZA_ITR_PARSER_V1;

    public record ExtractedIncome(
            BigDecimal salaryIncome,
            BigDecimal housePropertyIncome,
            BigDecimal businessProfessionIncome,
            BigDecimal capitalGains,
            BigDecimal otherSources,
            BigDecimal grossTotalIncome,
            BigDecimal totalIncome,
            Map<String, Object> metadata) {
    }

    public record ExtractedBusinessFinancials(
            BigDecimal grossReceipts,
            BigDecimal salesTurnover,
            BigDecimal grossProfit,
            BigDecimal ebitda,
            BigDecimal depreciation,
            BigDecimal financeCost,
            BigDecimal profitBeforeTax,
            BigDecimal profitAfterTax,
            BigDecimal totalLiabilities,
            BigDecimal netWorth,
            BigDecimal totalAssets,
            BigDecimal totalBorrowings,
            boolean balanceSheetPresent,
            Map<String, Object> metadata) {
    }

    public record ExtractedPresumptive(
            String applicableSection,
            BigDecimal grossReceipts,
            BigDecimal declaredPresumptiveIncome,
            BigDecimal declaredMargin,
            Map<String, Object> metadata) {
    }

    public record ExtractedTaxSummary(
            BigDecimal taxLiability,
            BigDecimal taxPayable,
            BigDecimal taxPaid,
            BigDecimal tds,
            BigDecimal tcs,
            BigDecimal advanceTax,
            BigDecimal selfAssessmentTax,
            BigDecimal refundClaimed,
            BigDecimal outstandingDemand,
            Map<String, Object> metadata) {
    }

    public record ExtractedReturn(
            String assessmentYear,
            String financialYear,
            ItrForm itrForm,
            LocalDate filingDate,
            String pan,
            String panHash,
            String panLast4,
            ReturnVersionType returnVersionType,
            String filingStatus,
            String filingSection,
            String ackMasked,
            boolean presumptive,
            ExtractedIncome income,
            ExtractedBusinessFinancials business,
            ExtractedPresumptive presumptiveIncome,
            ExtractedTaxSummary taxSummary,
            Map<String, Object> metadata) {
    }

    public record ExtractedAisInfo(
            String category,
            String informationCode,
            String reportingEntity,
            BigDecimal amount,
            BigDecimal acceptedValue,
            Map<String, Object> metadata) {
    }

    public record ExtractedAis(
            String financialYear,
            BigDecimal totalReportedValue,
            List<ExtractedAisInfo> information,
            Map<String, Object> metadata) {
    }

    public record ExtractedForm26AsEntry(
            String sectionCode,
            String deductorName,
            BigDecimal amountPaidCredited,
            BigDecimal taxDeducted,
            BigDecimal taxDeposited,
            LocalDate bookingDate,
            Map<String, Object> metadata) {
    }

    public record ExtractedForm26As(
            String financialYear,
            BigDecimal totalTds,
            BigDecimal totalTcs,
            BigDecimal advanceTax,
            BigDecimal selfAssessmentTax,
            BigDecimal refund,
            List<ExtractedForm26AsEntry> entries,
            Map<String, Object> metadata) {
    }

    public record ExtractionResult(
            List<ExtractedReturn> returns,
            boolean aisAvailable,
            List<ExtractedAis> aisSummaries,
            boolean form26AsAvailable,
            List<ExtractedForm26As> form26AsSummaries,
            String parserVersion) {
    }

    private KarzaItrCanonicalExtractor() {
    }

    @SuppressWarnings("unchecked")
    public static ExtractionResult extract(Map<String, Object> parsedDataOrResult) {
        Map<String, Object> root = parsedDataOrResult != null ? parsedDataOrResult : Map.of();
        Map<String, Object> result = resolveResult(root);

        Map<String, Object> formDetails = mapOf(result.get("formDetails"));
        Map<String, Object> general = mapOf(result.get("generalInformation"));
        String defaultForm = firstNonBlank(str(formDetails.get("formName")), str(formDetails.get("itrForm")));
        String defaultPan = firstNonBlank(str(general.get("entityPan")), str(general.get("pan")));
        String defaultAy = firstNonBlank(str(formDetails.get("assessmentYear")), str(formDetails.get("ay")));

        List<Map<String, Object>> itrFilled = listOfMaps(result.get("itrFilled"));
        List<Map<String, Object>> financials = listOfMaps(result.get("financialInformation"));

        Map<String, Map<String, Object>> filledByAy = new LinkedHashMap<>();
        for (Map<String, Object> row : itrFilled) {
            String ay = firstNonBlank(str(row.get("annualYear")), str(row.get("assessmentYear")));
            if (ay != null) {
                filledByAy.putIfAbsent(TaxYearUtils.normalizeYearLabel(ay).orElse(ay), row);
            }
        }

        List<ExtractedReturn> returns = new ArrayList<>();
        if (!financials.isEmpty()) {
            for (Map<String, Object> yr : financials) {
                returns.add(extractReturnFromYear(yr, filledByAy, defaultForm, defaultPan, defaultAy));
            }
        } else if (!itrFilled.isEmpty()) {
            for (Map<String, Object> row : itrFilled) {
                returns.add(extractReturnFromFilledOnly(row, defaultForm, defaultPan, defaultAy));
            }
        } else if (defaultAy != null || defaultForm != null) {
            returns.add(extractSparseReturn(formDetails, general, defaultForm, defaultPan, defaultAy));
        }

        boolean aisAvailable = false;
        List<ExtractedAis> aisList = new ArrayList<>();
        Object aisNode = firstPresent(result.get("ais"), result.get("AIS"), result.get("annualInformationStatement"));
        if (aisNode != null) {
            aisAvailable = true;
            aisList.addAll(extractAis(aisNode, defaultPan));
        }

        boolean form26Available = false;
        List<ExtractedForm26As> form26List = new ArrayList<>();
        Object f26 = firstPresent(result.get("form26as"), result.get("form26AS"),
                result.get("twentySixAS"), result.get("form26As"));
        if (f26 != null) {
            form26Available = true;
            form26List.addAll(extractForm26As(f26));
        }

        return new ExtractionResult(returns, aisAvailable, aisList, form26Available, form26List, PARSER_VERSION);
    }

    private static ExtractedReturn extractReturnFromYear(
            Map<String, Object> yr,
            Map<String, Map<String, Object>> filledByAy,
            String defaultForm,
            String defaultPan,
            String defaultAy) {

        String ayRaw = firstNonBlank(str(yr.get("assessmentYear")), defaultAy);
        String fyRaw = str(yr.get("financialYear"));
        Optional<String> ay = TaxYearUtils.normalizeYearLabel(ayRaw);
        Optional<String> fy = TaxYearUtils.normalizeYearLabel(fyRaw);
        if (fy.isEmpty() && ay.isPresent()) {
            fy = TaxYearUtils.ayToFy(ay.get());
        }
        if (ay.isEmpty() && fy.isPresent()) {
            ay = TaxYearUtils.fyToAy(fy.get());
        }

        Map<String, Object> filled = ay.map(filledByAy::get).orElse(null);
        String formRaw = firstNonBlank(
                filled != null ? firstNonBlank(str(filled.get("itrForm")), str(filled.get("formName"))) : null,
                defaultForm);
        ItrForm form = ItrFormNormalizer.normalize(formRaw);
        String pan = firstNonBlank(filled != null ? str(filled.get("pan")) : null, defaultPan);
        LocalDate filingDate = parseDate(filled != null
                ? firstNonBlank(str(filled.get("fillingDate")), str(filled.get("filingDate")))
                : null);
        ReturnVersionType version = ItrEffectiveReturnSelector.detectVersionType(
                filled != null ? firstNonBlank(str(filled.get("returnType")), str(filled.get("filingType")),
                        str(filled.get("section"))) : null);
        String filingSection = filled != null ? str(filled.get("section")) : null;
        String ack = maskAck(filled != null ? str(filled.get("ackNo")) : null);

        Map<String, Object> pl = mapOf(yr.get("profitAndLoss"));
        Map<String, Object> bs = mapOf(yr.get("balanceSheet"));
        boolean bsPresent = !bs.isEmpty();

        BigDecimal totalRevenue = bd(pl.get("totalRevenue"));
        BigDecimal pat = bd(pl.get("profitAfterTax"));
        BigDecimal ebitda = bd(pl.get("ebitda"));
        BigDecimal interest = bd(pl.get("interestExpense"));
        BigDecimal pbt = bd(firstPresent(pl.get("profitBeforeTax"), pl.get("pbt")));
        BigDecimal grossProfit = bd(pl.get("grossProfit"));
        BigDecimal depreciation = bd(pl.get("depreciation"));

        BigDecimal totalLiability = bd(bs.get("totalLiability"));
        BigDecimal totalEquity = bd(bs.get("totalEquity"));
        BigDecimal totalAssets = bd(firstPresent(bs.get("totalAssets"), bs.get("totalAsset")));
        BigDecimal totalBorrowings = bd(firstPresent(bs.get("totalBorrowings"), bs.get("borrowings")));

        ExtractedBusinessFinancials business = new ExtractedBusinessFinancials(
                totalRevenue, totalRevenue, grossProfit, ebitda, depreciation, interest,
                pbt, pat, totalLiability, totalEquity, totalAssets, totalBorrowings,
                bsPresent, Map.of("source", "financialInformation.profitAndLoss|balanceSheet"));

        // Income heads: never copy totalRevenue into business / GTI / total income.
        // salesTurnover / business turnover ← totalRevenue; income heads only when explicit.
        ExtractedIncome income = new ExtractedIncome(
                bd(pl.get("salaryIncome")),
                bd(pl.get("housePropertyIncome")),
                bd(pl.get("businessProfessionIncome")),
                bd(pl.get("capitalGains")),
                bd(pl.get("otherSources")),
                bd(pl.get("grossTotalIncome")),
                bd(pl.get("totalIncome")),
                Map.of(
                        "salesTurnoverFromTotalRevenue", totalRevenue != null,
                        "incomeHeadsExplicitOnly", true));

        boolean presumptive = ItrFormNormalizer.isPresumptiveForm(form)
                || ItrFormNormalizer.looksPresumptive(formRaw)
                || ItrFormNormalizer.looksPresumptive(filingSection);
        ExtractedPresumptive presumptiveIncome = null;
        if (presumptive) {
            PresumptiveSection section = detectPresumptiveSection(filingSection, formRaw);
            BigDecimal receipts = firstBd(totalRevenue, bd(yr.get("grossReceipts")));
            BigDecimal declared = firstBd(bd(yr.get("presumptiveIncome")), pat, income.businessProfessionIncome());
            BigDecimal margin = null;
            if (receipts != null && declared != null && receipts.compareTo(BigDecimal.ZERO) != 0) {
                margin = declared.divide(receipts, 4, java.math.RoundingMode.HALF_UP);
            }
            presumptiveIncome = new ExtractedPresumptive(
                    section.name(), receipts, declared, margin,
                    Map.of("form", form.name()));
        }

        ExtractedTaxSummary taxSummary = extractTaxSummary(yr, mapOf(yr.get("taxDetails")));

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("presumptive", presumptive);
        if (formRaw != null) {
            meta.put("rawForm", formRaw);
        }

        return new ExtractedReturn(
                ay.orElse(ayRaw),
                fy.orElse(fyRaw),
                form,
                filingDate,
                pan,
                hashPan(pan),
                panLast4(pan),
                version,
                filingDate != null ? "FILED" : "UNKNOWN",
                filingSection,
                ack,
                presumptive,
                income,
                business,
                presumptiveIncome,
                taxSummary,
                meta);
    }

    private static ExtractedReturn extractReturnFromFilledOnly(
            Map<String, Object> filled, String defaultForm, String defaultPan, String defaultAy) {
        String ayRaw = firstNonBlank(str(filled.get("annualYear")), str(filled.get("assessmentYear")), defaultAy);
        Optional<String> ay = TaxYearUtils.normalizeYearLabel(ayRaw);
        Optional<String> fy = ay.flatMap(TaxYearUtils::ayToFy);
        String formRaw = firstNonBlank(str(filled.get("itrForm")), defaultForm);
        ItrForm form = ItrFormNormalizer.normalize(formRaw);
        String pan = firstNonBlank(str(filled.get("pan")), defaultPan);
        boolean presumptive = ItrFormNormalizer.isPresumptiveForm(form)
                || ItrFormNormalizer.looksPresumptive(formRaw);
        return new ExtractedReturn(
                ay.orElse(ayRaw),
                fy.orElse(null),
                form,
                parseDate(firstNonBlank(str(filled.get("fillingDate")), str(filled.get("filingDate")))),
                pan,
                hashPan(pan),
                panLast4(pan),
                ItrEffectiveReturnSelector.detectVersionType(str(filled.get("returnType"))),
                "FILED",
                str(filled.get("section")),
                maskAck(str(filled.get("ackNo"))),
                presumptive,
                new ExtractedIncome(null, null, null, null, null, null, null, Map.of("noFinancials", true)),
                new ExtractedBusinessFinancials(null, null, null, null, null, null, null, null,
                        null, null, null, null, false, Map.of("noFinancials", true)),
                presumptive ? new ExtractedPresumptive(PresumptiveSection.S_44AD.name(), null, null, null, Map.of())
                        : null,
                null,
                Map.of("source", "itrFilled_only"));
    }

    private static ExtractedReturn extractSparseReturn(
            Map<String, Object> formDetails, Map<String, Object> general,
            String defaultForm, String defaultPan, String defaultAy) {
        ItrForm form = ItrFormNormalizer.normalize(defaultForm);
        Optional<String> ay = TaxYearUtils.normalizeYearLabel(defaultAy);
        return new ExtractedReturn(
                ay.orElse(defaultAy != null ? defaultAy : "UNKNOWN"),
                ay.flatMap(TaxYearUtils::ayToFy).orElse(null),
                form,
                null,
                defaultPan,
                hashPan(defaultPan),
                panLast4(defaultPan),
                ReturnVersionType.UNKNOWN,
                "UNKNOWN",
                null,
                null,
                ItrFormNormalizer.isPresumptiveForm(form),
                new ExtractedIncome(null, null, null, null, null, null, null, Map.of("sparse", true)),
                new ExtractedBusinessFinancials(null, null, null, null, null, null, null, null,
                        null, null, null, null, false, Map.of("sparse", true)),
                null,
                null,
                sparseMeta(general));
    }

    private static Map<String, Object> sparseMeta(Map<String, Object> general) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("sparse", true);
        String entityName = str(general.get("entityName"));
        if (entityName != null) {
            meta.put("entityName", entityName);
        }
        return meta;
    }

    private static ExtractedTaxSummary extractTaxSummary(Map<String, Object> yr, Map<String, Object> tax) {
        Map<String, Object> src = !tax.isEmpty() ? tax : yr;
        BigDecimal liability = bd(firstPresent(src.get("taxLiability"), src.get("totalTaxLiability")));
        BigDecimal payable = bd(src.get("taxPayable"));
        BigDecimal paid = bd(firstPresent(src.get("taxPaid"), src.get("totalTaxPaid")));
        BigDecimal tds = bd(src.get("tds"));
        BigDecimal tcs = bd(src.get("tcs"));
        BigDecimal advance = bd(src.get("advanceTax"));
        BigDecimal sat = bd(src.get("selfAssessmentTax"));
        BigDecimal refund = bd(firstPresent(src.get("refundClaimed"), src.get("refund")));
        BigDecimal demand = bd(src.get("outstandingDemand"));
        if (liability == null && payable == null && paid == null && tds == null && tcs == null
                && advance == null && sat == null && refund == null && demand == null) {
            return null;
        }
        return new ExtractedTaxSummary(liability, payable, paid, tds, tcs, advance, sat, refund, demand, Map.of());
    }

    @SuppressWarnings("unchecked")
    private static List<ExtractedAis> extractAis(Object aisNode, String defaultPan) {
        List<ExtractedAis> out = new ArrayList<>();
        if (aisNode instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    out.add(extractOneAis((Map<String, Object>) m));
                }
            }
            return out;
        }
        if (aisNode instanceof Map<?, ?> m) {
            Map<String, Object> mm = (Map<String, Object>) m;
            if (mm.containsKey("information") || mm.containsKey("items") || mm.containsKey("financialYear")) {
                out.add(extractOneAis(mm));
            } else if (mm.isEmpty()) {
                out.add(new ExtractedAis(null, null, List.of(), Map.of("empty", true)));
            } else {
                out.add(extractOneAis(mm));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static ExtractedAis extractOneAis(Map<String, Object> m) {
        String fy = TaxYearUtils.normalizeYearLabel(
                firstNonBlank(str(m.get("financialYear")), str(m.get("fy")))).orElse(str(m.get("financialYear")));
        List<ExtractedAisInfo> infos = new ArrayList<>();
        Object infoNode = firstPresent(m.get("information"), m.get("items"), m.get("details"));
        if (infoNode instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> row) {
                    Map<String, Object> r = (Map<String, Object>) row;
                    infos.add(new ExtractedAisInfo(
                            normalizeAisCategory(str(r.get("category"))),
                            str(r.get("informationCode")),
                            str(firstPresent(r.get("reportingEntity"), r.get("deductorName"))),
                            bd(firstPresent(r.get("amount"), r.get("value"))),
                            bd(r.get("acceptedValue")),
                            Map.of()));
                }
            }
        }
        BigDecimal total = bd(m.get("totalReportedValue"));
        if (total == null && !infos.isEmpty()) {
            total = infos.stream()
                    .map(i -> i.amount() != null ? i.amount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        return new ExtractedAis(fy, total, infos, Map.of());
    }

    @SuppressWarnings("unchecked")
    private static List<ExtractedForm26As> extractForm26As(Object node) {
        List<ExtractedForm26As> out = new ArrayList<>();
        if (node instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    out.add(extractOne26As((Map<String, Object>) m));
                }
            }
            return out;
        }
        if (node instanceof Map<?, ?> m) {
            Map<String, Object> mm = (Map<String, Object>) m;
            if (mm.isEmpty()) {
                out.add(new ExtractedForm26As(null, null, null, null, null, null, List.of(), Map.of("empty", true)));
            } else {
                out.add(extractOne26As(mm));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static ExtractedForm26As extractOne26As(Map<String, Object> m) {
        String fy = TaxYearUtils.normalizeYearLabel(
                firstNonBlank(str(m.get("financialYear")), str(m.get("fy")))).orElse(str(m.get("financialYear")));
        List<ExtractedForm26AsEntry> entries = new ArrayList<>();
        Object entryNode = firstPresent(m.get("entries"), m.get("tds"), m.get("details"));
        if (entryNode instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> row) {
                    Map<String, Object> r = (Map<String, Object>) row;
                    entries.add(new ExtractedForm26AsEntry(
                            str(r.get("sectionCode")),
                            str(r.get("deductorName")),
                            bd(firstPresent(r.get("amountPaidCredited"), r.get("amount"))),
                            bd(firstPresent(r.get("taxDeducted"), r.get("tds"))),
                            bd(r.get("taxDeposited")),
                            parseDate(str(r.get("bookingDate"))),
                            Map.of()));
                }
            }
        }
        return new ExtractedForm26As(
                fy,
                bd(firstPresent(m.get("totalTds"), m.get("tds"))),
                bd(firstPresent(m.get("totalTcs"), m.get("tcs"))),
                bd(m.get("advanceTax")),
                bd(m.get("selfAssessmentTax")),
                bd(m.get("refund")),
                entries,
                Map.of());
    }

    private static String normalizeAisCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            return AisCategory.UNKNOWN.name();
        }
        String u = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        try {
            return AisCategory.valueOf(u).name();
        } catch (Exception e) {
            if (u.contains("INTEREST")) return AisCategory.INTEREST.name();
            if (u.contains("DIVIDEND")) return AisCategory.DIVIDEND.name();
            if (u.contains("SALARY")) return AisCategory.SALARY.name();
            if (u.contains("TDS")) return AisCategory.TDS.name();
            if (u.contains("TCS")) return AisCategory.TCS.name();
            if (u.contains("CASH")) return AisCategory.CASH_DEPOSITS.name();
            if (u.contains("GST")) return AisCategory.GST_TURNOVER_INFORMATION.name();
            if (u.contains("BUSINESS")) return AisCategory.BUSINESS_RECEIPTS.name();
            return AisCategory.OTHER.name();
        }
    }

    private static PresumptiveSection detectPresumptiveSection(String section, String formRaw) {
        String u = ((section != null ? section : "") + " " + (formRaw != null ? formRaw : "")).toUpperCase(Locale.ROOT);
        if (u.contains("44ADA")) return PresumptiveSection.S_44ADA;
        if (u.contains("44AE")) return PresumptiveSection.S_44AE;
        if (u.contains("44AD") || u.contains("ITR-4") || u.contains("ITR4")) return PresumptiveSection.S_44AD;
        return PresumptiveSection.OTHER;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resolveResult(Map<String, Object> root) {
        Object result = root.get("result");
        if (result instanceof Map<?, ?> rm) {
            return (Map<String, Object>) rm;
        }
        if (root.containsKey("financialInformation") || root.containsKey("formDetails")
                || root.containsKey("itrFilled") || root.containsKey("generalInformation")) {
            return root;
        }
        return Map.of();
    }

    public static String hashPan(String pan) {
        if (pan == null || pan.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(pan.trim().toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String panLast4(String pan) {
        if (pan == null || pan.isBlank()) {
            return null;
        }
        String t = pan.trim();
        return t.length() <= 4 ? t : t.substring(t.length() - 4);
    }

    private static String maskAck(String ack) {
        if (ack == null || ack.isBlank()) {
            return null;
        }
        String t = ack.trim();
        if (t.length() <= 4) {
            return "****";
        }
        return "****" + t.substring(t.length() - 4);
    }

    private static BigDecimal firstBd(BigDecimal... values) {
        if (values == null) {
            return null;
        }
        for (BigDecimal v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static Object firstPresent(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapOf(Object o) {
        if (o instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object o) {
        if (!(o instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    private static String str(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o);
        return s.isBlank() || "null".equalsIgnoreCase(s) ? null : s;
    }

    private static BigDecimal bd(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof BigDecimal b) {
            return b;
        }
        if (o instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        String t = String.valueOf(o).trim().replace(",", "");
        if (t.isEmpty() || "null".equalsIgnoreCase(t)) {
            return null;
        }
        try {
            return new BigDecimal(t);
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim();
        for (DateTimeFormatter f : List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"))) {
            try {
                return LocalDate.parse(t, f);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        if (t.length() >= 10) {
            try {
                return LocalDate.parse(t.substring(0, 10));
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }
}
