package com.los.encore.client.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.los.encore.client.config.EncoreClientProperties;
import com.los.encore.client.http.EncoreHttpTransport;
import com.los.encore.client.support.EncoreLoanOdSummaryMapper;
import com.los.encore.client.support.EncoreRepaymentScheduleParser;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Default Encore LMS REST facade - aligned with legacy bl-core HTTP patterns
 * ({@code loanOdAccount} / {@code transactions} as query parameters on POST where applicable).
 */
@Slf4j
public class DefaultEncoreLmsApi implements EncoreLmsApi {

    private final EncoreClientProperties properties;
    private final EncoreHttpTransport transport;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /** Encore sandbox / Credinnov uses IST for value-date interpretation. */
    private static final ZoneId ENCORE_VALUE_DATE_ZONE = ZoneId.of("Asia/Kolkata");
    private static final Pattern BANK_DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    public DefaultEncoreLmsApi(EncoreClientProperties properties,
                               EncoreHttpTransport transport,
                               ObjectMapper objectMapper) {
        this.properties = properties;
        this.transport = transport;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isActive() {
        return transport.isConfigured();
    }

    @Override
    public String openLoanAccount(EncoreOpenLoanParams request) {
        if (!isActive()) {
            return "SIM-" + request.applicationNumber();
        }
        try {
            ObjectNode loanAccount = buildMinimalLoanOdAccount(request);
            String transactionId = "BL-" + UUID.randomUUID().toString().substring(0, 8);
            String loanOdAccountJson = objectMapper.writeValueAsString(loanAccount);
            log.info("[LMS-SANCTION] Encore openLoanAccount request payload | app={} | transactionId={} | payload={}",
                    request.applicationNumber(), transactionId, loanOdAccountJson);
            return openLoanAccountWithJson(transactionId, loanOdAccountJson);
        } catch (Exception e) {
            throw new RuntimeException("Encore openLoanAccount (minimal) failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String openLoanAccountWithJson(String transactionId, String loanOdAccountJson) {
        if (!isActive()) {
            return "SIM-" + transactionId;
        }
        try {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("transactionId", transactionId);
            params.put("loanOdAccount", loanOdAccountJson);

            String response = transport.httpPost(
                    properties.getApi().getCreateLoanAccount(), params, null);

            JsonNode responseJson = objectMapper.readTree(response);
            if (responseJson.has("accountId")) {
                return responseJson.get("accountId").asText();
            }
            if (responseJson.isTextual()) {
                return response.replace("\"", "");
            }
            return "ENCORE-JSON";
        } catch (Exception e) {
            throw new RuntimeException("Encore openLoanAccountWithJson failed: " + e.getMessage(), e);
        }
    }

    private ObjectNode buildMinimalLoanOdAccount(EncoreOpenLoanParams request) {
        ObjectNode loanAccount = objectMapper.createObjectNode();
        LocalDate disbDate = LocalDate.now();
        LocalDate disbByDate = disbDate.plusMonths(1);
        loanAccount.putNull("accountId");
        loanAccount.put("openedOnDate", disbDate.format(DATE_FORMAT));
        loanAccount.put("disbursementByDate", disbByDate.format(DATE_FORMAT));
        loanAccount.putNull("firstRepaymentDate");
        loanAccount.put("amountMagnitude", request.sanctionedAmount().toPlainString());
        loanAccount.put("branchCode", properties.getAdminBranch());
        loanAccount.put("currencyCode", properties.getCurrency());
        // TEMP FIX:
        // Using hardcoded Encore location codes until final LMS location master mapping is completed.
        // TODO: Restore dynamic location mapping after Encore location master is finalized.
        loanAccount.put("customer1CityCode", EncoreTemporaryOverrides.DEFAULT_ENCORE_LOCATION_CODE);
        loanAccount.put("customer1CountryCode", EncoreTemporaryOverrides.DEFAULT_ENCORE_LOCATION_CODE);
        loanAccount.put("customer1StateCode", EncoreTemporaryOverrides.DEFAULT_ENCORE_LOCATION_CODE);
        loanAccount.put("customer1FirstName", request.borrowerName() != null ? request.borrowerName() : "");
        loanAccount.put("customer1MiddleName", "");
        loanAccount.put("customer1LastName", "");
        loanAccount.put("customerId1", EncoreTemporaryOverrides.buildEncoreCustomerId(request.applicationNumber()));
        loanAccount.put("migrated", "false");
        loanAccount.put("normalInterestRate", request.interestRate().toPlainString());
        loanAccount.put("operationalStatus", "active");
        loanAccount.put("penalInterestRate", "0");
        loanAccount.put("preclosureFeeRate", "0");
        String productCode = request.productCode() != null && !request.productCode().isBlank()
                ? request.productCode()
                : EncoreTemporaryOverrides.DEFAULT_ENCORE_PRODUCT_CODE;
        loanAccount.put("productCode", productCode);
        loanAccount.put("productType", properties.getLoanProductType());
        loanAccount.put("tenureMagnitude", String.valueOf(request.tenureMonths()));
        loanAccount.put("tenureUnit", "Month");
        loanAccount.put("securityDepositAllowed", true);
        loanAccount.put("moratoriumPeriodUnit", "Month");
        loanAccount.put("moratoriumPeriodMagnitude", "0");
        loanAccount.put("moratoriumType", "None");
        loanAccount.put("moratoriumNormalInterestRateApplicable", false);
        loanAccount.putNull("moratoriumNormalInterestRate");
        loanAccount.putNull("moratoriumInterestAccrualCalculation");
        loanAccount.put("colendingApplicable", "false");
        return loanAccount;
    }

    @Override
    public String disburse(String encoreAccountId, EncoreOpenLoanParams requestContext) {
        if (!isActive()) {
            return "SIM-TXN-" + UUID.randomUUID().toString().substring(0, 8);
        }
        try {
            String transactionId = "BL-" + UUID.randomUUID().toString().substring(0, 8);
            ArrayNode transactions = objectMapper.createArrayNode();
            ObjectNode txn = objectMapper.createObjectNode();
            LocalDate requestedDisbursementDate = parseRequestedDisbursementDate(requestContext.disbursementDate());
            LocalDate bankDate = fetchBankWorkingDate();

            txn.put("transactionId", transactionId);
            long valueDateMillis = resolveDisburseValueDateMillis(requestedDisbursementDate);
            txn.put("valueDate", valueDateMillis);
            txn.putNull("transactionDate");
            txn.put("accountId", encoreAccountId);
            txn.put("transactionName", "Disbursement");
            txn.put("amount1", requestContext.sanctionedAmount().toPlainString());
            txn.put("description", "");
            String userId = requestContext.userId() != null ? requestContext.userId() : "los-adapter";
            txn.put("userId", userId);
            txn.put("instrument", "CASH");
            txn.putNull("transactionLotId");
            txn.putNull("param1");
            txn.putNull("param2");
            txn.putNull("param2Str");
            txn.putNull("sequenceNum");
            txn.putNull("param3");

            transactions.add(txn);

            log.info("[LMS-DISBURSE] accountId={} | requestedDisbursementDate={} | valueDateMillis={} | bankWorkingDate={}",
                    encoreAccountId, requestedDisbursementDate, valueDateMillis, bankDate);
            log.info("[LMS-DISBURSE-FINAL-PAYLOAD] class={} | mapper={} | endpoint={} | " +
                            "accountId={} | transactionId={} | fullPayload={}",
                    "ObjectNode (inline)", this.getClass().getSimpleName(),
                    properties.getApi().getPostTransactions(),
                    encoreAccountId, transactionId,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(transactions));

            postTransactionsArray(transactions);
            return transactionId;
        } catch (Exception e) {
            throw new RuntimeException("Encore disburse failed: " + e.getMessage(), e);
        }
    }

    /**
     * bl-core invoice disbursement posts the treasury-selected disbursement date to Encore as
     * {@code valueDate}. Noon IST keeps the intended calendar date when Encore interprets millis as UTC.
     */
    long resolveDisburseValueDateMillis(LocalDate requestedDisbursementDate) {
        if (requestedDisbursementDate != null) {
            return toEncoreCalendarDateMillis(requestedDisbursementDate);
        }
        return toEncoreCalendarDateMillis(fetchBankWorkingDate());
    }

    /** Noon IST keeps the intended calendar date when Encore interprets millis as UTC. */
    static long toEncoreCalendarDateMillis(LocalDate date) {
        return date.atTime(12, 0).atZone(ENCORE_VALUE_DATE_ZONE).toInstant().toEpochMilli();
    }

    private static LocalDate parseRequestedDisbursementDate(String disbursementDate) {
        if (disbursementDate == null || disbursementDate.isBlank()) {
            return null;
        }
        return LocalDate.parse(disbursementDate.trim(), DATE_FORMAT);
    }

    private LocalDate fetchBankWorkingDate() {
        if (!isActive()) {
            return LocalDate.now(ENCORE_VALUE_DATE_ZONE);
        }
        try {
            String raw = findBankWorkingDateRaw();
            if (raw != null) {
                String trimmed = raw.trim().replace("\"", "");
                var matcher = BANK_DATE_PATTERN.matcher(trimmed);
                if (matcher.find()) {
                    return LocalDate.parse(matcher.group(), DATE_FORMAT);
                }
            }
        } catch (Exception e) {
            log.warn("[LMS-DISBURSE] Could not fetch Encore bank working date, using IST today: {}", e.getMessage());
        }
        return LocalDate.now(ENCORE_VALUE_DATE_ZONE);
    }

    @Override
    public String repay(String encoreAccountId, BigDecimal amount, String repaymentType) {
        if (!isActive()) {
            return "SIM-RPY-" + UUID.randomUUID().toString().substring(0, 8);
        }
        try {
            // Mirror the proven-working disburse() transaction shape exactly (bl-core
            // SanctionedInvoiceLoanServiceFacadeImpl.recordPaymentToEncore parity): Encore resolves the
            // account period from the epoch-millis `valueDate`, so we MUST send `valueDate` (not a
            // `valueDateStr` string). Repayments use an empty description, CASH instrument and a
            // transactionLotId, matching the legacy ScheduledRepayment payload Encore accepts. The
            // previous `valueDateStr`/`NEFT` payload was rejected by Encore as INVALID_ACCOUNT_CODE.
            long nowMillis = System.currentTimeMillis();
            String transactionId = "BL-RPY-" + UUID.randomUUID().toString().substring(0, 8);
            String txnName = repaymentType != null && !repaymentType.isBlank() ? repaymentType : "ScheduledRepayment";
            ArrayNode transactions = objectMapper.createArrayNode();
            ObjectNode txn = objectMapper.createObjectNode();

            txn.put("transactionId", transactionId);
            txn.put("valueDate", nowMillis);
            txn.put("transactionDate", nowMillis);
            txn.put("accountId", encoreAccountId);
            txn.put("transactionName", txnName);
            txn.put("amount1", amount.toPlainString());
            txn.put("description", "");
            txn.put("userId", "los-adapter");
            txn.put("instrument", "CASH");
            txn.put("transactionLotId", String.valueOf(nowMillis));
            txn.putNull("param1");
            txn.putNull("param2");
            txn.putNull("param2Str");
            txn.putNull("sequenceNum");
            txn.putNull("param3");

            transactions.add(txn);

            log.info("[LMS-REPAY-FINAL-PAYLOAD] endpoint={} | accountId={} | transactionId={} | txnName={} | payload={}",
                    properties.getApi().getPostTransactions(), encoreAccountId, transactionId, txnName,
                    objectMapper.writeValueAsString(transactions));

            postTransactionsArray(transactions);
            return transactionId;
        } catch (Exception e) {
            throw new RuntimeException("Encore repay failed: " + e.getMessage(), e);
        }
    }

    /** Legacy {@code postTransactions}: query params {@code transactions} + {@code commitSize}. */
    private void postTransactionsArray(ArrayNode transactions) throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("transactions", objectMapper.writeValueAsString(transactions));
        params.put("commitSize", "0");
        transport.httpPost(properties.getApi().getPostTransactions(), params, null);
    }

    @Override
    public List<Map<String, Object>> findSummaries(List<String> encoreAccountIds) {
        if (!isActive()) {
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
            return findSummariesViaWebservice(encoreAccountIds);
        } catch (Exception e) {
            if (hasRestAuthToken() && isPreclosureFeeRateError(e)) {
                log.warn("[Encore] findSummaries failed (preclosureFeeRate null) for {} account(s) — "
                        + "falling back to REST loan-od-accounts", encoreAccountIds.size());
                List<Map<String, Object>> fallback = findSummariesViaLoanOdAccount(encoreAccountIds);
                if (!fallback.isEmpty()) {
                    return fallback;
                }
            }
            throw new RuntimeException("Encore findSummaries failed: " + e.getMessage(), e);
        }
    }

    private List<Map<String, Object>> findSummariesViaWebservice(List<String> encoreAccountIds) throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("accountId", objectMapper.writeValueAsString(encoreAccountIds));
        params.put("ignoreTransactions", "false");
        String response = transport.httpGet(properties.getApi().getFindSummaries(), params);
        JsonNode summaryArray = objectMapper.readTree(response);
        List<Map<String, Object>> results = new ArrayList<>();
        if (summaryArray.isArray()) {
            for (JsonNode node : summaryArray) {
                @SuppressWarnings("unchecked")
                Map<String, Object> row = objectMapper.convertValue(node, Map.class);
                results.add(row);
            }
        }
        return results;
    }

    private List<Map<String, Object>> findSummariesViaLoanOdAccount(List<String> encoreAccountIds) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (String accountId : encoreAccountIds) {
            JsonNode root = fetchLoanOdAccountRoot(accountId);
            if (root != null) {
                Map<String, Object> row = EncoreLoanOdSummaryMapper.toSummaryRow(root);
                if (!row.isEmpty()) {
                    results.add(row);
                }
            }
        }
        return results;
    }

