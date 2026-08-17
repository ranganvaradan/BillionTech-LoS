package com.los.core.creditintelligence.bureau.provider;

import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.xml.sax.InputSource;

/**
 * Equifax account/inquiry extractor for Phase C1 (parser EQUIFAX_PARSER_V2).
 * Stores account number hash + last4 only — never full account numbers in reportData.
 */
@Slf4j
public final class EquifaxBureauAccountExtractor {

    public static final String PARSER_VERSION = "EQUIFAX_PARSER_V2";
    private static final String EQUIFAX_NS = "http://services.equifax.com/eport/ws/schemas/1.0";

    private EquifaxBureauAccountExtractor() {
    }

    /**
     * Enrich reportData with per-account maps, inquiries, parserVersion, reportDate.
     * Invoked from EquifaxBureauProvider after aggregate extraction.
     */
    public static void enrichFromDocument(Document doc, XPath xpath, Map<String, Object> reportData) {
        reportData.put("parserVersion", PARSER_VERSION);
        try {
            String reportDateRaw = getTagValue(doc, xpath, "//sch:InquiryResponseHeader/sch:Date");
            LocalDate reportDate = parseFlexibleDate(reportDateRaw);
            if (reportDate != null) {
                reportData.put("reportDate", reportDate.toString());
            }
        } catch (Exception ignored) {
            // Missing/unparseable header date must not be replaced with wall-clock.
        }

        extractNativeHeaderAndSummaries(doc, xpath, reportData);
        extractScoringElements(doc, xpath, reportData);
        extractPanIds(doc, xpath, reportData);
        extractAccounts(doc, xpath, reportData);
        extractInquiries(doc, xpath, reportData);
    }

