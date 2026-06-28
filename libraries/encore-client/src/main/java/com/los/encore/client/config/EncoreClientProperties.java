package com.los.encore.client.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code los.lms.encore.*} — same prefix as legacy-inspired YAML.
 * <p>
 * After binding, timeouts are clamped to positive values so {@link java.net.http.HttpClient}
 * never receives a negative {@link java.time.Duration}.
 */
@Slf4j
@Data
@ConfigurationProperties(prefix = "los.lms.encore")
public class EncoreClientProperties {

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 30_000;

    private String baseUrl = "http://localhost:8080/encore/";
    private String schema = "http";
    private String hostname = "localhost";
    private int port = 8080;

    private String apiUsername = "";
    private String apiPassword = "";

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
            log.warn("los.lms.encore.port was invalid ({}); using 8090", port);
            port = 8090;
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
}
