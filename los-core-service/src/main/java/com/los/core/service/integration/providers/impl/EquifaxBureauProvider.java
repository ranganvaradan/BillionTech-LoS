package com.los.core.service.integration.providers.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.config.IntegrationProperties;
import com.los.core.creditintelligence.bureau.provider.EquifaxBureauAccountExtractor;
import com.los.core.model.entity.ApiAuditLog;
import com.los.core.repository.ApiAuditLogRepository;
import com.los.core.service.document.IDocumentService;
import com.los.core.service.integration.providers.IBureauProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Equifax Credit Bureau Provider — adapted from legacy EquifaxController + EquifaxServiceFacadeImpl.
 *
 * Sends SOAP/XML inquiry to Equifax India API, parses the XML response to extract
 * credit score, account details, enquiry summary, and validation flags.
 *
 * Legacy reference:
 *   - bl-core/.../controllers/EquifaxController.java  (retailRequest, commercialRequest XML builders)
 *   - bl-core/.../facade/impl/EquifaxServiceFacadeImpl.java (extractReport, XPath parsing)
 *   - bl-core/.../web/rest/DsaRestController.java (getEquifaxScore, buildEquifaxReqData)
 */
@Slf4j
@Component("equifaxBureauProvider")
@RequiredArgsConstructor
public class EquifaxBureauProvider implements IBureauProvider {

    public static final String AVAILABILITY_PROVIDER_READY = "PROVIDER_READY";
    public static final String AVAILABILITY_PROVIDER_UNAVAILABLE = "PROVIDER_UNAVAILABLE";
    public static final String AVAILABILITY_PROVIDER_FAILED = "PROVIDER_FAILED";
    public static final String AVAILABILITY_EXPLICIT_SIMULATION = "EXPLICIT_SIMULATION";
    public static final String PROVENANCE_PROVIDER = "PROVIDER";
    public static final String PROVENANCE_SIMULATED = "SIMULATED";
    public static final String ERR_PROVIDER_UNAVAILABLE =
            "PROVIDER_UNAVAILABLE: Equifax credentials not configured";
    public static final String FIXTURE_SOURCE_KEY = "equifaxFixtureSource";
    public static final String FIXTURE_CLASSPATH_SAMPLE = "CLASSPATH_SAMPLE";

    private final IntegrationProperties integrationProperties;
    private final ApiAuditLogRepository apiAuditLogRepository;
    private final ObjectMapper objectMapper;
    private final IDocumentService documentService;

    /** Equifax SOAP namespace used in response XPath queries */
    private static final String EQUIFAX_NS = "http://services.equifax.com/eport/ws/schemas/1.0";

    /** Overridable for unit tests (timeout / HTTP error goldens). */
    private EquifaxHttpTransport httpTransport = EquifaxHttpTransport.DEFAULT;

    void setHttpTransportForTests(EquifaxHttpTransport httpTransport) {
        this.httpTransport = httpTransport != null ? httpTransport : EquifaxHttpTransport.DEFAULT;
    }

    @FunctionalInterface
    interface EquifaxHttpTransport {
        HttpResponse<String> send(HttpRequest request, IntegrationProperties.EquifaxProperties config) throws Exception;

        EquifaxHttpTransport DEFAULT = (request, config) -> {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        };
    }

    @Override
    public String getProviderName() {
        return "EQUIFAX";
    }

    @Override
    public BureauPullResult pullReport(Map<String, Object> borrowerInfo) {
        log.info("[Equifax] Pulling credit bureau report for: {}", borrowerInfo.getOrDefault("name", "unknown"));

        IntegrationProperties.EquifaxProperties config = integrationProperties.getEquifax();
        String transactionId = "EQX-" + UUID.randomUUID().toString().substring(0, 8);

        // BUREAU-P0-3 availability contract — never infer simulation from missing credentials.
        // Live credentials always win over any fixture request (production cannot be diverted).
        if (config.isConfigured()) {
            return pullLive(borrowerInfo, config, transactionId);
        }
        if (internalClasspathFixtureRequested(borrowerInfo, config)) {
            log.info("[Equifax] INTERNAL fixture ingest — classpath sample XML at provider-response boundary");
            return simulatedFallback(borrowerInfo, transactionId);
        }
        if (config.isSimulation()) {
            log.info("[Equifax] Explicit simulation enabled — returning SIMULATED fixture (not PROVIDER)");
            return simulatedFallback(borrowerInfo, transactionId);
        }
        log.warn("[Equifax] Credentials not configured and simulation disabled — PROVIDER_UNAVAILABLE");
        return providerUnavailable(transactionId);
    }

