package com.los.lms.legacy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.los.encore.client.api.EncoreOpenLoanParams;
import com.los.encore.client.config.EncoreClientProperties;
import com.los.lms.dto.LoanHandoverRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import com.los.core.exception.BusinessRuleException;
import com.los.lms.service.LmsApplicationConfigResolver;

import static com.los.encore.client.api.EncoreTemporaryOverrides.DEFAULT_ENCORE_LOCATION_CODE;
import static com.los.encore.client.api.EncoreTemporaryOverrides.buildEncoreCustomerId;

/**
 * Builds Encore {@code loanOdAccount} JSON aligned 1:1 with bl-core
 * {@code EncoreServiceFacadeImpl.openLoanAccount} (lines 359-462).
 */
@Component
@RequiredArgsConstructor
public class BlCoreEncoreLmsAdapter {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final EncoreClientProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * Full bl-core parity {@code loanOdAccount} builder - populates all 30+ fields.
     */
    public ObjectNode buildLoanOdAccount(EncoreOpenLoanParams p, LoanHandoverRequest req) {
        ObjectNode acc = objectMapper.createObjectNode();

        String disbDateStr = p.disbursementDate() != null ? p.disbursementDate()
                : LocalDate.now().format(DATE_FORMAT);
        LocalDate disbDate = LocalDate.parse(disbDateStr, DATE_FORMAT);
        LocalDate disbByDate = disbDate.plusMonths(1);

        acc.putNull("accountId");
        acc.put("openedOnDate", disbDateStr);
        acc.put("disbursementByDate", disbByDate.format(DATE_FORMAT));
        acc.putNull("firstRepaymentDate");
        acc.put("amountMagnitude", p.sanctionedAmount().toPlainString());

        // branchCode: partner-specific (bl-core: partnerDto.getBranchCode())
        String branch = coalesce(p.branchCode(),
                req != null ? req.getEncoreBranchCode() : null,
                properties.getAdminBranch());
        acc.put("branchCode", branch);

        acc.put("currencyCode", properties.getCurrency());

        // Customer name split (bl-core puts full name in firstName, blanks middle/last)
        acc.put("customer1FirstName", p.borrowerName() != null ? p.borrowerName() : "");
        acc.put("customer1MiddleName", "");
        acc.put("customer1LastName", "");

        // customerId1: compact LMS-safe ID (Encore column limit ~15 chars)
        String rawCustomerId = coalesce(p.customerId(),
                req != null ? req.getEncorePartyOrCustomerId() : null,
                p.applicationNumber());
        acc.put("customerId1", buildEncoreCustomerId(rawCustomerId));

        acc.put("migrated", "false");

        // normalInterestRate: bl-core uses loan.getMaxInterestRate() when > 0
        acc.put("normalInterestRate", p.interestRate() != null ? p.interestRate().toPlainString() : "0");

        acc.put("operationalStatus", "active");

        // penalInterestRate: bl-core uses loan.getOverDueInterestRt()
        BigDecimal penalRate = p.penalInterestRate() != null ? p.penalInterestRate() : BigDecimal.ZERO;
        acc.put("penalInterestRate", penalRate.toPlainString());
        acc.put("preclosureFeeRate", "0");

        String productCode = requireProductCode(p.productCode());
        acc.put("productCode", productCode);
        acc.put("productType", properties.getLoanProductType());

        // tenureMagnitude: bl-core uses loan.getNoOfInstallments(), NOT calendar months
        int tenure = p.numberOfInstallments() > 0 ? p.numberOfInstallments() : p.tenureMonths();
        acc.put("tenureMagnitude", String.valueOf(tenure));

        // tenureUnit: from product config (Month/Quarter/Half Year/Year/Week/Day)
        acc.put("tenureUnit", coalesce(p.tenureUnit(), "Month"));

        acc.put("securityDepositAllowed", true);

        // Moratorium fields (bl-core: loan.getMoratoriumType() etc.)
        acc.put("moratoriumType", coalesce(p.moratoriumType(), "None"));
        acc.put("moratoriumPeriodMagnitude", String.valueOf(p.moratoriumPeriodMagnitude()));
        acc.put("moratoriumPeriodUnit", coalesce(p.moratoriumPeriodUnit(), "Month"));

        if (p.moratoriumNormalInterestRateApplicable()) {
            acc.put("moratoriumNormalInterestRateApplicable", "true");
            if (p.moratoriumNormalInterestRate() != null) {
                acc.put("moratoriumNormalInterestRate", p.moratoriumNormalInterestRate());
            }
            if (p.moratoriumInterestAccrualCalculation() != null) {
                acc.put("moratoriumInterestAccrualCalculation", p.moratoriumInterestAccrualCalculation());
            }
        } else {
            acc.put("moratoriumNormalInterestRateApplicable", false);
            acc.putNull("moratoriumNormalInterestRate");
            acc.putNull("moratoriumInterestAccrualCalculation");
        }

        // Geo fields (bl-core: cityService.findCityById -> state/country codes)
        String pinCode = coalesce(p.pinCode(), geoFromBorrower(req, "pinCode", "pincode", "postalCode"));
        if (pinCode == null || pinCode.isBlank()) {
            pinCode = properties.getAdminBranch() != null ? null : null;
        }
        putIfPresent(acc, "customer1PinCode", pinCode);
        // TEMP FIX:
        // Using hardcoded Encore location codes until final LMS location master mapping is completed.
        // TODO: Restore dynamic location mapping after Encore location master is finalized.
        acc.put("customer1CityCode", DEFAULT_ENCORE_LOCATION_CODE);
        acc.put("customer1CountryCode", DEFAULT_ENCORE_LOCATION_CODE);
        acc.put("customer1StateCode", DEFAULT_ENCORE_LOCATION_CODE);

        // Co-lending fields (bl-core: Colending entity when coLending > 0)
        if ("true".equalsIgnoreCase(p.colendingApplicable()) ||
                (req != null && "true".equalsIgnoreCase(req.getColendingApplicable()))) {
            acc.put("colendingApplicable", "true");
            putIfPresent(acc, "colenderProductCode",
                    coalesce(p.colenderProductCode(), req != null ? req.getColenderProductCode() : null));
            putIfPresent(acc, "colenderId",
                    coalesce(p.colenderId(), req != null ? req.getColenderId() : null));
            putIfPresent(acc, "colenderLendingRatio",
                    coalesce(p.colenderLendingRatio(), req != null ? req.getColenderLendingRatio() : null));
            putIfPresent(acc, "colenderNormalInterestRate",
                    coalesce(p.colenderNormalInterestRate(), req != null ? req.getColenderNormalInterestRate() : null));
        } else {
            acc.put("colendingApplicable", "false");
        }

        return acc;
    }

