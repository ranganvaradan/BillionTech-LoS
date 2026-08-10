package com.los.encore.client.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code los.lms.encore.*} — remote Encore LMS HTTP API (see {@code ENCORE_BASE_URL} / {@code application.yml}).
 * <p>
 * After binding, timeouts are clamped to positive values so {@link java.net.http.HttpClient}
 * never receives a negative {@link java.time.Duration} (e.g. {@code PT-10S}).
 */
@Slf4j
@Data
@ConfigurationProperties(prefix = "los.lms.encore")
public class EncoreClientProperties {

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 30_000;

    private String baseUrl = "http://credinnov-sandbox.senseitech.com/credinnov-encore-server/";
    private String schema = "http";
    private String hostname = "encore.bl-internal.com";
    private int port = 8080;

    private String apiUsername = "admin";
    private String apiPassword = "password1";

    /**
     * Optional session token for Encore REST paths under {@code api/} (e.g. loan-od-accounts).
     * Webservices paths continue to use Basic auth. Set {@code ENCORE_REST_AUTH_TOKEN} in env.
     */
    private String restAuthToken = "";

    private EncoreApiEndpoints api = new EncoreApiEndpoints();

    private String currency = "INR";
    private String adminBranch = "MGPP";
    private String casaBranchCode = "MGPP";
    private String casaProductCode = "C100";
    private String loanProductType = "Loans";
    private String investmentAccountId = "000010000001";

    private int connectTimeoutMs = 10000;
    private int readTimeoutMs = 30000;

    /** Max retries for idempotent GET calls only (0 = disabled). */
    private int maxRetriesForGet = 2;
    /** Milliseconds between GET retries. */
    private long retryBackoffMs = 500;

    /**
     * Optional behaviour alignment with legacy bl-core {@code EncoreServiceFacadeImpl} (see {@code docs/encore-bl-core-parity-spec.md}).
     */
    private BlCoreParity blCoreParity = new BlCoreParity();

    @PostConstruct
    void normalizeTimeouts() {
        if (connectTimeoutMs <= 0) {
            log.warn("los.lms.encore.connect-timeout-ms was non-positive ({}); using {} ms",
                    connectTimeoutMs, DEFAULT_CONNECT_TIMEOUT_MS);
            connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MS;
        }
        if (readTimeoutMs <= 0) {
            log.warn("los.lms.encore.read-timeout-ms was non-positive ({}); using {} ms",
                    readTimeoutMs, DEFAULT_READ_TIMEOUT_MS);
            readTimeoutMs = DEFAULT_READ_TIMEOUT_MS;
        }
        if (maxRetriesForGet < 0) {
            log.warn("los.lms.encore.max-retries-for-get was negative ({}); using 0", maxRetriesForGet);
            maxRetriesForGet = 0;
        }
        if (retryBackoffMs < 0) {
            log.warn("los.lms.encore.retry-backoff-ms was negative ({}); using 0", retryBackoffMs);
            retryBackoffMs = 0;
        }
        if (port <= 0 || port > 65535) {
            log.warn("los.lms.encore.port was invalid ({}); using 8080", port);
            port = 8080;
        }
        log.info("LOS Encore LMS client configured: baseUrl={} apiUsername={} apiPasswordConfigured={} "
                        + "restAuthTokenConfigured={} openAccountEndpoint={} connectTimeoutMs={} readTimeoutMs={}",
                baseUrl, apiUsername, apiPassword != null && !apiPassword.isBlank(),
                restAuthToken != null && !restAuthToken.isBlank(),
                api != null ? api.getCreateLoanAccount() : "-", connectTimeoutMs, readTimeoutMs);
        if (restAuthToken == null || restAuthToken.isBlank()) {
            log.warn("ENCORE_REST_AUTH_TOKEN is not set — repayment schedule and SOA via api/loan-od-accounts "
                    + "will fail (401). Copy X-Auth-Token from Encore UI after login, or set ENCORE_REST_AUTH_TOKEN.");
        }
    }

    @Data
    public static class EncoreApiEndpoints {
        private String createLoanAccount = "webservices/loans/accounts/openAccount";
        private String postTransactions = "webservices/loans/accounts/postTransactions";
        private String reverseTransactions = "webservices/loans/accounts/reverseTransactions";
        private String findSummaries = "webservices/loans/accounts/findSummaries";
        private String findSummary = "webservices/loans/accounts/findSummary";
        private String findAccounts = "webservices/loans/accounts/findLoanOdAccounts";
        private String findWorkingDate = "webservices/loans/accounts/findBankWorkingDate";
        private String createProduct = "webservices/v2/createLoanProduct";
        private String findAccountStatement = "webservices/loans/accounts/findAccountStatements";
        private String getPrecloseAmtByValueDt = "webservices/loans/accounts/findPreclosureAmountAsOfDate";
        private String findODProductInfo = "webservices/loans/accounts/findLoanOdProduct";
        private String findPreOpenSummary = "webservices/findPreOpenSummary";
        private String findLoanInfo = "webservices/findLoanInfo";
        /** REST account details including {@code compositeStatement} (SOA). */
        private String loanOdAccountDetails = "api/loan-od-accounts";
    }

    @Data
    public static class BlCoreParity {
        /**
         * When true, {@link com.los.lms.service.LmsService} builds {@code loanOdAccount} JSON using
         * {@link com.los.lms.legacy.BlCoreEncoreLmsAdapter} (geo / customer id / branch fields from handover).
         */
        private boolean extendedOpenLoanPayload = false;
        /**
         * When true, repayment schedule lines are read from the first {@code findSummaries} object
         * ({@code repaymentSchedule} array), matching bl-core {@code findSummary} + {@code findRepaymentSchedules}.
         */
        private boolean scheduleViaFindSummary = true;
        /** When true, compares host clock to Encore bank working date (best-effort) before open/disburse. */
        private boolean validateBankWorkingDateBeforeMutations = false;
        /** When true, sanction+KFS merges Encore {@code findPreOpenSummary} JSON into KFS {@code charges} map. */
        private boolean preOpenSummaryForKfs = false;
    }
}
