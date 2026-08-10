package com.los.plp.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.plp.config.PlpProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads/writes borrower-scoped PLP resources (invoices, finance requests, invoice-discounting loans and
 * repayments) on behalf of a LOS borrower. Uses the shared PLP machine identity ({@code los.plp.integration-*},
 * a PLP lender role) so the gateway authorizes access to any {@code borrowerId}. The borrower is identified by
 * the PLP borrower UUID that LOS captured when the borrower's application was synced ({@code plp_borrower_id}).
 *
 * <p>All methods throw {@link PlpIntegrationException} on transport/HTTP/parse failures so the calling service
 * can degrade gracefully (PLP off / unreachable → empty invoice-discounting view).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlpBorrowerClient {

    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAPS = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    @Qualifier("plpRestClient")
    private final RestClient plpRestClient;
    private final PlpProperties plpProperties;
    private final PlpIntegrationClient plpIntegrationClient;
    private final ObjectMapper objectMapper;

    public boolean isEnabled() {
        return plpProperties.isEnabled();
    }

    /** All invoices visible to a borrower. PLP returns a raw JSON array (not wrapped in {status,data}). */
    public List<Map<String, Object>> listInvoices(UUID plpBorrowerId) {
        return listInvoices(plpBorrowerId, null);
    }

    public List<Map<String, Object>> listInvoices(UUID plpBorrowerId, String flowType) {
        String path = "/api/v1/invoices/borrower/" + plpBorrowerId;
        if (flowType != null && !flowType.isBlank()) {
            path += "?flowType=" + flowType.trim();
        }
        String raw = get(path);
        return parseList(raw);
    }

    /** Borrower creates a seller-initiated invoice (SBD/PO). */
    public Map<String, Object> createBorrowerInvoice(UUID plpBorrowerId, Map<String, Object> invoiceBody) {
        LinkedHashMap<String, Object> body = new LinkedHashMap<>(invoiceBody);
        body.putIfAbsent("borrowerId", plpBorrowerId.toString());
        String raw = post("/api/v1/invoices", body);
        return parseInvoice(raw);
    }

    /** Digital invoice bytes for a PLP invoice (lender machine identity). */
    public DigitalInvoiceFile downloadDigitalInvoice(UUID invoiceId) {
        String bearer = "Bearer " + plpIntegrationClient.currentBearerToken();
        String path = "/api/v1/invoices/" + invoiceId + "/digital-invoice/download";
        try {
            return plpRestClient.get()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, bearer)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().isError()) {
                            throw new PlpIntegrationException(
                                    "PLP HTTP " + response.getStatusCode().value() + " on " + path);
                        }
                        byte[] body;
                        try (InputStream in = response.getBody()) {
                            body = in != null ? in.readAllBytes() : new byte[0];
                        } catch (IOException e) {
                            throw new PlpIntegrationException("PLP: failed to read digital invoice bytes: " + e.getMessage());
                        }
                        String ct = response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
                        String cd = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
                        return new DigitalInvoiceFile(body, ct, filenameFromContentDisposition(cd));
                    });
        } catch (RestClientResponseException e) {
            log.warn("[PLP][borrower] GET {} -> HTTP {} {}", path, e.getStatusCode(),
                    truncate(e.getResponseBodyAsString()));
            throw new PlpIntegrationException("PLP HTTP " + e.getStatusCode() + " on " + path);
        } catch (PlpIntegrationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[PLP][borrower] GET {} failed: {}", path, e.getMessage());
            throw new PlpIntegrationException(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    public record DigitalInvoiceFile(byte[] body, String contentType, String fileName) {}

    /** Upload digital invoice bytes for a PLP invoice (lender machine identity). */
    public void uploadDigitalInvoice(UUID invoiceId, byte[] bytes, String filename, String contentType) {
        String bearer = "Bearer " + plpIntegrationClient.currentBearerToken();
        String path = "/api/v1/invoices/" + invoiceId + "/digital-invoice";
        org.springframework.core.io.ByteArrayResource resource =
                new org.springframework.core.io.ByteArrayResource(bytes) {
                    @Override
                    public String getFilename() {
                        return filename != null && !filename.isBlank() ? filename : "invoice.pdf";
                    }
                };
        org.springframework.util.LinkedMultiValueMap<String, Object> body =
                new org.springframework.util.LinkedMultiValueMap<>();
        body.add("file", resource);
        try {
            plpRestClient.post()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, bearer)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            log.warn("[PLP][borrower] POST {} -> HTTP {} {}", path, e.getStatusCode(),
                    truncate(e.getResponseBodyAsString()));
            throw new PlpIntegrationException("PLP HTTP " + e.getStatusCode() + " on " + path);
        } catch (PlpIntegrationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[PLP][borrower] POST {} failed: {}", path, e.getMessage());
            throw new PlpIntegrationException(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    /** All loans for a borrower (lender scope passes borrowerId as a query filter). PLP wraps in {status,data}. */
    public List<Map<String, Object>> listLoans(UUID plpBorrowerId) {
        String raw = get("/api/v1/loans?borrowerId=" + plpBorrowerId);
        return parseDataList(raw);
    }

    /** Purchase-flow: borrower accepts an anchor-eligible invoice before requesting finance. */
    public Map<String, Object> acceptInvoice(UUID plpBorrowerId, UUID invoiceId) {
        String raw = post("/api/v1/invoices/" + invoiceId + "/borrower-accept?borrowerId=" + plpBorrowerId, Map.of());
        return parseInvoice(raw);
    }

    /** Soft-delete an SBD/PO invoice before finance (program must allow invoice delete). */
    public void deleteBorrowerInvoice(UUID plpBorrowerId, UUID invoiceId) {
        delete("/api/v1/invoices/" + invoiceId + "/borrower-delete?borrowerId=" + plpBorrowerId);
    }

    /** Request invoice-discounting finance against a single invoice; returns the created PLP loan. */
    public Map<String, Object> requestFinance(UUID plpBorrowerId, UUID invoiceId, BigDecimal amount, UUID programId) {
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("borrowerId", plpBorrowerId.toString());
        body.put("invoiceId", invoiceId.toString());
        body.put("productType", "INVOICE_DISCOUNTING");
        body.put("requestedAmount", amount);
        if (programId != null) {
            body.put("programId", programId.toString());
        }
        String raw = post("/api/v1/loans", body);
        return parseData(raw);
    }

    /** Repayment history for a loan (newest first). */
    public List<Map<String, Object>> listLoanRepayments(UUID plpLoanId) {
        String raw = get("/api/v1/loans/" + plpLoanId + "/repayments");
        return parseDataList(raw);
    }

    /** Record a repayment against an invoice-discounting loan; returns the updated PLP loan. */
    public Map<String, Object> repay(UUID plpLoanId, BigDecimal amount) {
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amount);
        String raw = post("/api/v1/loans/" + plpLoanId + "/repay", body);
        return parseData(raw);
    }

    public Map<String, Object> getPaymentProfile(UUID plpBorrowerId) {
        return parseData(get("/api/v1/portal/borrower/payments/checkout/payment-method?borrowerId=" + plpBorrowerId));
    }

    /** Effective repayment method for invoice discounting (SMART_COLLECT or PAYU_PG). */
    public String getPaymentMethod(UUID plpBorrowerId) {
        Map<String, Object> data = getPaymentProfile(plpBorrowerId);
        Object pm = data.get("paymentMethod");
        return pm != null ? pm.toString() : "SMART_COLLECT";
    }

    public List<Map<String, Object>> listEligibleInvoices(UUID plpBorrowerId) {
        return parseDataList(
                get("/api/v1/portal/borrower/payments/checkout/eligible-invoices?borrowerId=" + plpBorrowerId));
    }

    public List<Map<String, Object>> listUnfinancedRepayments(UUID plpBorrowerId, UUID invoiceId) {
        return parseDataList(get(
                "/api/v1/portal/borrower/invoices/" + invoiceId + "/unfinanced-repayments?borrowerId=" + plpBorrowerId));
    }

    public List<Map<String, Object>> listCreditNotes(UUID plpBorrowerId, String status) {
        String path = "/api/v1/portal/borrower/credit-notes?borrowerId=" + plpBorrowerId;
        if (status != null && !status.isBlank()) {
            path += "&status=" + status.trim();
        }
        return parseDataList(get(path));
    }

    public Map<String, Object> getCreditNote(UUID plpBorrowerId, UUID creditNoteId) {
        return parseData(get(
                "/api/v1/portal/borrower/credit-notes/" + creditNoteId + "?borrowerId=" + plpBorrowerId));
    }

    public List<Map<String, Object>> listPaymentCart(UUID plpBorrowerId) {
        return parseDataList(get("/api/v1/portal/borrower/payments/checkout/lines?borrowerId=" + plpBorrowerId));
    }

    public long paymentCartCount(UUID plpBorrowerId) {
        String raw = get("/api/v1/portal/borrower/payments/checkout/count?borrowerId=" + plpBorrowerId);
        Map<String, Object> wrapper = parseWrapper(raw);
        Object data = wrapper.get("data");
        if (data instanceof Number num) {
            return num.longValue();
        }
        try {
            return data != null ? Long.parseLong(data.toString()) : 0L;
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public void addPaymentCartLine(UUID plpBorrowerId, UUID invoiceId) {
        post(
                "/api/v1/portal/borrower/payments/checkout/lines?borrowerId=" + plpBorrowerId,
                Map.of("invoiceId", invoiceId.toString()));
    }

    public void addPaymentCartBulk(UUID plpBorrowerId, List<UUID> invoiceIds) {
        post(
                "/api/v1/portal/borrower/payments/checkout/lines/bulk?borrowerId=" + plpBorrowerId,
                Map.of("invoiceIds", invoiceIds.stream().map(UUID::toString).toList()));
    }

    public void removePaymentCartLine(UUID plpBorrowerId, UUID lineId) {
        delete("/api/v1/portal/borrower/payments/checkout/lines/" + lineId + "?borrowerId=" + plpBorrowerId);
    }

    public Map<String, Object> initiatePayuPayment(UUID plpBorrowerId) {
        return parseData(post(
                "/api/v1/portal/borrower/payments/payu/initiate?borrowerId=" + plpBorrowerId,
                Map.of("portalSource", "LOS")));
    }

    /** All sub-programs (lender machine identity). */
    public List<Map<String, Object>> listSubPrograms() {
        String raw = get("/api/v1/sub-programs");
        return parseDataList(raw);
    }

    /** Borrower memberships on a sub-program (includes per-borrower term overrides when set). */
    public List<Map<String, Object>> listSubProgramBorrowers(UUID subProgramId) {
        String raw = get("/api/v1/sub-programs/" + subProgramId + "/borrowers");
        return parseDataList(raw);
    }

    /** Program detail including aggregate limits ({@code GET /api/v1/programs/{id}}). */
    public Map<String, Object> getProgram(UUID programId) {
        String raw = get("/api/v1/programs/" + programId);
        return parseData(raw);
    }

    public Optional<Map<String, Object>> getEarlyPayTodayParameter(UUID plpBorrowerId, UUID subProgramId) {
        String path = "/api/v1/invoices/early-pay/parameters/today?subProgramId="
                + subProgramId + "&borrowerId=" + plpBorrowerId;
        return Optional.of(parseData(get(path)));
    }

    public Map<String, Object> createEarlyPayRequest(UUID plpBorrowerId, Map<String, Object> body) {
        String path = "/api/v1/invoices/early-pay/requests?borrowerId=" + plpBorrowerId;
        return parseData(post(path, body));
    }

    /**
     * Borrower membership limits for a sub-program, or empty when the borrower is not enrolled.
     */
    public Optional<Map<String, Object>> getBorrowerLimitSummary(UUID subProgramId, UUID borrowerId) {
        try {
            String raw = get("/api/v1/sub-programs/" + subProgramId + "/borrowers/" + borrowerId + "/limit-summary");
            Map<String, Object> data = parseData(raw);
            return data.isEmpty() ? Optional.empty() : Optional.of(data);
        } catch (PlpIntegrationException e) {
            if (e.getMessage() != null && e.getMessage().contains("HTTP 403")) {
                return Optional.empty();
            }
            if (e.getMessage() != null && e.getMessage().contains("HTTP 404")) {
                return Optional.empty();
            }
            if (e.getMessage() != null && e.getMessage().contains("HTTP 500")) {
                return Optional.empty();
            }
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("not enrolled")) {
                return Optional.empty();
            }
            throw e;
        }
    }

    // ----- transport -----

    private String get(String path) {
        String bearer = "Bearer " + plpIntegrationClient.currentBearerToken();
        try {
            return plpRestClient.get()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, bearer)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            log.warn("[PLP][borrower] GET {} -> HTTP {} {}", path, e.getStatusCode(),
                    truncate(e.getResponseBodyAsString()));
            throw new PlpIntegrationException("PLP HTTP " + e.getStatusCode() + " on " + path);
        } catch (PlpIntegrationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[PLP][borrower] GET {} failed: {}", path, e.getMessage());
            throw new PlpIntegrationException(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private String post(String path, Object body) {
        String bearer = "Bearer " + plpIntegrationClient.currentBearerToken();
        try {
            return plpRestClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, bearer)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            log.warn("[PLP][borrower] POST {} -> HTTP {} {}", path, e.getStatusCode(),
                    truncate(e.getResponseBodyAsString()));
            throw new PlpIntegrationException("PLP HTTP " + e.getStatusCode() + ": " + safeMessage(e.getResponseBodyAsString()));
        } catch (PlpIntegrationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[PLP][borrower] POST {} failed: {}", path, e.getMessage());
            throw new PlpIntegrationException(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private void delete(String path) {
        String bearer = "Bearer " + plpIntegrationClient.currentBearerToken();
        try {
            plpRestClient.delete()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, bearer)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            log.warn("[PLP][borrower] DELETE {} -> HTTP {} {}", path, e.getStatusCode(),
                    truncate(e.getResponseBodyAsString()));
            throw new PlpIntegrationException("PLP HTTP " + e.getStatusCode() + " on " + path);
        } catch (Exception e) {
            log.warn("[PLP][borrower] DELETE {} failed: {}", path, e.getMessage());
            throw new PlpIntegrationException(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private List<Map<String, Object>> parseList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            String trimmed = raw.trim();
            if (trimmed.startsWith("[")) {
                return objectMapper.readValue(raw, LIST_OF_MAPS);
            }
            if (trimmed.startsWith("{")) {
                Map<String, Object> wrapper = objectMapper.readValue(raw, MAP);
                Object data = wrapper.get("data");
                if (data instanceof List<?> list) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> rows = (List<Map<String, Object>>) list;
                    return rows;
                }
                return List.of();
            }
            return List.of();
        } catch (Exception e) {
            throw new PlpIntegrationException("PLP: failed to parse invoice list: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseDataList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            Map<String, Object> wrapper = objectMapper.readValue(raw, MAP);
            Object data = wrapper.get("data");
            if (data instanceof List<?> list) {
                return (List<Map<String, Object>>) list;
            }
            return List.of();
        } catch (Exception e) {
            throw new PlpIntegrationException("PLP: failed to parse loan list: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseData(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> wrapper = objectMapper.readValue(raw, MAP);
            Object data = wrapper.get("data");
            if (data instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            return wrapper;
        } catch (Exception e) {
            throw new PlpIntegrationException("PLP: failed to parse response: " + e.getMessage());
        }
    }

    private Map<String, Object> parseWrapper(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, MAP);
        } catch (Exception e) {
            throw new PlpIntegrationException("PLP: failed to parse response: " + e.getMessage());
        }
    }

    /** Invoice endpoints return the entity directly (not wrapped in {status,data}). */
    private Map<String, Object> parseInvoice(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, MAP);
        } catch (Exception e) {
            throw new PlpIntegrationException("PLP: failed to parse invoice response: " + e.getMessage());
        }
    }

    private static String safeMessage(String body) {
        if (body == null || body.isBlank()) {
            return "request rejected";
        }
        return truncate(body);
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 512 ? s : s.substring(0, 512) + "...";
    }

    private static String filenameFromContentDisposition(String cd) {
        if (cd == null || cd.isBlank()) {
            return "digital-invoice";
        }
        int fn = cd.toLowerCase(java.util.Locale.ROOT).indexOf("filename=");
        if (fn < 0) {
            return "digital-invoice";
        }
        String rest = cd.substring(fn + "filename=".length()).trim();
        if (rest.startsWith("\"")) {
            int end = rest.indexOf('"', 1);
            return end > 0 ? rest.substring(1, end) : rest.replace("\"", "");
        }
        int semi = rest.indexOf(';');
        return (semi > 0 ? rest.substring(0, semi) : rest).trim();
    }
}