    @Override
    public JsonNode findSummaryFirstObject(String accountId, boolean getAllDetails) {
        if (!isActive()) {
            return null;
        }
        try {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("accountId", objectMapper.writeValueAsString(List.of(accountId)));
            params.put("ignoreTransactions", Boolean.toString(getAllDetails));
            String response = transport.httpGet(properties.getApi().getFindSummaries(), params);
            JsonNode arr = objectMapper.readTree(response);
            if (arr.isArray() && arr.size() >= 1) {
                return arr.get(0);
            }
            return null;
        } catch (Exception e) {
            if (hasRestAuthToken() && isPreclosureFeeRateError(e)) {
                log.warn("[Encore] findSummaryFirstObject failed (preclosureFeeRate null) for accountId={} — "
                        + "falling back to REST loan-od-accounts", accountId);
                return fetchLoanOdAccountRoot(accountId);
            }
            throw new RuntimeException("Encore findSummaryFirstObject failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> findRepaymentSchedule(String encoreAccountId) {
        if (!isActive()) {
            return Collections.emptyList();
        }
        if (hasRestAuthToken()) {
            List<Map<String, Object>> fromRest = findRepaymentScheduleViaLoanOdAccount(encoreAccountId);
            if (!fromRest.isEmpty()) {
                return fromRest;
            }
        }
        try {
            List<Map<String, Object>> fromWebservice = findRepaymentScheduleViaFindSummaries(encoreAccountId);
            if (!fromWebservice.isEmpty()) {
                return fromWebservice;
            }
        } catch (Exception e) {
            log.warn("[Encore] findSummaries repayment schedule failed for accountId={}: {} — trying REST loan-od-accounts",
                    encoreAccountId, e.getMessage());
        }
        if (!hasRestAuthToken()) {
            return findRepaymentScheduleViaLoanOdAccount(encoreAccountId);
        }
        return Collections.emptyList();
    }

    private List<Map<String, Object>> findRepaymentScheduleViaFindSummaries(String encoreAccountId) throws Exception {
        if (properties.getBlCoreParity().isScheduleViaFindSummary()) {
            JsonNode summary = findSummaryFirstObject(encoreAccountId, false);
            return EncoreRepaymentScheduleParser.parseFromSummaryRoot(summary);
        }
        Map<String, String> params = new LinkedHashMap<>();
        params.put("accountId", objectMapper.writeValueAsString(List.of(encoreAccountId)));
        params.put("ignoreTransactions", "false");
        String response = transport.httpGet(properties.getApi().getFindSummaries(), params);
        JsonNode summaryArray = objectMapper.readTree(response);
        if (summaryArray.isArray() && !summaryArray.isEmpty()) {
            return EncoreRepaymentScheduleParser.parseFromSummaryRoot(summaryArray.get(0));
        }
        return Collections.emptyList();
    }

    private List<Map<String, Object>> findRepaymentScheduleViaLoanOdAccount(String encoreAccountId) {
        JsonNode root = getLoanOdAccountDetails(encoreAccountId);
        return EncoreRepaymentScheduleParser.parseFromSummaryRoot(root);
    }

    @Override
    public void reverseTransaction(String transactionId, String transactionName, String userId) {
        if (!isActive()) {
            return;
        }
        try {
            List<String> transactionIds = List.of(transactionId + ":" + transactionName);
            Map<String, String> params = new LinkedHashMap<>();
            params.put("transactionIdJson", objectMapper.writeValueAsString(transactionIds));
            params.put("reversalUserId", userId);
            transport.httpPost(properties.getApi().getReverseTransactions(), params, null);
        } catch (Exception e) {
            throw new RuntimeException("Encore reverse failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> getAccountStatement(String encoreAccountId, String fromDate, String toDate) {
        if (!isActive()) {
            return Collections.emptyList();
        }
        try {
            if (hasRestAuthToken()) {
                List<Map<String, Object>> composite = getCompositeStatement(encoreAccountId);
                if (!composite.isEmpty()) {
                    return composite;
                }
                List<Map<String, Object>> accountStatement = fetchAccountStatementFromLoanOd(encoreAccountId);
                if (!accountStatement.isEmpty()) {
                    return accountStatement;
                }
            }
            List<Map<String, Object>> legacy = fetchFindAccountStatements(encoreAccountId, fromDate, toDate);
            if (!legacy.isEmpty()) {
                return legacy;
            }
            if (!hasRestAuthToken()) {
                List<Map<String, Object>> composite = getCompositeStatement(encoreAccountId);
                if (!composite.isEmpty()) {
                    return composite;
                }
            }
            return Collections.emptyList();
        } catch (Exception e) {
            throw new RuntimeException("Encore getAccountStatement failed: " + e.getMessage(), e);
        }
    }

    private boolean hasRestAuthToken() {
        String token = properties.getRestAuthToken();
        return token != null && !token.isBlank();
    }

    private List<Map<String, Object>> fetchAccountStatementFromLoanOd(String encoreAccountId) {
        try {
            JsonNode root = getLoanOdAccountDetails(encoreAccountId);
            JsonNode stmtArray = root != null ? root.path("accountStatement") : null;
            if (stmtArray == null || !stmtArray.isArray() || stmtArray.isEmpty()) {
                return Collections.emptyList();
            }
            List<Map<String, Object>> entries = new ArrayList<>();
            for (JsonNode node : stmtArray) {
                @SuppressWarnings("unchecked")
                Map<String, Object> row = objectMapper.convertValue(node, Map.class);
                entries.add(row);
            }
            return entries;
        } catch (Exception e) {
            log.warn("[Encore] accountStatement from loan-od-accounts failed for accountId={}: {}",
                    encoreAccountId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> fetchFindAccountStatements(
            String encoreAccountId, String fromDate, String toDate) {
        try {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("accountId", encoreAccountId);
            if (fromDate != null) {
                params.put("fromDate", fromDate);
            }
            if (toDate != null) {
                params.put("toDate", toDate);
            }
            String response = transport.httpGet(properties.getApi().getFindAccountStatement(), params);
            JsonNode stmtArray = objectMapper.readTree(response);
            List<Map<String, Object>> entries = new ArrayList<>();
            if (stmtArray.isArray()) {
                for (JsonNode node : stmtArray) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> row = objectMapper.convertValue(node, Map.class);
                    entries.add(row);
                }
            }
            return entries;
        } catch (Exception e) {
            log.warn("[Encore] findAccountStatements failed for accountId={}: {}", encoreAccountId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public JsonNode getLoanOdAccountDetails(String encoreAccountId) {
        if (!isActive()) {
            return objectMapper.createObjectNode();
        }
        JsonNode root = fetchLoanOdAccountRoot(encoreAccountId);
        return root != null ? root : objectMapper.createObjectNode();
    }

    /** Returns parsed loan-od JSON or {@code null} when the REST call fails or returns no accountId. */
    private JsonNode fetchLoanOdAccountRoot(String encoreAccountId) {
        try {
            String path = properties.getApi().getLoanOdAccountDetails() + "/" + encoreAccountId;
            String response = transport.httpGet(path, null);
            JsonNode root = objectMapper.readTree(response);
            if (root == null || root.isNull() || !root.hasNonNull("accountId")) {
                return null;
            }
            return root;
        } catch (Exception e) {
            log.warn("[Encore] fetchLoanOdAccountRoot failed for accountId={}: {}", encoreAccountId, e.getMessage());
            return null;
        }
    }

    private static boolean isPreclosureFeeRateError(Throwable e) {
        String msg = e.getMessage();
        if (msg != null && msg.contains("preclosureFeeRate")) {
            return true;
        }
        Throwable cause = e.getCause();
        return cause != null && isPreclosureFeeRateError(cause);
    }

    @Override
    public List<Map<String, Object>> getCompositeStatement(String encoreAccountId) {
        if (!isActive()) {
            return Collections.emptyList();
        }
        try {
            JsonNode root = getLoanOdAccountDetails(encoreAccountId);
            JsonNode composite = root != null ? root.get("compositeStatement") : null;
            if (composite == null || !composite.isArray() || composite.isEmpty()) {
                return Collections.emptyList();
            }
            List<Map<String, Object>> entries = new ArrayList<>();
            for (JsonNode node : composite) {
                @SuppressWarnings("unchecked")
                Map<String, Object> row = objectMapper.convertValue(node, Map.class);
                entries.add(row);
            }
            return entries;
        } catch (Exception e) {
            log.warn("[Encore] getCompositeStatement failed for accountId={}: {}", encoreAccountId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public String findBankWorkingDateRaw() {
        return transport.httpGet(properties.getApi().getFindWorkingDate(), null);
    }

    @Override
    public String findLoanAccountsForCustomerRaw(long customerId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("customerId", Long.toString(customerId));
        return transport.httpGet(properties.getApi().getFindAccounts(), params);
    }

    @Override
    public String createProductRaw(String requestBodyJson) {
        return transport.httpPost(properties.getApi().getCreateProduct(), null, requestBodyJson);
    }

    @Override
    public String getPrecloseAmountRaw(String accountId, String valueDate) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("accountId", accountId);
        params.put("valueDate", valueDate);
        return transport.httpPost(properties.getApi().getGetPrecloseAmtByValueDt(), params, null);
    }

    @Override
    public String findODProductInfoRaw(String productName) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("productCode", productName);
        params.put("currencyCode", properties.getCurrency());
        return transport.httpGet(properties.getApi().getFindODProductInfo(), params);
    }

    @Override
    public String findPreOpenSummaryRaw(String requestBodyJson) {
        return transport.httpPost(properties.getApi().getFindPreOpenSummary(), null, requestBodyJson);
    }

    @Override
    public String findLoanInfoRaw(String encoreAccountId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("accountId", encoreAccountId);
        return transport.httpGet(properties.getApi().getFindLoanInfo(), params);
    }

    @Override
    public String findSummaryRaw(String accountId, boolean getAllDetails) {
        if (!isActive()) {
            return "{}";
        }
        JsonNode n = findSummaryFirstObject(accountId, getAllDetails);
        return n != null ? n.toString() : "{}";
    }
}
