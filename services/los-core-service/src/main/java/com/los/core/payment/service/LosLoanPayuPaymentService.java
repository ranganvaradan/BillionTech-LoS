package com.los.core.payment.service;

import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.ForbiddenException;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.LosUser;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.payment.PayuHashUtil;
import com.los.core.payment.config.LosPayuProperties;
import com.los.core.payment.model.LosLoanPaymentInProgress;
import com.los.core.payment.model.LosLoanPaymentTransaction;
import com.los.core.payment.repository.LosLoanPaymentInProgressRepository;
import com.los.core.payment.repository.LosLoanPaymentTransactionRepository;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.repository.LosUserRepository;
import com.los.core.service.loan.InvoiceDiscountingApplicationRules;
import com.los.core.service.repayment.LoanProductRepaymentDefaultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LosLoanPayuPaymentService {

    public static final String PAYMENT_METHOD_PAYU = "PAYU_PG";
    public static final String PIP_OPEN = "OPEN";
    public static final String PORTAL_SOURCE = "LOS_LOAN";

    private final LosLoanPaymentTransactionRepository transactionRepository;
    private final LosLoanPaymentInProgressRepository pipRepository;
    private final LoanApplicationRepository applicationRepository;
    private final LosUserRepository losUserRepository;
    private final LoanProductRepaymentDefaultService repaymentDefaultService;
    private final LosPayuProperties payuProperties;
    private final LosPayuVerificationService payuVerificationService;

    public boolean isPayuConfigured() {
        return payuProperties.getMerchantKey() != null && !payuProperties.getMerchantKey().isBlank()
                && payuProperties.getMerchantSalt() != null && !payuProperties.getMerchantSalt().isBlank();
    }

    @Transactional
    public Map<String, Object> initiatePayu(UUID borrowerUserId, UUID applicationId, BigDecimal amount) {
        LoanApplication app = loadOwnedDisbursed(borrowerUserId, applicationId);
        requirePayuRepayment(app);
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Enter a valid repayment amount.");
        }
        if (!isPayuConfigured()) {
            throw new IllegalStateException("PayU merchant credentials are not configured on LOS (los.payu.*)");
        }

        String txnRef = payuProperties.getTransactionIdSuffix() + "-"
                + applicationId.toString().substring(0, 8) + "-" + Instant.now().toEpochMilli();
        LosLoanPaymentTransaction txn = LosLoanPaymentTransaction.builder()
                .pgTransactionRef(txnRef)
                .borrowerUserId(borrowerUserId)
                .applicationId(applicationId)
                .totalAmount(amount)
                .gateway("PAYU")
                .status("INITIATED")
                .portalSource(PORTAL_SOURCE)
                .build();
        txn = transactionRepository.save(txn);

        LosUser user = losUserRepository.findById(borrowerUserId).orElse(null);
        String email = user != null && user.getEmail() != null ? user.getEmail() : "borrower@example.com";
        String name = user != null && user.getName() != null ? user.getName() : "Borrower";
        String phone = user != null ? user.getMobile() : null;
        String amountStr = PayuHashUtil.formatAmount(amount);
        String productinfo = "LOS Loan Repayment " + app.getApplicationNumber();
        String hash = PayuHashUtil.forwardHash(
                payuProperties.getMerchantKey(),
                txnRef,
                amountStr,
                productinfo,
                name,
                email,
                borrowerUserId.toString(),
                applicationId.toString(),
                payuProperties.getMerchantSalt());
        String callbackBase = payuProperties.getCallbackBaseUrl().replaceAll("/$", "");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("baseUrl", payuProperties.getGatewayUrl());
        result.put("key", payuProperties.getMerchantKey());
        result.put("txnid", txnRef);
        result.put("amount", amountStr);
        result.put("productinfo", productinfo);
        result.put("firstname", name);
        result.put("email", email);
        result.put("phone", phone);
        result.put("udf1", borrowerUserId.toString());
        result.put("udf2", applicationId.toString());
        result.put("hash", hash);
        result.put("surl", callbackBase + "/api/v1/webhooks/los-payments/payu/success");
        result.put("furl", callbackBase + "/api/v1/webhooks/los-payments/payu/failure");
        result.put("transactionId", txn.getId().toString());
        result.put("applicationId", applicationId.toString());
        return result;
    }

    @Transactional
    public String handlePayuSuccess(Map<String, String> params) {
        String txnid = params.get("txnid");
        String status = params.get("status");
        String hash = params.get("hash");
        LosLoanPaymentTransaction txn = transactionRepository.findByPgTransactionRef(txnid)
                .orElseThrow(() -> new IllegalArgumentException("Unknown transaction: " + txnid));
        if ("SUCCESS".equals(txn.getStatus())) {
            return redirectUrl(txn, true);
        }
        if (status == null || !"success".equalsIgnoreCase(status.trim())) {
            log.warn("PayU success callback with non-success status {} for txn {}", status, txnid);
            return handlePayuFailure(params);
        }
        boolean hashValid = PayuHashUtil.reverseHashMatches(
                params, payuProperties.getMerchantSalt(), payuProperties.getMerchantKey(), hash);
        if (!hashValid) {
            log.warn("PayU hash mismatch for LOS loan txn {}", txnid);
            hashValid = payuVerificationService.isSuccessful(txnid);
            if (hashValid) {
                log.info("PayU verify_payment confirmed success for LOS loan txn {}", txnid);
            }
        }
        if (!hashValid) {
            return handlePayuFailure(params);
        }
        txn.setStatus("SUCCESS");
        txn.setPayuMihpayid(params.get("mihpayid"));
        txn.setRawCallbackJson(new LinkedHashMap<>(params));
        transactionRepository.save(txn);

        LoanApplication app = applicationRepository.findById(txn.getApplicationId()).orElse(null);
        LosLoanPaymentInProgress pip = LosLoanPaymentInProgress.builder()
                .pgTransactionId(txn.getId())
                .applicationId(txn.getApplicationId())
                .applicationNumber(app != null ? app.getApplicationNumber() : null)
                .loanProduct(app != null ? app.getLoanProduct() : null)
                .borrowerUserId(txn.getBorrowerUserId())
                .principalAmount(txn.getTotalAmount())
                .pipStatus(PIP_OPEN)
                .build();
        pipRepository.save(pip);
        return redirectUrl(txn, true);
    }

    @Transactional
    public String handlePayuFailure(Map<String, String> params) {
        String txnid = params.get("txnid");
        if (txnid == null) {
            return payuProperties.getBorrowerUiUrl().replaceAll("/$", "") + "/loans/payments/result?status=failure";
        }
        transactionRepository.findByPgTransactionRef(txnid).ifPresent(txn -> {
            txn.setStatus("FAILED");
            txn.setRawCallbackJson(new LinkedHashMap<>(params));
            transactionRepository.save(txn);
        });
        LosLoanPaymentTransaction txn = transactionRepository.findByPgTransactionRef(txnid).orElse(null);
        return redirectUrl(txn, false);
    }

    public void requirePayuRepayment(LoanApplication app) {
        if (InvoiceDiscountingApplicationRules.isInvoiceDiscounting(app)) {
            throw new BusinessRuleException(
                    "Invoice discounting repayments use PLP PayU — open Invoice Discounting in the borrower portal.");
        }
        String mechanism = repaymentDefaultService.resolveMechanism(app.getLoanProduct());
        if (!PAYMENT_METHOD_PAYU.equals(mechanism)) {
            throw new BusinessRuleException("PayU is not enabled for this loan product.");
        }
    }

    private LoanApplication loadOwnedDisbursed(UUID borrowerUserId, UUID applicationId) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessRuleException("Loan not found."));
        if (!borrowerUserId.equals(app.getCustomerId())) {
            throw new ForbiddenException("You do not have access to this loan.");
        }
        if (app.getStatus() != ApplicationStatus.DISBURSED) {
            throw new ForbiddenException("Repayments are available after disbursement.");
        }
        return app;
    }

    private String redirectUrl(LosLoanPaymentTransaction txn, boolean success) {
        String base = payuProperties.getBorrowerUiUrl().replaceAll("/$", "");
        String status = success ? "success" : "failure";
        String txnRef = txn != null ? txn.getPgTransactionRef() : "";
        String appId = txn != null ? txn.getApplicationId().toString() : "";
        return base + "/loans/" + appId + "/payments/result?status=" + status + "&txnId=" + txnRef;
    }
}
