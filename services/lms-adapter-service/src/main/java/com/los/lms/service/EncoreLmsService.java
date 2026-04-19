package com.los.lms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.los.lms.config.EncoreProperties;
import com.los.lms.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Encore LMS Integration Service — adapted from legacy EncoreServiceFacadeImpl.
 *
 * Provides:
 *   - openLoanAccount: Create loan account in Encore after disbursement
 *   - disburse: Post disbursement transaction to Encore
 *   - repay: Post repayment transaction to Encore
 *   - findSummaries: Get loan account summaries from Encore
 *   - findRepaymentSchedule: Get repayment schedule from Encore
 *   - reverseTransaction: Reverse a transaction in Encore
 *   - getAccountStatement: Get account statement from Encore
 *
 * Falls back to simulated in-memory responses when Encore credentials are not configured.
 *
 * Legacy reference:
 *   - bl-core/.../facade/impl/EncoreServiceFacadeImpl.java
 *   - bl-core/.../facade/impl/EncoreHTTPClientServiceFacadeImpl.java
 *   - prod-encore.properties / dev-encore.properties
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EncoreLmsService {

    private final EncoreHttpClient encoreHttpClient;
    private final EncoreProperties encoreProperties;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Open a loan account in Encore LMS.
     * Adapted from legacy EncoreServiceFacadeImpl.openLoanAccount()
     *
     * @return Encore account ID (e.g., "000010000123")
     */
    public String openLoanAccount(LoanHandoverRequest request) {
        if (!encoreHttpClient.isConfigured()) {
            log.info("[Encore-SIM] Simulated openLoanAccount for {}", request.getApplicationNumber());
            return "SIM-" + request.getApplicationNumber();
        }

        try {
            // Build LoanOdAccountWSDto-equivalent JSON — from legacy code
            ObjectNode loanAccount = objectMapper.createObjectNode();

            LocalDate disbDate = LocalDate.now();
            LocalDate disbByDate = disbDate.plusMonths(1);

            loanAccount.put("openedOnDate", disbDate.format(DATE_FORMAT));
            loanAccount.put("disbursementByDate", disbByDate.format(DATE_FORMAT));
            loanAccount.put("amountMagnitude", request.getSanctionedAmount().toPlainString());
            loanAccount.put("branchCode", encoreProperties.getAdminBranch());
            loanAccount.put("currencyCode", encoreProperties.getCurrency());

            // Customer details
            loanAccount.put("customer1FirstName", request.getBorrowerName() != null ? request.getBorrowerName() : "");
            loanAccount.put("customer1MiddleName", "");
            loanAccount.put("customer1LastName", "");
            loanAccount.put("customerId1", request.getApplicationNumber());

            // Loan parameters
            loanAccount.put("migrated", "false");
            loanAccount.put("normalInterestRate", request.getInterestRate().toPlainString());
            loanAccount.put("operationalStatus", "active");
            loanAccount.put("penalInterestRate", "0");
            loanAccount.put("productCode", request.getProductCode() != null ? request.getProductCode() : "");
            loanAccount.put("productType", encoreProperties.getLoanProductType());
            loanAccount.put("tenureMagnitude", String.valueOf(request.getTenureMonths()));
            loanAccount.put("tenureUnit", "Month");
            loanAccount.put("securityDepositAllowed", true);

            // Moratorium defaults
            loanAccount.put("moratoriumPeriodUnit", "Month");
            loanAccount.put("moratoriumPeriodMagnitude", "0");
            loanAccount.put("moratoriumType", "None");

            String requestBody = objectMapper.writeValueAsString(loanAccount);

            Map<String, String> params = new LinkedHashMap<>();
            params.put("transactionId", "BL-" + UUID.randomUUID().toString().substring(0, 8));

            String response = encoreHttpClient.httpPost(
                    encoreProperties.getApi().getCreateLoanAccount(), params, requestBody);

            // Parse response to extract account ID
            JsonNode responseJson = objectMapper.readTree(response);
            if (responseJson.has("accountId")) {
                String accountId = responseJson.get("accountId").asText();
                log.info("[Encore] Loan account opened: {} for {}", accountId, request.getApplicationNumber());
                return accountId;
            } else if (responseJson.isTextual()) {
                log.info("[Encore] Loan account opened: {} for {}", response, request.getApplicationNumber());
                return response.replaceAll("\"", "");
            }

            log.warn("[Encore] Unexpected openLoanAccount response: {}", response);
            return "ENCORE-" + request.getApplicationNumber();

        } catch (Exception e) {
            log.error("[Encore] Failed to open loan account for {}: {}", request.getApplicationNumber(), e.getMessage(), e);
            throw new RuntimeException("Encore openLoanAccount failed: " + e.getMessage(), e);
        }
    }

    /**
     * Post disbursement transaction to Encore.
     * Adapted from legacy EncoreServiceFacadeImpl.disburse()
     */
    public String disburse(String encoreAccountId, LoanHandoverRequest request) {
        if (!encoreHttpClient.isConfigured()) {
            log.info("[Encore-SIM] Simulated disburse for account {}", encoreAccountId);
            return "SIM-TXN-" + UUID.randomUUID().toString().substring(0, 8);
        }

        try {
            String transactionId = "BL-" + UUID.randomUUID().toString().substring(0, 8);

            // Build transaction list — from legacy disburse() pattern
            ArrayNode transactions = objectMapper.createArrayNode();
            ObjectNode txn = objectMapper.createObjectNode();
            txn.put("accountId", encoreAccountId);
            txn.put("amount1", request.getSanctionedAmount().toPlainString());
            txn.put("description", "Disbursement");
            txn.put("valueDateStr", LocalDate.now().format(DATE_FORMAT));
            txn.put("transactionId", transactionId);
            txn.put("transactionName", "Disbursement");
            txn.put("instrument", "CASH");
            transactions.add(txn);

            String requestBody = objectMapper.writeValueAsString(transactions);
            String response = encoreHttpClient.httpPost(
                    encoreProperties.getApi().getPostTransactions(), null, requestBody);

            log.info("[Encore] Disbursement posted for account {}: txnId={}", encoreAccountId, transactionId);
            return transactionId;

        } catch (Exception e) {
            log.error("[Encore] Disbursement failed for account {}: {}", encoreAccountId, e.getMessage(), e);
            throw new RuntimeException("Encore disburse failed: " + e.getMessage(), e);
        }
    }

    /**
     * Post repayment transaction to Encore.
     * Adapted from legacy EncoreServiceFacadeImpl.repay()
     */
    public String repay(String encoreAccountId, BigDecimal amount, String repaymentType) {
        if (!encoreHttpClient.isConfigured()) {
            log.info("[Encore-SIM] Simulated repayment of {} for account {}", amount, encoreAccountId);
            return "SIM-RPY-" + UUID.randomUUID().toString().substring(0, 8);
        }

        try {
            String transactionId = "BL-RPY-" + UUID.randomUUID().toString().substring(0, 8);

            ArrayNode transactions = objectMapper.createArrayNode();
            ObjectNode txn = objectMapper.createObjectNode();
            txn.put("accountId", encoreAccountId);
            txn.put("amount1", amount.toPlainString());
            txn.put("description", repaymentType != null ? repaymentType : "ScheduledRepayment");
            txn.put("valueDateStr", LocalDate.now().format(DATE_FORMAT));
            txn.put("transactionId", transactionId);
            txn.put("transactionName", repaymentType != null ? repaymentType : "ScheduledRepayment");
            txn.put("instrument", "NEFT");
            transactions.add(txn);

            String requestBody = objectMapper.writeValueAsString(transactions);
            encoreHttpClient.httpPost(
                    encoreProperties.getApi().getPostTransactions(), null, requestBody);

            log.info("[Encore] Repayment posted for account {}: {} {}", encoreAccountId, amount, repaymentType);
            return transactionId;

        } catch (Exception e) {
            log.error("[Encore] Repayment failed for account {}: {}", encoreAccountId, e.getMessage(), e);
            throw new RuntimeException("Encore repay failed: " + e.getMessage(), e);
        }
    }

    /**
     * Find loan account summaries from Encore.
     * Adapted from legacy EncoreServiceFacadeImpl.findSummaries()
     */
    public List<Map<String, Object>> findSummaries(List<String> encoreAccountIds) {
        if (!encoreHttpClient.isConfigured()) {
            log.info("[Encore-SIM] Simulated findSummaries for {}", encoreAccountIds);
            return encoreAccountIds.stream().map(id -> {
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("accountId", id);
                summary.put("accountBalance", "500000");
                summary.put("normalInterestRate", "12.5");
                summary.put("operationalStatus", "active");
                summary.put("totalNormalInterestDue", "15000");
                summary.put("overdueAmount", "0");
                return summary;
            }).toList();
        }

        try {
            String accountIdJson = objectMapper.writeValueAsString(encoreAccountIds);
            Map<String, String> params = new LinkedHashMap<>();
            params.put("accountId", accountIdJson);
            params.put("ignoreTransactions", "false");

            String response = encoreHttpClient.httpGet(
                    encoreProperties.getApi().getFindSummaries(), params);

            // Parse JSON array response — from legacy toFindSummariesDto()
            JsonNode summaryArray = objectMapper.readTree(response);
            List<Map<String, Object>> results = new ArrayList<>();
            if (summaryArray.isArray()) {
                for (JsonNode node : summaryArray) {
                    Map<String, Object> summary = objectMapper.convertValue(node, Map.class);
                    results.add(summary);
                }
            }

            log.info("[Encore] Found {} summaries", results.size());
            return results;

        } catch (Exception e) {
            log.error("[Encore] findSummaries failed: {}", e.getMessage(), e);
            throw new RuntimeException("Encore findSummaries failed: " + e.getMessage(), e);
        }
    }

    /**
     * Find repayment schedule from Encore.
     * Adapted from legacy EncoreServiceFacadeImpl.findRepaymentSchedules()
     */
    public List<Map<String, Object>> findRepaymentSchedule(String encoreAccountId) {
        if (!encoreHttpClient.isConfigured()) {
            log.info("[Encore-SIM] Simulated findRepaymentSchedule for {}", encoreAccountId);
            return Collections.emptyList(); // Will use local calculation
        }

        try {
            List<String> accountIds = List.of(encoreAccountId);
            String accountIdJson = objectMapper.writeValueAsString(accountIds);
            Map<String, String> params = new LinkedHashMap<>();
            params.put("accountId", accountIdJson);
            params.put("ignoreTransactions", "false");

            String response = encoreHttpClient.httpGet(
                    encoreProperties.getApi().getFindSummaries(), params);

            JsonNode summaryArray = objectMapper.readTree(response);
            List<Map<String, Object>> schedules = new ArrayList<>();

            if (summaryArray.isArray() && summaryArray.size() > 0) {
                JsonNode summary = summaryArray.get(0);
                JsonNode repaymentSchedule = summary.path("repaymentSchedule");
                if (repaymentSchedule.isArray()) {
                    for (JsonNode entry : repaymentSchedule) {
                        Map<String, Object> scheduleEntry = new LinkedHashMap<>();
                        scheduleEntry.put("sequenceNum", entry.path("sequenceNum").asInt());
                        scheduleEntry.put("description", entry.path("description").asText());
                        scheduleEntry.put("installmentAmount", entry.path("amount1").asText());
                        scheduleEntry.put("amountDue", entry.path("amount3").asText());
                        scheduleEntry.put("valueDateStr", entry.path("valueDateStr").asText());
                        scheduleEntry.put("normalInterestRate", entry.path("part1").asDouble());
                        scheduleEntry.put("principalRate", entry.path("part2").asDouble());
                        scheduleEntry.put("penalInterestRate", entry.path("part3").asDouble());
                        scheduleEntry.put("balance", entry.path("amount2").asText());
                        schedules.add(scheduleEntry);
                    }
                }
            }

            log.info("[Encore] Found {} schedule entries for {}", schedules.size(), encoreAccountId);
            return schedules;

        } catch (Exception e) {
            log.error("[Encore] findRepaymentSchedule failed for {}: {}", encoreAccountId, e.getMessage(), e);
            throw new RuntimeException("Encore findRepaymentSchedule failed: " + e.getMessage(), e);
        }
    }

    /**
     * Reverse a transaction in Encore.
     * Adapted from legacy EncoreServiceFacadeImpl.reverse()
     */
    public void reverseTransaction(String transactionId, String transactionName, String userId) {
        if (!encoreHttpClient.isConfigured()) {
            log.info("[Encore-SIM] Simulated reversal of {} ({})", transactionId, transactionName);
            return;
        }

        try {
            List<String> transactionIds = List.of(transactionId + ":" + transactionName);
            String transactionIdJson = objectMapper.writeValueAsString(transactionIds);

            Map<String, String> params = new LinkedHashMap<>();
            params.put("transactionIdJson", transactionIdJson);
            params.put("reversalUserId", userId);

            encoreHttpClient.httpPost(
                    encoreProperties.getApi().getReverseTransactions(), params, null);

            log.info("[Encore] Reversed transaction {} ({})", transactionId, transactionName);

        } catch (Exception e) {
            log.error("[Encore] Reversal failed for {}: {}", transactionId, e.getMessage(), e);
            throw new RuntimeException("Encore reverse failed: " + e.getMessage(), e);
        }
    }

    /**
     * Get account statement from Encore.
     */
    public List<Map<String, Object>> getAccountStatement(String encoreAccountId,
                                                           String fromDate, String toDate) {
        if (!encoreHttpClient.isConfigured()) {
            log.info("[Encore-SIM] Simulated getAccountStatement for {}", encoreAccountId);
            return Collections.emptyList();
        }

        try {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("accountId", encoreAccountId);
            if (fromDate != null) params.put("fromDate", fromDate);
            if (toDate != null) params.put("toDate", toDate);

            String response = encoreHttpClient.httpGet(
                    encoreProperties.getApi().getFindAccountStatement(), params);

            JsonNode stmtArray = objectMapper.readTree(response);
            List<Map<String, Object>> entries = new ArrayList<>();
            if (stmtArray.isArray()) {
                for (JsonNode node : stmtArray) {
                    entries.add(objectMapper.convertValue(node, Map.class));
                }
            }

            log.info("[Encore] Found {} statement entries for {}", entries.size(), encoreAccountId);
            return entries;

        } catch (Exception e) {
            log.error("[Encore] getAccountStatement failed for {}: {}", encoreAccountId, e.getMessage(), e);
            throw new RuntimeException("Encore getAccountStatement failed: " + e.getMessage(), e);
        }
    }

    /**
     * Check if Encore integration is active (credentials configured).
     */
    public boolean isActive() {
        return encoreHttpClient.isConfigured();
    }
}
