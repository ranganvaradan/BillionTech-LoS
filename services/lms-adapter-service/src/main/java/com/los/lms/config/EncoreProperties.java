package com.los.lms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Encore LMS configuration — maps to los.lms.encore.* properties.
 * Adapted from legacy dev-encore.properties / prod-encore.properties.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "los.lms.encore")
public class EncoreProperties {

    private String baseUrl = "http://encore.bl-internal.com:8080/encore/";
    private String schema = "http";
    private String hostname = "encore.bl-internal.com";
    private int port = 8080;

    // Basic Auth credentials
    private String apiUsername = "";
    private String apiPassword = "";

    // API endpoint paths (relative to baseUrl)
    private EncoreApiEndpoints api = new EncoreApiEndpoints();

    // Defaults from legacy config
    private String currency = "INR";
    private String adminBranch = "HQ";
    private String casaBranchCode = "HQ";
    private String casaProductCode = "C100";
    private String loanProductType = "Loans";
    private String investmentAccountId = "000010000001";

    // Timeouts
    private int connectTimeoutMs = 10000;
    private int readTimeoutMs = 30000;

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
    }
}