    /** JSON body for {@code findPreOpenSummary} (bl-core {@code EncoreServiceFacadeImpl#findPreOpenSummary}). */
    public String buildPreOpenSummaryRequestBody(String accountId,
                                                   BigDecimal amount,
                                                   LocalDate openedOn,
                                                   String productCode,
                                                   int tenureMagnitude,
                                                   String tenureUnit) {
        return buildPreOpenSummaryRequestBody(
                accountId, amount, openedOn, productCode, tenureMagnitude, tenureUnit, null);
    }

    /**
     * {@code findPreOpenSummary} requires borrower location codes (same as {@code openLoanAccount});
     * without them Encore returns {@code INVALID_LOCATION}.
     */
    public String buildPreOpenSummaryRequestBody(String accountId,
                                                   BigDecimal amount,
                                                   LocalDate openedOn,
                                                   String productCode,
                                                   int tenureMagnitude,
                                                   String tenureUnit,
                                                   String pinCode) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("accountId", accountId != null ? accountId : "");
        body.put("amountMagnitude", amount != null ? amount.toPlainString() : "0");
        body.put("openedOnDate", openedOn != null ? openedOn.format(DATE_FORMAT) : LocalDate.now().format(DATE_FORMAT));
        body.put("productCode", requireProductCode(productCode));
        body.put("tenureMagnitude", String.valueOf(tenureMagnitude));
        body.put("tenureUnit", tenureUnit != null ? tenureUnit : "Month");
        String branch = properties.getAdminBranch();
        if (branch != null && !branch.isBlank()) {
            body.put("branchCode", branch.trim());
        }
        putIfPresent(body, "customer1PinCode", pinCode);
        body.put("customer1CityCode", DEFAULT_ENCORE_LOCATION_CODE);
        body.put("customer1CountryCode", DEFAULT_ENCORE_LOCATION_CODE);
        body.put("customer1StateCode", DEFAULT_ENCORE_LOCATION_CODE);
        return body.toString();
    }

    private static String requireProductCode(String productCode) {
        if (productCode != null && !productCode.isBlank()) {
            return productCode.trim();
        }
        throw new BusinessRuleException(
                "No LMS product mapping is configured for this application.",
                LmsApplicationConfigResolver.REASON_LMS_PRODUCT_MAPPING_MISSING,
                "OPEN_LOAN_ACCOUNT",
                Map.of("field", "productCode"));
    }

    private static void putIfPresent(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value);
        }
    }

    private static String geoFromBorrower(LoanHandoverRequest req, String... keys) {
        if (req == null || req.getBorrowerDetails() == null) {
            return null;
        }
        return firstString(req.getBorrowerDetails(), keys);
    }

    private static String firstString(Map<String, Object> d, String... keys) {
        for (String k : keys) {
            Object v = d.get(k);
            if (v == null) continue;
            String s = String.valueOf(v).trim();
            if (!s.isEmpty()) return s;
        }
        return null;
    }

    private static String coalesce(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