    /**
     * Parse XML string (for tests / offline). Returns account list enrichment into a new map copy.
     */
    public static Map<String, Object> enrichFromXml(String xml, Map<String, Object> baseReportData) {
        Map<String, Object> reportData = new LinkedHashMap<>(baseReportData != null ? baseReportData : Map.of());
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new InputSource(new StringReader(xml)));
            XPath xpath = newXPath();
            enrichFromDocument(doc, xpath, reportData);
            if (!reportData.containsKey("tradelineExtractionStatus")) {
                Object accounts = reportData.get("accounts");
                if (accounts instanceof List<?> list) {
                    reportData.put("tradelineExtractionStatus", list.isEmpty() ? "EMPTY" : "OK");
                }
            }
        } catch (Exception e) {
            log.warn("[EquifaxExtractor] Failed to parse XML: {}", e.getMessage());
            reportData.put("tradelineExtractionStatus", "FAILED");
            reportData.put("parserVersion", PARSER_VERSION);
            // Do not invent reportDate on parse failure.
        }
        return reportData;
    }

    public static void markSimulatedMissing(Map<String, Object> reportData) {
        reportData.put("parserVersion", PARSER_VERSION);
        reportData.put("reportDate", LocalDate.now().toString());
        reportData.put("tradelineExtractionStatus", "MISSING");
        reportData.put("tradelinesPresent", false);
        // Do NOT invent accounts
        reportData.remove("accounts");
    }

    private static void extractAccounts(Document doc, XPath xpath, Map<String, Object> reportData) {
        try {
            NodeList accounts = (NodeList) xpath.evaluate("//sch:Account", doc, XPathConstants.NODESET);
            List<Map<String, Object>> list = new ArrayList<>();
            for (int i = 0; i < accounts.getLength(); i++) {
                Element account = (Element) accounts.item(i);
                Map<String, Object> row = new LinkedHashMap<>();
                String accountType = text(account, xpath, "./sch:AccountType");
                String accountTypeCode = text(account, xpath, "./sch:AccountTypeCode");
                row.put("AccountType", accountType);
                row.put("AccountTypeCode", accountTypeCode);
                String member = text(account, xpath, "./sch:MemberName");
                if (member == null || member.isBlank()) {
                    member = text(account, xpath, "./sch:Institution");
                }
                row.put("MemberName", member);

                String acctNum = text(account, xpath, "./sch:AccountNumber");
                putAccountNumberHash(row, acctNum);

                row.put("Balance", text(account, xpath, "./sch:Balance"));
                row.put("SanctionAmount", text(account, xpath, "./sch:SanctionAmount"));
                row.put("HighCredit", text(account, xpath, "./sch:HighCredit"));
                String emi = text(account, xpath, "./sch:InstallmentAmount");
                if (emi == null || emi.isBlank()) {
                    emi = text(account, xpath, "./sch:EMI");
                }
                row.put("InstallmentAmount", emi);
                row.put("EMI", emi);
                row.put("AccountStatus", text(account, xpath, "./sch:AccountStatus"));
                row.put("Open", text(account, xpath, "./sch:Open"));
                row.put("DateOpened", text(account, xpath, "./sch:DateOpened"));
                row.put("DateClosed", text(account, xpath, "./sch:DateClosed"));
                String dateReported = text(account, xpath, "./sch:DateReported");
                if (dateReported == null || dateReported.isBlank()) {
                    dateReported = account.getAttribute("ReportedDate");
                }
                row.put("DateReported", dateReported);
                row.put("SuitFiledStatus", text(account, xpath, "./sch:SuitFiledStatus"));
                row.put("WrittenOffAmount", text(account, xpath, "./sch:WrittenOffAmount"));
                row.put("SettlementAmount", text(account, xpath, "./sch:SettlementAmount"));
                row.put("InterestRate", text(account, xpath, "./sch:InterestRate"));
                row.put("Ownership", text(account, xpath, "./sch:OwnershipType"));
                row.put("PastDueAmount", text(account, xpath, "./sch:PastDueAmount"));
                row.put("AssetClassification", text(account, xpath, "./sch:AssetClassification"));
                row.put("CollateralType", text(account, xpath, "./sch:CollateralType"));
                row.put("CollateralValue", text(account, xpath, "./sch:CollateralValue"));
                row.put("RepaymentTenure", text(account, xpath, "./sch:RepaymentTenure"));
                row.put("LastPayment", text(account, xpath, "./sch:LastPayment"));
                row.put("LastPaymentDate", text(account, xpath, "./sch:LastPaymentDate"));
                row.put("TermFrequency", text(account, xpath, "./sch:TermFrequency"));
                row.put("DisputeCode", text(account, xpath, "./sch:DisputeCode"));
                row.put("Reason", text(account, xpath, "./sch:Reason"));
                row.put("CreditLimit", text(account, xpath, "./sch:CreditLimit"));
                row.put("WilfulDefault", text(account, xpath, "./sch:WilfulDefault"));

                // Prefer structured History48Months; also keep coded PaymentHistory string if present
                String paymentHistoryRaw = text(account, xpath, "./sch:PaymentHistory");
                if (paymentHistoryRaw != null && !paymentHistoryRaw.isBlank()) {
                    row.put("PaymentHistory", paymentHistoryRaw);
                }
                List<Map<String, Object>> months = extractHistoryMonths(account, xpath);
                if (!months.isEmpty()) {
                    row.put("HistoryMonths", months);
                }

                String seq = account.getAttribute("seq");
                if (seq != null && !seq.isBlank()) {
                    row.put("seq", seq);
                }
                list.add(row);
            }
            reportData.put("accounts", list);
            // EMPTY means extraction succeeded with zero accounts (valid zero); MISSING means not extracted
            reportData.put("tradelinesPresent", true);
            reportData.put("tradelineExtractionStatus", list.isEmpty() ? "EMPTY" : "OK");
        } catch (Exception e) {
            log.warn("[EquifaxExtractor] Account extraction failed: {}", e.getMessage());
            reportData.put("tradelineExtractionStatus", "FAILED");
            reportData.put("tradelinesPresent", false);
        }
    }

    private static List<Map<String, Object>> extractHistoryMonths(Element account, XPath xpath) {
        List<Map<String, Object>> months = new ArrayList<>();
        try {
            NodeList monthNodes = (NodeList) xpath.evaluate("./sch:History48Months/sch:Month", account, XPathConstants.NODESET);
            for (int i = 0; i < monthNodes.getLength(); i++) {
                Element monthEl = (Element) monthNodes.item(i);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("key", monthEl.getAttribute("key"));
                m.put("PaymentStatus", text(monthEl, xpath, "./sch:PaymentStatus"));
                m.put("DaysPastDue", text(monthEl, xpath, "./sch:DaysPastDue"));
                m.put("SuitFiledStatus", text(monthEl, xpath, "./sch:SuitFiledStatus"));
                m.put("AssetClassificationStatus", text(monthEl, xpath, "./sch:AssetClassificationStatus"));
                months.add(m);
            }
        } catch (Exception ignored) {
            /* leave empty */
        }
        return months;
    }

    private static void extractInquiries(Document doc, XPath xpath, Map<String, Object> reportData) {
        try {
            NodeList inquiries = (NodeList) xpath.evaluate(
                    "//sch:Enquiries|//sch:Enquiry|//sch:Inquiry|//sch:EnquiryDetail|//sch:InquiryDetail",
                    doc, XPathConstants.NODESET);
            List<Map<String, Object>> list = new ArrayList<>();
            for (int i = 0; i < inquiries.getLength(); i++) {
                Element inq = (Element) inquiries.item(i);
                if ("EnquirySummary".equals(localName(inq))) {
                    continue;
                }
                // Skip EnquirySummary children mistakenly matched — require a date-like child
                String date = firstNonBlank(
                        text(inq, xpath, "./sch:Date"),
                        text(inq, xpath, "./sch:EnquiryDate"),
                        text(inq, xpath, "./sch:InquiryDate"));
                if (date == null || date.isBlank()) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("inquiryDate", date);
                row.put("memberName", firstNonBlank(
                        text(inq, xpath, "./sch:MemberName"),
                        text(inq, xpath, "./sch:Institution"),
                        text(inq, xpath, "./sch:Lender")));
                row.put("purpose", firstNonBlank(
                        text(inq, xpath, "./sch:RequestPurpose"),
                        text(inq, xpath, "./sch:Purpose")));
                row.put("amount", text(inq, xpath, "./sch:Amount"));
                row.put("inquiryTime", firstNonBlank(
                        text(inq, xpath, "./sch:Time"),
                        text(inq, xpath, "./sch:EnquiryTime")));
                list.add(row);
            }
            if (!list.isEmpty()) {
                reportData.put("inquiries", list);
            }
        } catch (Exception e) {
            log.warn("[EquifaxExtractor] Inquiry extraction failed: {}", e.getMessage());
        }
    }

    /**
     * Collects every {@code sch:PANId} text into reportData {@code panIds} without removing {@code panId}.
     */
    private static void extractPanIds(Document doc, XPath xpath, Map<String, Object> reportData) {
        try {
            NodeList nodes = (NodeList) xpath.evaluate("//sch:PANId", doc, XPathConstants.NODESET);
            if (nodes == null || nodes.getLength() == 0) {
                nodes = (NodeList) xpath.evaluate("//*[local-name()='PANId']", doc, XPathConstants.NODESET);
            }
            List<String> pans = new ArrayList<>();
            if (nodes != null) {
                for (int i = 0; i < nodes.getLength(); i++) {
                    String raw = nodes.item(i) != null && nodes.item(i).getTextContent() != null
                            ? nodes.item(i).getTextContent().trim() : "";
                    if (!raw.isEmpty()) {
                        pans.add(raw);
                    }
                }
            }
            reportData.put("panIds", pans);
        } catch (Exception e) {
            log.warn("[EquifaxExtractor] PAN extraction failed: {}", e.getMessage());
        }
    }

    private static void extractScoringElements(Document doc, XPath xpath, Map<String, Object> reportData) {
        try {
            String scoreName = getTagValue(doc, xpath, "//sch:Score/sch:Name");
            if (scoreName != null && !scoreName.isBlank()) {
                reportData.put("scoreName", scoreName.trim());
            }
            String scoreValue = getTagValue(doc, xpath, "//sch:Score/sch:Value");
            if (scoreValue != null && !scoreValue.isBlank() && !reportData.containsKey("creditScore")) {
                try {
                    reportData.put("creditScore", Integer.parseInt(scoreValue.trim()));
                } catch (NumberFormatException ignored) {
                    /* leave absent — do not invent 0 */
                }
            }
            NodeList elements = (NodeList) xpath.evaluate(
                    "//sch:ScoringElements/sch:ScoringElement", doc, XPathConstants.NODESET);
            List<Map<String, Object>> list = new ArrayList<>();
            for (int i = 0; i < elements.getLength(); i++) {
                Element el = (Element) elements.item(i);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("code", text(el, xpath, "./sch:Code"));
                row.put("description", text(el, xpath, "./sch:Description"));
                if (row.get("code") != null || row.get("description") != null) {
                    list.add(row);
                }
            }
            if (!list.isEmpty()) {
                reportData.put("scoringElements", list);
            }
        } catch (Exception e) {
            log.warn("[EquifaxExtractor] Scoring element extraction failed: {}", e.getMessage());
        }
    }

    private static void extractNativeHeaderAndSummaries(Document doc, XPath xpath, Map<String, Object> reportData) {
        putIfPresent(reportData, "hitCode", firstNonBlank(
                getTagValue(doc, xpath, "//sch:InquiryResponseHeader/sch:HitCode"),
                getTagValue(doc, xpath, "//sch:ResponseHeader/sch:HitCode")));
        putIfPresent(reportData, "successCode", firstNonBlank(
                getTagValue(doc, xpath, "//sch:InquiryResponseHeader/sch:SuccessCode"),
                getTagValue(doc, xpath, "//sch:ResponseHeader/sch:SuccessCode")));
        putIfPresent(reportData, "reportOrderNo", firstNonBlank(
                getTagValue(doc, xpath, "//sch:InquiryResponseHeader/sch:ReportOrderNO"),
                getTagValue(doc, xpath, "//sch:InquiryResponseHeader/sch:ReportOrderNo")));
        putIfPresent(reportData, "reportTime", firstNonBlank(
                getTagValue(doc, xpath, "//sch:InquiryResponseHeader/sch:Time"),
                getTagValue(doc, xpath, "//sch:ResponseHeader/sch:Time")));
        reportData.put("nativeAccountSummary", childMap(doc, xpath, "//sch:AccountSummary"));
        reportData.put("nativeEnquirySummary", childMap(doc, xpath, "//sch:EnquirySummary"));
        reportData.put("nativeRecentActivities", childMap(doc, xpath, "//sch:RecentActivities"));
        reportData.put("nativeOtherKeyInd", childMap(doc, xpath, "//sch:OtherKeyInd"));
    }

    private static Map<String, String> childMap(Document doc, XPath xpath, String parentExpr) {
        Map<String, String> out = new LinkedHashMap<>();
        try {
            Element parent = (Element) xpath.evaluate(parentExpr, doc, XPathConstants.NODE);
            if (parent == null) {
                return out;
            }
            NodeList children = parent.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (!(children.item(i) instanceof Element child)) {
                    continue;
                }
                String name = localName(child);
                String value = child.getTextContent() != null ? child.getTextContent().trim() : "";
                if (name != null && !name.isBlank() && !value.isEmpty()) {
                    out.put(name, value);
                }
            }
        } catch (Exception ignored) {
            return out;
        }
        return out;
    }

    private static void putIfPresent(Map<String, Object> reportData, String key, String value) {
        if (value != null && !value.isBlank()) {
            reportData.put(key, value.trim());
        }
    }

    private static String localName(Element el) {
        if (el == null) {
            return "";
        }
        String ln = el.getLocalName();
        if (ln != null && !ln.isBlank()) {
            return ln;
        }
        String tag = el.getTagName();
        int colon = tag == null ? -1 : tag.indexOf(':');
        return colon >= 0 ? tag.substring(colon + 1) : tag;
    }

    static void putAccountNumberHash(Map<String, Object> row, String acctNum) {
        if (acctNum == null || acctNum.isBlank()) {
            return;
        }
        String cleaned = acctNum.replace("*", "").trim();
        String last4 = cleaned.length() >= 4
                ? cleaned.substring(cleaned.length() - 4)
                : cleaned;
        // Prefer hashing last4 when masked; otherwise hash full then store only hash+last4
        String toHash = cleaned.isEmpty() ? acctNum : cleaned;
        row.put("AccountNumberHash", sha256Hex(toHash));
        if (!last4.isEmpty() && !last4.contains("*")) {
            row.put("AccountNumberLast4", last4);
        }
        // Never put raw AccountNumber
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static LocalDate parseFlexibleDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        String[] patterns = {"yyyy-MM-dd", "dd-MM-yyyy", "dd/MM/yyyy", "yyyy/MM/dd", "dd-MMM-yyyy"};
        for (String p : patterns) {
            try {
                return LocalDate.parse(s, DateTimeFormatter.ofPattern(p, Locale.ENGLISH));
            } catch (DateTimeParseException ignored) {
                /* try next */
            }
        }
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    /** Parse Equifax History48Months key like "10-25" → first day of month. */
    public static LocalDate parseHistoryMonthKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            String[] parts = key.trim().split("-");
            if (parts.length == 2) {
                int month = Integer.parseInt(parts[0]);
                int year = Integer.parseInt(parts[1]);
                if (year < 100) {
                    year += 2000;
                }
                return LocalDate.of(year, month, 1);
            }
        } catch (Exception ignored) {
            /* fall through */
        }
        return null;
    }

    private static XPath newXPath() {
        XPath xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(new NamespaceContext() {
            public String getNamespaceURI(String prefix) {
                if ("sch".equals(prefix)) {
                    return EQUIFAX_NS;
                }
                return XMLConstants.NULL_NS_URI;
            }
            public String getPrefix(String namespaceURI) {
                return null;
            }
            public Iterator<String> getPrefixes(String namespaceURI) {
                return null;
            }
        });
        return xpath;
    }

    private static String getTagValue(Document doc, XPath xpath, String expression) {
        try {
            return (String) xpath.evaluate(expression, doc, XPathConstants.STRING);
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(Element element, XPath xpath, String expression) {
        try {
            String v = (String) xpath.evaluate(expression, element, XPathConstants.STRING);
            return v != null && !v.isBlank() ? v.trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