    private static boolean internalClasspathFixtureRequested(
            Map<String, Object> borrowerInfo,
            IntegrationProperties.EquifaxProperties config) {
        if (config == null || !config.isInternalFixtureIngestEnabled()) {
            return false;
        }
        if (borrowerInfo == null) {
            return false;
        }
        Object source = borrowerInfo.get(FIXTURE_SOURCE_KEY);
        return source != null && FIXTURE_CLASSPATH_SAMPLE.equalsIgnoreCase(String.valueOf(source).trim());
    }

    private static BureauPullResult providerUnavailable(String transactionId) {
        Map<String, Object> reportData = new LinkedHashMap<>();
        reportData.put("providerAvailability", AVAILABILITY_PROVIDER_UNAVAILABLE);
        reportData.put("dataProvenance", AVAILABILITY_PROVIDER_UNAVAILABLE);
        reportData.put("simulated", false);
        return new BureauPullResult(false, 0, reportData, transactionId, ERR_PROVIDER_UNAVAILABLE);
    }

    private BureauPullResult pullLive(
            Map<String, Object> borrowerInfo,
            IntegrationProperties.EquifaxProperties config,
            String transactionId) {
        String pan = (String) borrowerInfo.getOrDefault("panNumber", "");
        if (pan.isEmpty()) {
            return new BureauPullResult(false, 0, null, transactionId, "PAN number is required for bureau pull");
        }

        // Build SOAP request XML — adapted from legacy retailRequest()
        String requestXml = buildRetailRequest(borrowerInfo, config);
        Instant requestTime = Instant.now();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getUrl()))
                    .timeout(Duration.ofMillis(config.getReadTimeoutMs()))
                    .header("Content-Type", "text/xml; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(requestXml))
                    .build();

            HttpResponse<String> response = httpTransport.send(request, config);
            Instant responseTime = Instant.now();
            long durationMs = Duration.between(requestTime, responseTime).toMillis();

            log.info("[Equifax] HTTP {} in {}ms", response.statusCode(), durationMs);

            // Audit log — mask sensitive fields in request
            String maskedRequest = requestXml
                    .replaceAll("<ns:Password>[^<]*</ns:Password>", "<ns:Password>***</ns:Password>")
                    .replaceAll("<ns:SecurityCode>[^<]*</ns:SecurityCode>", "<ns:SecurityCode>***</ns:SecurityCode>");
            saveAuditLog("EQUIFAX", "EQUIFAX_RETAIL_INQUIRY", maskedRequest,
                    truncateForAudit(response.body()), response.statusCode() == 200 ? "SUCCESS" : "FAILED",
                    response.statusCode(), null, transactionId, requestTime, responseTime, durationMs);

            if (response.statusCode() == 200) {
                BureauPullResult parsed = parseEquifaxResponse(response.body(), transactionId);
                return stampProviderProvenance(parsed);
            }
            return providerFailed(transactionId,
                    "Equifax API returned HTTP " + response.statusCode());

        } catch (Exception e) {
            log.error("[Equifax] API call failed: {}", e.getMessage(), e);
            saveAuditLog("EQUIFAX", "EQUIFAX_RETAIL_INQUIRY", requestXml,
                    null, "ERROR", null, e.getMessage(), transactionId,
                    requestTime, Instant.now(), null);
            return providerFailed(transactionId, "Equifax API error: " + e.getMessage());
        }
    }

    private static BureauPullResult providerFailed(String transactionId, String message) {
        Map<String, Object> reportData = new LinkedHashMap<>();
        reportData.put("providerAvailability", AVAILABILITY_PROVIDER_FAILED);
        reportData.put("dataProvenance", AVAILABILITY_PROVIDER_FAILED);
        reportData.put("simulated", false);
        return new BureauPullResult(false, 0, reportData, transactionId, message);
    }

    private static BureauPullResult stampProviderProvenance(BureauPullResult parsed) {
        if (parsed.reportData() == null) {
            return parsed;
        }
        Map<String, Object> reportData = new LinkedHashMap<>(parsed.reportData());
        reportData.putIfAbsent("providerAvailability",
                parsed.success() ? AVAILABILITY_PROVIDER_READY : AVAILABILITY_PROVIDER_FAILED);
        reportData.put("dataProvenance", PROVENANCE_PROVIDER);
        reportData.put("simulated", false);
        return new BureauPullResult(
                parsed.success(), parsed.creditScore(), reportData, parsed.transactionId(), parsed.errorMessage());
    }

    /**
     * Build SOAP XML request for Equifax Retail inquiry.
     * Exact format from legacy EquifaxController.retailRequest()
     */
    private String buildRetailRequest(Map<String, Object> borrowerInfo, IntegrationProperties.EquifaxProperties config) {
        String name = (String) borrowerInfo.getOrDefault("name", "");
        String address = (String) borrowerInfo.getOrDefault("address", "");
        String state = (String) borrowerInfo.getOrDefault("state", "");
        String pincode = String.valueOf(borrowerInfo.getOrDefault("pincode", ""));
        String phone = (String) borrowerInfo.getOrDefault("phoneNumber", "");
        String dob = (String) borrowerInfo.getOrDefault("dob", "");
        String gender = (String) borrowerInfo.getOrDefault("gender", "");
        String aadhaar = (String) borrowerInfo.getOrDefault("aadhaarNumber", "");
        String pan = (String) borrowerInfo.getOrDefault("panNumber", "");
        String voterId = (String) borrowerInfo.getOrDefault("voterId", "");

        return "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:ns=\"" + EQUIFAX_NS + "\">" +
                "<soapenv:Header/>" +
                "<soapenv:Body>" +
                "<ns:InquiryRequest>" +
                "<ns:RequestHeader>" +
                "<ns:CustomerId>" + escapeXml(config.getCustomerId()) + "</ns:CustomerId>" +
                "<ns:UserId>" + escapeXml(config.getUserId()) + "</ns:UserId>" +
                "<ns:Password>" + escapeXml(config.getPassword()) + "</ns:Password>" +
                "<ns:MemberNumber>" + escapeXml(config.getMemberNumber()) + "</ns:MemberNumber>" +
                "<ns:SecurityCode>" + escapeXml(config.getSecurityCode()) + "</ns:SecurityCode>" +
                "<ns:ProductVersion>" + escapeXml(config.getProductVersion()) + "</ns:ProductVersion>" +
                "<ns:ReportFormat>" + escapeXml(config.getReportFormat()) + "</ns:ReportFormat>" +
                "<ns:ProductCode>" + escapeXml(config.getProductCode()) + "</ns:ProductCode>" +
                "</ns:RequestHeader>" +
                "<ns:RequestBody>" +
                "<ns:InquiryPurpose>00</ns:InquiryPurpose>" +
                "<ns:TransactionAmount>400000</ns:TransactionAmount>" +
                "<ns:AdditionalSearchField></ns:AdditionalSearchField>" +
                "<ns:FullName>" + escapeXml(name) + "</ns:FullName>" +
                "<ns:FirstName>" + escapeXml(name) + "</ns:FirstName>" +
                "<ns:LastName></ns:LastName>" +
                "<ns:InquiryAddresses>" +
                "<ns:InquiryAddress seq=\"1\">" +
                "<ns:AddressLine>" + escapeXml(address) + "</ns:AddressLine>" +
                "<ns:City></ns:City>" +
                "<ns:State>" + escapeXml(state) + "</ns:State>" +
                "<ns:Postal>" + escapeXml(pincode) + "</ns:Postal>" +
                "</ns:InquiryAddress>" +
                "</ns:InquiryAddresses>" +
                "<ns:InquiryPhones>" +
                "<ns:InquiryPhone seq=\"1\">" +
                "<ns:Number>" + escapeXml(phone) + "</ns:Number>" +
                "<ns:PhoneType>M</ns:PhoneType>" +
                "</ns:InquiryPhone>" +
                "</ns:InquiryPhones>" +
                "<ns:DOB>" + escapeXml(dob) + "</ns:DOB>" +
                "<ns:Gender>" + escapeXml(gender) + "</ns:Gender>" +
                "<ns:NationalIdCard>" + escapeXml(aadhaar) + "</ns:NationalIdCard>" +
                "<ns:RationCard></ns:RationCard>" +
                "<ns:PANId>" + escapeXml(pan) + "</ns:PANId>" +
                "<ns:PassportId></ns:PassportId>" +
                "<ns:VoterId>" + escapeXml(voterId) + "</ns:VoterId>" +
                "<ns:DriverLicense></ns:DriverLicense>" +
                "<ns:InquiryFieldsDsv></ns:InquiryFieldsDsv>" +
                "</ns:RequestBody>" +
                "</ns:InquiryRequest>" +
                "</soapenv:Body>" +
                "</soapenv:Envelope>";
    }

    /**
     * Parse Equifax XML response using XPath — adapted from legacy EquifaxServiceFacadeImpl.extractReport()
     * Package-visible for P0-3 goldens.
     */
    BureauPullResult parseEquifaxResponse(String responseXml, String transactionId) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new InputSource(new StringReader(responseXml)));

            XPathFactory xpathFactory = XPathFactory.newInstance();
            XPath xpath = xpathFactory.newXPath();
            xpath.setNamespaceContext(new NamespaceContext() {
                public String getNamespaceURI(String prefix) {
                    if ("sch".equals(prefix)) return EQUIFAX_NS;
                    return XMLConstants.NULL_NS_URI;
                }
                public String getPrefix(String namespaceURI) { return null; }
                public Iterator<String> getPrefixes(String namespaceURI) { return null; }
            });

            // Extract header info (PCS uses InquiryResponseHeader; legacy uses ResponseHeader)
            String scoreValue = getTagValue(doc, xpath, "//sch:Score/sch:Value");
            String successCode = firstNonBlank(
                    getTagValue(doc, xpath, "//sch:InquiryResponseHeader/sch:SuccessCode"),
                    getTagValue(doc, xpath, "//sch:ResponseHeader/sch:SuccessCode"));
            String errorMsg = firstNonBlank(
                    getTagValue(doc, xpath, "//sch:InquiryResponseHeader/sch:ErrorMessage"),
                    getTagValue(doc, xpath, "//sch:ResponseHeader/sch:ErrorMessage"));
            String panId = getTagValue(doc, xpath, "//sch:PANId");
            String fullName = getTagValue(doc, xpath, "//sch:FullName");
            String scoreName = getTagValue(doc, xpath, "//sch:Score/sch:Name");

            int creditScore = 0;
            if (scoreValue != null && !scoreValue.isEmpty()) {
                try {
                    creditScore = Integer.parseInt(scoreValue.trim());
                } catch (NumberFormatException e) {
                    log.warn("[Equifax] Could not parse score value: {}", scoreValue);
                }
            } else if (errorMsg != null && errorMsg.contains("Consumer record not found")) {
                creditScore = -1; // No record found — legacy convention
            }

            boolean success = "1".equals(successCode) && (creditScore > 0 || creditScore == -1);

            Map<String, Object> reportData = new LinkedHashMap<>();
            reportData.put("creditScore", creditScore);
            if (scoreName != null && !scoreName.isBlank()) {
                reportData.put("scoreVersion", scoreName.trim());
            }
            reportData.put("panId", panId != null ? panId : "");
            reportData.put("fullName", fullName != null ? fullName : "");

            if (success && creditScore > 0) {
                // Extract account summary (aggregates + per-account list for Phase C1)
                extractAccountSummary(doc, xpath, reportData);
                // Extract enquiry summary
                extractEnquirySummary(doc, xpath, reportData);
                // Extract account details count
                extractAccountDetails(doc, xpath, reportData);
                // Phase C1: per-tradeline + inquiries + parser version
                EquifaxBureauAccountExtractor.enrichFromDocument(doc, xpath, reportData);
            }

            if (creditScore == -1) {
                reportData.put("noRecordFound", true);
                reportData.put("parserVersion", EquifaxBureauAccountExtractor.PARSER_VERSION);
                reportData.put("tradelineExtractionStatus", "EMPTY");
                reportData.put("tradelinesPresent", true);
                reportData.put("accounts", List.of());
            }

            return new BureauPullResult(success, creditScore, reportData, transactionId,
                    success ? null : (errorMsg != null ? errorMsg : "Bureau pull failed"));

        } catch (Exception e) {
            log.error("[Equifax] Failed to parse XML response: {}", e.getMessage(), e);
            return new BureauPullResult(false, 0, null, transactionId,
                    "Failed to parse Equifax response: " + e.getMessage());
        }
    }

    private void extractAccountSummary(Document doc, XPath xpath, Map<String, Object> reportData) {
        try {
            NodeList accounts = (NodeList) xpath.evaluate("//sch:Account", doc, XPathConstants.NODESET);
            int totalAccounts = accounts.getLength();
            int activeAccounts = 0;
            int closedAccounts = 0;
            int overdueAccounts = 0;
            BigDecimal totalOutstanding = BigDecimal.ZERO;
            BigDecimal totalSanctioned = BigDecimal.ZERO;
            boolean suitFiled = false;

            for (int i = 0; i < totalAccounts; i++) {
                Element account = (Element) accounts.item(i);
                String balance = getTagValueWithElement(account, xpath, "./sch:Balance");
                String sanctioned = getTagValueWithElement(account, xpath, "./sch:SanctionAmount");
                String accountStatus = getTagValueWithElement(account, xpath, "./sch:AccountStatus");
                String suitFiledStatus = getTagValueWithElement(account, xpath, "./sch:SuitFiledStatus");

                if (balance != null && !balance.isEmpty()) {
                    try {
                        totalOutstanding = totalOutstanding.add(new BigDecimal(balance));
                    } catch (NumberFormatException ignored) {}
                }
                if (sanctioned != null && !sanctioned.isEmpty()) {
                    try {
                        totalSanctioned = totalSanctioned.add(new BigDecimal(sanctioned));
                    } catch (NumberFormatException ignored) {}
                }
                if (accountStatus != null && accountStatus.toLowerCase(java.util.Locale.ROOT).contains("closed")) {
                    closedAccounts++;
                } else {
                    activeAccounts++;
                }
                if (balance != null && !balance.isEmpty()) {
                    try {
                        if (new BigDecimal(balance).compareTo(BigDecimal.ZERO) > 0
                                && "Overdue".equalsIgnoreCase(accountStatus)) {
                            overdueAccounts++;
                        }
                    } catch (NumberFormatException ignored) {}
                }
                if ("Yes".equalsIgnoreCase(suitFiledStatus)) {
                    suitFiled = true;
                }
            }

            reportData.put("totalAccounts", totalAccounts);
            reportData.put("activeAccounts", activeAccounts);
            reportData.put("closedAccounts", closedAccounts);
            reportData.put("overdueAccounts", overdueAccounts);
            reportData.put("totalOutstanding", totalOutstanding.longValue());
            reportData.put("totalSanctioned", totalSanctioned.longValue());
            reportData.put("suitFiled", suitFiled);

        } catch (Exception e) {
            log.warn("[Equifax] Error extracting account summary: {}", e.getMessage());
        }
    }

    private void extractEnquirySummary(Document doc, XPath xpath, Map<String, Object> reportData) {
        try {
            String past30Days = getTagValue(doc, xpath, "//sch:EnquirySummary/sch:Past30Days");
            String past12Months = getTagValue(doc, xpath, "//sch:EnquirySummary/sch:Past12Months");
            String past24Months = getTagValue(doc, xpath, "//sch:EnquirySummary/sch:Past24Months");
            String recent = getTagValue(doc, xpath, "//sch:EnquirySummary/sch:Recent");

            reportData.put("enquiryAge30Days", parseIntSafe(past30Days));
            reportData.put("recentEnquiries", parseIntSafe(recent));
            reportData.put("enquiries12Months", parseIntSafe(past12Months));
            reportData.put("enquiries24Months", parseIntSafe(past24Months));
        } catch (Exception e) {
            log.warn("[Equifax] Error extracting enquiry summary: {}", e.getMessage());
        }
    }

    private void extractAccountDetails(Document doc, XPath xpath, Map<String, Object> reportData) {
        try {
            String dpd30 = getTagValue(doc, xpath, "//sch:OtherKeyInd/sch:DPD30Plus");
            String dpd60 = getTagValue(doc, xpath, "//sch:OtherKeyInd/sch:DPD60Plus");
            String dpd90 = getTagValue(doc, xpath, "//sch:OtherKeyInd/sch:DPD90Plus");

            reportData.put("dpd30Plus", parseIntSafe(dpd30));
            reportData.put("dpd60Plus", parseIntSafe(dpd60));
            reportData.put("dpd90Plus", parseIntSafe(dpd90));
        } catch (Exception e) {
            log.warn("[Equifax] Error extracting DPD details: {}", e.getMessage());
        }
    }

    /** Simulated fallback — only when {@code los.integration.equifax.simulation=true}. */
    private BureauPullResult simulatedFallback(Map<String, Object> borrowerInfo, String transactionId) {
        String pan = (String) borrowerInfo.getOrDefault("panNumber", "");
        if (pan.isEmpty()) {
            return new BureauPullResult(false, 0, null, transactionId, "PAN number is required for bureau pull");
        }

        String sampleXml = loadSimulatedEquifaxXml();
        if (sampleXml != null && !sampleXml.isBlank()) {
            BureauPullResult parsed = parseEquifaxResponse(sampleXml, transactionId);
            if (parsed.success() && parsed.reportData() != null) {
                Map<String, Object> reportData = stampSimulatedProvenance(parsed.reportData());
                attachSimulatedBureauReport(borrowerInfo);
                log.info("[Equifax] Simulated pull using sample XML — score={}, accounts={}, status={}",
                        parsed.creditScore(),
                        reportData.get("totalAccounts"),
                        reportData.get("tradelineExtractionStatus"));
                return new BureauPullResult(true, parsed.creditScore(), reportData, transactionId, null);
            }
            log.warn("[Equifax] Sample XML parse failed ({}) — falling back to aggregates-only simulation",
                    parsed.errorMessage());
        }

        int creditScore = 720;
        Map<String, Object> reportData = stampSimulatedProvenance(new LinkedHashMap<>());
        reportData.put("creditScore", creditScore);
        reportData.put("scoreVersion", "ERS 3.0");
        reportData.put("totalAccounts", 5);
        reportData.put("activeAccounts", 3);
        reportData.put("closedAccounts", 2);
        reportData.put("overdueAccounts", 0);
        reportData.put("totalOutstanding", 450000);
        reportData.put("totalSanctioned", 1500000);
        reportData.put("recentEnquiries", 2);
        reportData.put("enquiryAge30Days", 1);
        reportData.put("dpd30Plus", 0);
        reportData.put("dpd60Plus", 0);
        reportData.put("dpd90Plus", 0);
        reportData.put("suitFiled", false);
        EquifaxBureauAccountExtractor.markSimulatedMissing(reportData);

        attachSimulatedBureauReport(borrowerInfo);

        return new BureauPullResult(true, creditScore, reportData, transactionId, null);
    }

    private static Map<String, Object> stampSimulatedProvenance(Map<String, Object> source) {
        Map<String, Object> reportData = new LinkedHashMap<>(source != null ? source : Map.of());
        reportData.put("simulated", true);
        reportData.put("providerAvailability", AVAILABILITY_EXPLICIT_SIMULATION);
        reportData.put("dataProvenance", PROVENANCE_SIMULATED);
        reportData.put("simulatedSource", "simulated/equifax-sample-inquiry-response.xml");
        return reportData;
    }

    private String loadSimulatedEquifaxXml() {
        try {
            ClassPathResource resource = new ClassPathResource("simulated/equifax-sample-inquiry-response.xml");
            try (InputStream in = resource.getInputStream()) {
                return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.warn("[Equifax] Could not load simulated/equifax-sample-inquiry-response.xml: {}", e.getMessage());
            return null;
        }
    }

    private void attachSimulatedBureauReport(Map<String, Object> borrowerInfo) {
        Object appIdRaw = borrowerInfo != null ? borrowerInfo.get("applicationId") : null;
        if (appIdRaw == null || appIdRaw.toString().isBlank()) {
            log.warn("[Equifax] Simulated report HTML not attached — applicationId missing from borrowerInfo");
            return;
        }
        try {
            UUID applicationId = UUID.fromString(appIdRaw.toString().trim());
            byte[] html = loadSimulatedBureauHtml();
            if (html == null || html.length == 0) {
                log.warn("[Equifax] Simulated sample-bureau-report.html is empty or missing");
                return;
            }
            documentService.storeDocumentBytes(
                    applicationId,
                    "BUREAU_REPORT",
                    "sample-bureau-report.html",
                    "text/html",
                    html);
            log.info("[Equifax] Attached simulated BUREAU_REPORT HTML for application {}", applicationId);
        } catch (Exception e) {
            log.warn("[Equifax] Failed to attach simulated BUREAU_REPORT: {}", e.getMessage());
        }
    }

    private byte[] loadSimulatedBureauHtml() {
        try {
            ClassPathResource resource = new ClassPathResource("simulated/sample-bureau-report.html");
            try (InputStream in = resource.getInputStream()) {
                return in.readAllBytes();
            }
        } catch (Exception e) {
            log.warn("[Equifax] Could not load simulated/sample-bureau-report.html: {}", e.getMessage());
            return null;
        }
    }

    private String getTagValue(Document doc, XPath xpath, String expression) {
        try {
            return (String) xpath.evaluate(expression, doc, XPathConstants.STRING);
        } catch (Exception e) {
            return null;
        }
    }

    private String getTagValueWithElement(Element element, XPath xpath, String expression) {
        try {
            return (String) xpath.evaluate(expression, element, XPathConstants.STRING);
        } catch (Exception e) {
            return null;
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        if (b != null && !b.isBlank()) {
            return b.trim();
        }
        return null;
    }

    private int parseIntSafe(String value) {
        if (value == null || value.isEmpty()) return 0;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String escapeXml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private String truncateForAudit(String text) {
        if (text == null) return null;
        return text.length() > 10000 ? text.substring(0, 10000) + "... [TRUNCATED]" : text;
    }

    private void saveAuditLog(String provider, String apiName, String request, String response,
                               String status, Integer httpStatus, String errorMsg, String txnId,
                               Instant reqTime, Instant resTime, Long durationMs) {
        try {
            ApiAuditLog audit = ApiAuditLog.builder()
                    .providerName(provider)
                    .apiName(apiName)
                    .requestPayload(truncateForAudit(request))
                    .responsePayload(response)
                    .status(status)
                    .httpStatusCode(httpStatus)
                    .errorMessage(errorMsg)
                    .transactionId(txnId)
                    .requestTime(reqTime)
                    .responseTime(resTime)
                    .durationMs(durationMs)
                    .build();
            apiAuditLogRepository.save(audit);
        } catch (Exception e) {
            log.error("[Equifax] Failed to save audit log: {}", e.getMessage());
        }
    }
}
