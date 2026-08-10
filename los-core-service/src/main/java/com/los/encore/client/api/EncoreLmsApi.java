package com.los.encore.client.api;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * High-level Encore LMS operations (REST). Does not include legacy JAR-only {@code EncoreWebServiceFacade} flows.
 */
public interface EncoreLmsApi {

    boolean isActive();

    String openLoanAccount(EncoreOpenLoanParams params);

    /**
     * bl-core style POST {@code openAccount} with pre-serialized {@code loanOdAccount} JSON and explicit transaction id.
     */
    String openLoanAccountWithJson(String transactionId, String loanOdAccountJson);

    String disburse(String encoreAccountId, EncoreOpenLoanParams requestContext);

    String repay(String encoreAccountId, BigDecimal amount, String repaymentType);

    List<Map<String, Object>> findSummaries(List<String> encoreAccountIds);

    /**
     * Same HTTP contract as bl-core {@code findSummary}: GET {@code findSummaries} with a one-element
     * {@code accountId} array; returns the first summary object or null.
     */
    JsonNode findSummaryFirstObject(String accountId, boolean getAllDetails);

    List<Map<String, Object>> findRepaymentSchedule(String encoreAccountId);

    void reverseTransaction(String transactionId, String transactionName, String userId);

    List<Map<String, Object>> getAccountStatement(String encoreAccountId, String fromDate, String toDate);

    /**
     * Composite statement lines from Encore loan OD account details
     * ({@code GET api/loan-od-accounts/{accountId}} → {@code compositeStatement}).
     */
    List<Map<String, Object>> getCompositeStatement(String encoreAccountId);

    /** Raw loan OD account JSON (includes compositeStatement, accountStatement, etc.). */
    JsonNode getLoanOdAccountDetails(String encoreAccountId);

    /** Bank working date as returned by Encore (raw body). */
    String findBankWorkingDateRaw();

    /** Loan OD accounts for customer (raw JSON array string). */
    String findLoanAccountsForCustomerRaw(long customerId);

    String createProductRaw(String requestBodyJson);

    String getPrecloseAmountRaw(String accountId, String valueDate);

    String findODProductInfoRaw(String productName);

    String findPreOpenSummaryRaw(String requestBodyJson);

    String findLoanInfoRaw(String encoreAccountId);

    /** Single-account summary (raw). */
    String findSummaryRaw(String accountId, boolean getAllDetails);
}
