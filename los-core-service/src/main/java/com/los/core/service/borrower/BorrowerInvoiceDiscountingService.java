package com.los.core.service.borrower;

import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.ForbiddenException;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.dto.response.BorrowerInvoiceRepaymentItemResponse;
import com.los.core.model.dto.response.BorrowerInvoiceDiscountingResponse;
import com.los.core.model.dto.response.BorrowerInvoiceItemResponse;
import com.los.core.model.dto.response.BorrowerInvoiceLoanResponse;
import com.los.core.repository.LosUserRepository;
import com.los.plp.client.PlpBorrowerClient;
import com.los.plp.client.PlpIntegrationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Borrower-facing Invoice discounting, backed by the PLP platform (proxied via {@link PlpBorrowerClient}).
 *
 * <p>Mirrors the PLP borrower portal flow without changing the existing LOS flow/design: list the borrower's
 * invoices, request finance against an eligible invoice (creates a PLP invoice-discounting loan), and repay
 * invoice-discounting loans. When PLP is disabled, the borrower has no PLP identity yet, or PLP is unreachable,
 * the page degrades to a calm "not available yet" state rather than erroring.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BorrowerInvoiceDiscountingService {

    private static final String ROLE = "BORROWER";

    /** Loan statuses that accept a repayment. */
    private static final Set<String> REPAYABLE_LOAN_STATUSES = Set.of(
            "DISBURSED", "REPAYMENT_DUE", "OVERDUE");

    private static final String INVOICE_DISCOUNTING_PRODUCT = "INVOICE_DISCOUNTING";

    private final LosUserRepository losUserRepository;
    private final PlpBorrowerClient plpBorrowerClient;
    private final BorrowerApplicationOwnershipService ownershipService;

    public void requireBorrower(String role) {
        if (role == null || !ROLE.equalsIgnoreCase(role.trim())) {
            throw new ForbiddenException("This area is only for borrowers. Sign in with a borrower account.");
        }
    }

    /** Invoices + invoice-discounting loans for the borrower, or a graceful unavailable payload. */
    public BorrowerInvoiceDiscountingResponse overview(UUID borrowerUserId) {
        return overview(borrowerUserId, null);
    }

    public BorrowerInvoiceDiscountingResponse overview(UUID borrowerUserId, String flowType) {
        losUserRepository.findById(borrowerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!plpBorrowerClient.isEnabled()) {
            return unavailable("Invoice discounting is not enabled in this environment.");
        }
        Optional<UUID> plpBorrowerId = resolvePlpBorrowerId(borrowerUserId);
        if (plpBorrowerId.isEmpty()) {
            return unavailable(
                    "Invoice discounting will be available once your invoice-discounting application has been "
                            + "set up with the anchor program. Apply for an invoice discounting loan to get started.");
        }
        try {
            List<Map<String, Object>> rawInvoices = plpBorrowerClient.listInvoices(plpBorrowerId.get(), flowType);
            List<Map<String, Object>> rawLoans = plpBorrowerClient.listLoans(plpBorrowerId.get());
            List<BorrowerInvoiceItemResponse> invoices = new ArrayList<>();
            for (Map<String, Object> inv : rawInvoices) {
                invoices.add(toInvoice(inv));
            }
            List<BorrowerInvoiceLoanResponse> loans = new ArrayList<>();
            for (Map<String, Object> loan : rawLoans) {
                if (isInvoiceDiscountingLoan(loan)) {
                    loans.add(toLoan(loan));
                }
            }
            invoices = enrichInvoicesWithLoanOutstanding(invoices, loans);
            boolean paymentEnabled = invoices.stream().anyMatch(BorrowerInvoiceItemResponse::isEnablePaymentForBorrower);
            String paymentMethod = "SMART_COLLECT";
            String hdfcAccountNo = null;
            String hdfcIfscCode = null;
            List<Map<String, Object>> paymentEligibility = List.of();
            try {
                Map<String, Object> profile = plpBorrowerClient.getPaymentProfile(plpBorrowerId.get());
                Object pm = profile.get("paymentMethod");
                if (pm != null) {
                    paymentMethod = pm.toString();
                }
                hdfcAccountNo = asString(profile.get("hdfcAccountNo"));
                hdfcIfscCode = asString(profile.get("hdfcIfscCode"));
            } catch (PlpIntegrationException e) {
                log.debug("Payment method unavailable for borrower {}: {}", borrowerUserId, e.getMessage());
            }
            try {
                paymentEligibility = plpBorrowerClient.listEligibleInvoices(plpBorrowerId.get());
            } catch (PlpIntegrationException e) {
                log.debug("Payment eligibility unavailable for borrower {}: {}", borrowerUserId, e.getMessage());
            }
            return BorrowerInvoiceDiscountingResponse.builder()
                    .available(true)
                    .message(null)
                    .paymentMethod(paymentMethod)
                    .paymentEnabled(paymentEnabled)
                    .hdfcAccountNo(hdfcAccountNo)
                    .hdfcIfscCode(hdfcIfscCode)
                    .paymentEligibility(paymentEligibility)
                    .invoices(invoices)
                    .loans(loans)
                    .build();
        } catch (PlpIntegrationException e) {
            log.warn("Invoice discounting unavailable for borrower {}: {}", borrowerUserId, e.getMessage());
            return unavailable("Invoice discounting is temporarily unavailable. Please try again shortly.");
        }
    }

    /** Purchase-flow: accept an ELIGIBLE invoice before requesting finance; returns the refreshed overview. */
    public BorrowerInvoiceDiscountingResponse acceptInvoice(UUID borrowerUserId, UUID invoiceId) {
        UUID plpBorrowerId = resolvePlpBorrowerId(borrowerUserId)
                .orElseThrow(() -> new BusinessRuleException(
                        "Invoice discounting is not set up for your account yet."));
        Map<String, Object> invoice = findInvoice(plpBorrowerId, invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found for your account."));
        String status = asString(invoice.get("status"));
        String flowType = asString(invoice.get("flowType"));
        if (!InvoiceDiscountingFlowRules.acceptable(status, flowType)) {
            throw new BusinessRuleException("This invoice cannot be accepted right now.");
        }
        try {
            plpBorrowerClient.acceptInvoice(plpBorrowerId, invoiceId);
        } catch (PlpIntegrationException e) {
            throw new BusinessRuleException("Could not accept invoice: " + e.getMessage());
        }
        return overview(borrowerUserId, asString(invoice.get("flowType")));
    }

    public BorrowerInvoiceDiscountingResponse deleteInvoice(UUID borrowerUserId, UUID invoiceId) {
        UUID plpBorrowerId = resolvePlpBorrowerId(borrowerUserId)
                .orElseThrow(() -> new BusinessRuleException(
                        "Invoice discounting is not set up for your account yet."));
        Map<String, Object> invoice = findInvoice(plpBorrowerId, invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found for your account."));
        String status = asString(invoice.get("status"));
        String flowType = asString(invoice.get("flowType"));
        boolean allowed = resolveInvoiceDeleteAllowed(asUuid(invoice.get("programId")));
        if (!InvoiceDiscountingFlowRules.deletableByBorrower(status, flowType, allowed)) {
            throw new BusinessRuleException("This invoice cannot be deleted right now.");
        }
        try {
            plpBorrowerClient.deleteBorrowerInvoice(plpBorrowerId, invoiceId);
        } catch (PlpIntegrationException e) {
            throw new BusinessRuleException("Could not delete invoice: " + e.getMessage());
        }
        return overview(borrowerUserId, flowType);
    }

    public BorrowerInvoiceDiscountingResponse createInvoice(
            UUID borrowerUserId, Map<String, Object> invoiceBody) {
        UUID plpBorrowerId = resolvePlpBorrowerId(borrowerUserId)
                .orElseThrow(() -> new BusinessRuleException(
                        "Invoice discounting is not set up for your account yet."));
        try {
            Map<String, Object> created = plpBorrowerClient.createBorrowerInvoice(plpBorrowerId, invoiceBody);
            String flowType = asString(created.get("flowType"));
            UUID createdId = asUuid(created.get("id"));
            BorrowerInvoiceDiscountingResponse base = overview(borrowerUserId, flowType);
            return BorrowerInvoiceDiscountingResponse.builder()
                    .available(base.isAvailable())
                    .message(base.getMessage())
                    .paymentMethod(base.getPaymentMethod())
                    .paymentEnabled(base.isPaymentEnabled())
                    .createdInvoiceId(createdId != null ? createdId.toString() : null)
                    .invoices(base.getInvoices())
                    .loans(base.getLoans())
                    .build();
        } catch (PlpIntegrationException e) {
            throw new BusinessRuleException("Could not create invoice: " + e.getMessage());
        }
    }

    public List<BorrowerInvoiceRepaymentItemResponse> listRepayments(UUID borrowerUserId, UUID loanId) {
        UUID plpBorrowerId = resolvePlpBorrowerId(borrowerUserId)
                .orElseThrow(() -> new BusinessRuleException(
                        "Invoice discounting is not set up for your account yet."));
        if (!ownsLoan(plpBorrowerId, loanId)) {
            throw new ForbiddenException("You do not have access to this loan.");
        }
        try {
            List<Map<String, Object>> raw = plpBorrowerClient.listLoanRepayments(loanId);
            List<BorrowerInvoiceRepaymentItemResponse> items = new ArrayList<>();
            for (Map<String, Object> row : raw) {
                items.add(toRepayment(row));
            }
            return items;
        } catch (PlpIntegrationException e) {
            log.warn("Repayment history unavailable for loan {}: {}", loanId, e.getMessage());
            return List.of();
        }
    }

    /** Unfinanced invoice repayments from HDFC / manual advance payments (no loan). */
    public List<BorrowerInvoiceRepaymentItemResponse> listUnfinancedRepayments(
            UUID borrowerUserId, UUID invoiceId) {
        UUID plpBorrowerId = resolvePlpBorrowerId(borrowerUserId)
                .orElseThrow(() -> new BusinessRuleException(
                        "Invoice discounting is not set up for your account yet."));
        Optional<Map<String, Object>> invoice = findInvoice(plpBorrowerId, invoiceId);
        if (invoice.isEmpty()) {
            throw new ResourceNotFoundException("Invoice not found for your account.");
        }
        try {
            List<Map<String, Object>> raw = plpBorrowerClient.listUnfinancedRepayments(plpBorrowerId, invoiceId);
            List<BorrowerInvoiceRepaymentItemResponse> items = new ArrayList<>();
            for (Map<String, Object> row : raw) {
                String source = asString(row.get("source"));
                items.add(BorrowerInvoiceRepaymentItemResponse.builder()
                        .repaymentId(asString(row.get("id")))
                        .paidAt(asString(row.get("paidAt")))
                        .amount(asBig(row.get("amount")))
                        .reference(asString(row.get("reference")))
                        .source(source)
                        .status(asString(row.get("status")))
                        .paymentMode(asString(row.get("allocationType")))
                        .friendlySource(friendlyUnfinancedRepaymentSource(source))
                        .build());
            }
            return items;
        } catch (PlpIntegrationException e) {
            log.warn("Unfinanced repayment history unavailable for invoice {}: {}", invoiceId, e.getMessage());
            return List.of();
        }
    }

    public List<Map<String, Object>> listCreditNotes(UUID borrowerUserId, String status) {
        UUID plpBorrowerId = resolvePlpBorrowerId(borrowerUserId)
                .orElseThrow(() -> new BusinessRuleException(
                        "Invoice discounting is not set up for your account yet."));
        try {
            return plpBorrowerClient.listCreditNotes(plpBorrowerId, status);
        } catch (PlpIntegrationException e) {
            log.warn("Credit notes unavailable for borrower {}: {}", borrowerUserId, e.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> getCreditNote(UUID borrowerUserId, UUID creditNoteId) {
        UUID plpBorrowerId = resolvePlpBorrowerId(borrowerUserId)
                .orElseThrow(() -> new BusinessRuleException(
                        "Invoice discounting is not set up for your account yet."));
        try {
            Map<String, Object> data = plpBorrowerClient.getCreditNote(plpBorrowerId, creditNoteId);
            if (data == null || data.isEmpty()) {
                throw new ResourceNotFoundException("Credit note not found.");
            }
            return data;
        } catch (PlpIntegrationException e) {
            throw new ResourceNotFoundException("Credit note not found.");
        }
    }

    /** Request finance for one invoice; returns the refreshed overview. */
    public BorrowerInvoiceDiscountingResponse requestFinance(UUID borrowerUserId, UUID invoiceId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Enter a valid finance amount.");
        }
        UUID plpBorrowerId = resolvePlpBorrowerId(borrowerUserId)
                .orElseThrow(() -> new BusinessRuleException(
                        "Invoice discounting is not set up for your account yet."));
        // Resolve the invoice so we can pass its program and validate ownership/eligibility before requesting.
        Map<String, Object> invoice = findInvoice(plpBorrowerId, invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found for your account."));
        String status = asString(invoice.get("status"));
        String flowType = asString(invoice.get("flowType"));
        if (!InvoiceDiscountingFlowRules.financeable(status, flowType)) {
            if (InvoiceDiscountingFlowRules.acceptable(status, flowType)) {
                throw new BusinessRuleException("Accept this invoice before requesting finance.");
            }
            throw new BusinessRuleException("This invoice is not eligible for a finance request right now.");
        }
        BigDecimal max = firstNonNullPositive(
                asBig(invoice.get("availableAmount")),
                asBig(invoice.get("eligibleAmount")),
                asBig(invoice.get("netAmount")),
                asBig(invoice.get("invoiceAmount")));
        BigDecimal balanceDue = firstNonNull(
                asBig(invoice.get("balanceDueAmount")), asBig(invoice.get("balDueAmount")));
        if (balanceDue != null && balanceDue.compareTo(BigDecimal.ZERO) > 0) {
            max = max == null ? balanceDue : max.min(balanceDue);
        }
        if (max != null && amount.compareTo(max) > 0) {
            throw new BusinessRuleException(
                    "Finance amount cannot exceed " + max.toPlainString() + " (remaining for this invoice).");
        }
        UUID programId = asUuid(invoice.get("programId"));
        try {
            plpBorrowerClient.requestFinance(plpBorrowerId, invoiceId, amount, programId);
        } catch (PlpIntegrationException e) {
            throw new BusinessRuleException("Could not submit the finance request: " + e.getMessage());
        }
        return overview(borrowerUserId, flowType);
    }

    public List<Map<String, Object>> listEligibleInvoices(UUID borrowerUserId) {
        UUID plpBorrowerId = resolvePlpBorrowerId(borrowerUserId)
                .orElseThrow(() -> new BusinessRuleException("Invoice discounting is not set up for your account yet."));
        return plpBorrowerClient.listEligibleInvoices(plpBorrowerId);
    }

    public List<Map<String, Object>> listPaymentCart(UUID borrowerUserId) {
        UUID plpBorrowerId = resolvePlpBorrowerId(borrowerUserId)
                .orElseThrow(() -> new BusinessRuleException("Invoice discounting is not set up for your account yet."));
        return plpBorrowerClient.listPaymentCart(plpBorrowerId);
    }

    public long paymentCartCount(UUID borrowerUserId) {
        return resolvePlpBorrowerId(borrowerUserId).map(plpBorrowerClient::paymentCartCount).orElse(0L);
    }

    public void addPaymentCartLine(UUID borrowerUserId, UUID invoiceId) {
        UUID plpBorrowerId = resolvePlpBorrowerId(borrowerUserId)
                .orElseThrow(() -> new BusinessRuleException("Invoice discounting is not set up for your account yet."));
        plpBorrowerClient.addPaymentCartLine(plpBorrowerId, invoiceId);
    }

    public void addPaymentCartBulk(UUID borrowerUserId, List<UUID> invoiceIds) {
        UUID plpBorrowerId = resolvePlpBorrowerId(borrowerUserId)
                .orElseThrow(() -> new BusinessRuleException("Invoice discounting is not set up for your account yet."));
        plpBorrowerClient.addPaymentCartBulk(plpBorrowerId, invoiceIds);
    }

    public void removePaymentCartLine(UUID borrowerUserId, UUID lineId) {
        UUID plpBorrowerId = resolvePlpBorrowerId(borrowerUserId)
                .orElseThrow(() -> new BusinessRuleException("Invoice discounting is not set up for your account yet."));
        plpBorrowerClient.removePaymentCartLine(plpBorrowerId, lineId);
    }

    public Map<String, Object> initiatePayuPayment(UUID borrowerUserId) {
        UUID plpBorrowerId = resolvePlpBorrowerId(borrowerUserId)
                .orElseThrow(() -> new BusinessRuleException("Invoice discounting is not set up for your account yet."));
        return plpBorrowerClient.initiatePayuPayment(plpBorrowerId);
    }

    public Map<String, Object> getEarlyPayTodayParameter(UUID borrowerUserId, UUID subProgramId) {
        UUID plpBorrowerId = resolvePlpBorrowerId(borrowerUserId)
                .orElseThrow(() -> new BusinessRuleException("Invoice discounting is not set up for your account yet."));
        try {
            return plpBorrowerClient.getEarlyPayTodayParameter(plpBorrowerId, subProgramId)
                    .orElseThrow(() -> new BusinessRuleException("No Early Pay parameter for today"));
        } catch (PlpIntegrationException e) {
            throw new BusinessRuleException("Could not load Early Pay parameters: " + e.getMessage());
        }
    }

    public BorrowerInvoiceDiscountingResponse createEarlyPayRequest(
            UUID borrowerUserId, UUID invoiceId, UUID epParameterId, BigDecimal requestedAmount) {
        UUID plpBorrowerId = resolvePlpBorrowerId(borrowerUserId)
                .orElseThrow(() -> new BusinessRuleException("Invoice discounting is not set up for your account yet."));
        Optional<Map<String, Object>> inv = findInvoice(plpBorrowerId, invoiceId);
        if (inv.isEmpty()) {
            throw new ResourceNotFoundException("Invoice not found");
        }
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("invoiceId", invoiceId.toString());
        body.put("epParameterId", epParameterId.toString());
        if (requestedAmount != null) {
            body.put("requestedAmount", requestedAmount);
        }
        try {
            plpBorrowerClient.createEarlyPayRequest(plpBorrowerId, body);
        } catch (PlpIntegrationException e) {
            throw new BusinessRuleException("Early Pay request failed: " + e.getMessage());
        }
        return overview(borrowerUserId, asString(inv.get().get("flowType")));
    }

    /** Download digital invoice copy (proxied from PLP) when the invoice belongs to this borrower. */
    public PlpBorrowerClient.DigitalInvoiceFile downloadDigitalInvoice(UUID borrowerUserId, UUID invoiceId) {
        UUID plpBorrowerId = resolvePlpBorrowerId(borrowerUserId)
                .orElseThrow(() -> new BusinessRuleException(
                        "Invoice discounting is not set up for your account yet."));
        Optional<Map<String, Object>> inv = findInvoice(plpBorrowerId, invoiceId);
        if (inv.isEmpty()) {
            throw new ResourceNotFoundException("Invoice not found");
        }
        String fileName = asString(inv.get().get("digitalInvoiceFileName"));
        if (fileName == null || fileName.isBlank()) {
            throw new BusinessRuleException("Digital invoice file not available");
        }
        try {
            return plpBorrowerClient.downloadDigitalInvoice(invoiceId);
        } catch (PlpIntegrationException e) {
            throw new BusinessRuleException("Could not download digital invoice: " + e.getMessage());
        }
    }

    /** Upload digital invoice copy for a borrower-owned PLP invoice. */
    public void uploadDigitalInvoice(UUID borrowerUserId, UUID invoiceId, byte[] bytes, String filename, String contentType) {
        UUID plpBorrowerId = resolvePlpBorrowerId(borrowerUserId)
                .orElseThrow(() -> new BusinessRuleException(
                        "Invoice discounting is not set up for your account yet."));
        Optional<Map<String, Object>> inv = findInvoice(plpBorrowerId, invoiceId);
        if (inv.isEmpty()) {
            throw new ResourceNotFoundException("Invoice not found");
        }
        try {
            plpBorrowerClient.uploadDigitalInvoice(invoiceId, bytes, filename, contentType);
        } catch (PlpIntegrationException e) {
            throw new BusinessRuleException("Could not upload invoice copy: " + e.getMessage());
        }
    }

    /** Repay an invoice-discounting loan; returns the refreshed overview. */
    public BorrowerInvoiceDiscountingResponse repayLoan(UUID borrowerUserId, UUID loanId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Enter a valid repayment amount.");
        }
        UUID plpBorrowerId = resolvePlpBorrowerId(borrowerUserId)
                .orElseThrow(() -> new BusinessRuleException(
                        "Invoice discounting is not set up for your account yet."));
        // Ownership check: the loan must belong to this borrower.
        boolean owns;
        try {
            owns = plpBorrowerClient.listLoans(plpBorrowerId).stream()
                    .anyMatch(l -> loanId.equals(asUuid(l.get("id"))));
        } catch (PlpIntegrationException e) {
            throw new BusinessRuleException("Could not verify the loan: " + e.getMessage());
        }
        if (!owns) {
            throw new ForbiddenException("You do not have access to this loan.");
        }
        try {
            plpBorrowerClient.repay(loanId, amount);
        } catch (PlpIntegrationException e) {
            throw new BusinessRuleException("Could not record the repayment: " + e.getMessage());
        }
        return overview(borrowerUserId);
    }

    // ----- helpers -----

    private Optional<UUID> resolvePlpBorrowerId(UUID borrowerUserId) {
        return ownershipService.resolvePlpBorrowerId(borrowerUserId);
    }

    private boolean ownsLoan(UUID plpBorrowerId, UUID loanId) {
        return plpBorrowerClient.listLoans(plpBorrowerId).stream()
                .anyMatch(l -> loanId.equals(asUuid(l.get("id"))));
    }

    private BorrowerInvoiceRepaymentItemResponse toRepayment(Map<String, Object> row) {
        String source = asString(row.get("source"));
        return BorrowerInvoiceRepaymentItemResponse.builder()
                .repaymentId(asString(row.get("id")))
                .paidAt(asString(row.get("paidAt")))
                .amount(asBig(row.get("amount")))
                .reference(asString(row.get("reference")))
                .source(source)
                .status(asString(row.get("status")))
                .paymentMode(asString(row.get("paymentMode")))
                .friendlySource(friendlyRepaymentSource(source))
                .build();
    }

    private static String friendlyUnfinancedRepaymentSource(String source) {
        if (source == null) {
            return "Advance payment";
        }
        return switch (source.toUpperCase()) {
            case "HDFC-ALERT" -> "HDFC advance payment";
            case "ADMIN" -> "Manual advance payment";
            default -> source;
        };
    }

    private static String friendlyRepaymentSource(String source) {
        if (source == null) {
            return "Payment";
        }
        return switch (source.toUpperCase()) {
            case "LMS", "ENCORE" -> "LMS";
            case "RECORDED" -> "Recorded";
            case "AUDIT" -> "System";
            default -> source;
        };
    }

    private Optional<Map<String, Object>> findInvoice(UUID plpBorrowerId, UUID invoiceId) {
        return plpBorrowerClient.listInvoices(plpBorrowerId).stream()
                .filter(inv -> invoiceId.equals(asUuid(inv.get("id"))))
                .findFirst();
    }

    private static boolean isInvoiceDiscountingLoan(Map<String, Object> loan) {
        String product = asString(loan.get("productType"));
        // Treat loans tied to an invoice as invoice discounting even if productType naming differs.
        return INVOICE_DISCOUNTING_PRODUCT.equalsIgnoreCase(product) || loan.get("invoiceId") != null;
    }

    private static boolean repayable(String status) {
        return status != null && REPAYABLE_LOAN_STATUSES.contains(status.toUpperCase());
    }

    private BorrowerInvoiceItemResponse toInvoice(Map<String, Object> inv) {
        String status = asString(inv.get("status"));
        String flowType = asString(inv.get("flowType"));
        BigDecimal available = asBig(inv.get("availableAmount"));
        BigDecimal eligible = asBig(inv.get("eligibleAmount"));
        BigDecimal net = asBig(inv.get("netAmount"));
        BigDecimal invoiceAmount = asBig(inv.get("invoiceAmount"));
        BigDecimal balanceDue = firstNonNull(asBig(inv.get("balanceDueAmount")), asBig(inv.get("balDueAmount")));
        BigDecimal max = firstNonNullPositive(available, eligible, net, invoiceAmount);
        if (balanceDue != null && balanceDue.compareTo(BigDecimal.ZERO) > 0) {
            max = max == null ? balanceDue : max.min(balanceDue);
        }
        boolean canFinance = InvoiceDiscountingFlowRules.financeable(status, flowType)
                && max != null && max.compareTo(BigDecimal.ZERO) > 0;
        String isEarlyPayAllowed = asString(inv.get("isEarlyPayAllowed"));
        String showEarlyPay = asString(inv.get("showEarlyPay"));
        boolean earlyPayable = InvoiceDiscountingFlowRules.earlyPayable(
                status, flowType, isEarlyPayAllowed, showEarlyPay);
        UUID programId = asUuid(inv.get("programId"));
        boolean invoiceDeleteAllowed = resolveInvoiceDeleteAllowed(programId);
        boolean enablePaymentForBorrower = resolveBorrowerPaymentAllowed(programId);
        boolean deletable = InvoiceDiscountingFlowRules.deletableByBorrower(status, flowType, invoiceDeleteAllowed);
        return BorrowerInvoiceItemResponse.builder()
                .invoiceId(asString(inv.get("id")))
                .invoiceNumber(asString(inv.get("invoiceNumber")))
                .invoiceDate(asString(inv.get("invoiceDate")))
                .dueDate(asString(inv.get("dueDate")))
                .invoiceAmount(invoiceAmount)
                .netAmount(net)
                .eligibleAmount(eligible)
                .availableAmount(available)
                .status(status)
                .friendlyStatus(friendlyInvoiceStatus(status))
                .programId(asString(inv.get("programId")))
                .anchorId(asString(inv.get("anchorId")))
                .flowType(flowType)
                .acceptable(InvoiceDiscountingFlowRules.acceptable(status, flowType))
                .financeable(canFinance)
                .maxFinanceableAmount(max)
                .suggestedFinanceAmount(canFinance ? max : null)
                .pipAmount(asBig(inv.get("pipAmount")))
                .subProgramId(asString(inv.get("subProgramId")))
                .isEarlyPayAllowed(isEarlyPayAllowed)
                .showEarlyPay(showEarlyPay)
                .balDueAmount(balanceDue)
                .earlyPayable(earlyPayable)
                .digitalInvoiceFileName(asString(inv.get("digitalInvoiceFileName")))
                .digitalInvoiceContentType(asString(inv.get("digitalInvoiceContentType")))
                .deletable(deletable)
                .enablePaymentForBorrower(enablePaymentForBorrower)
                .build();
    }

    private boolean resolveInvoiceDeleteAllowed(UUID programId) {
        if (programId == null) {
            return false;
        }
        try {
            Map<String, Object> program = plpBorrowerClient.getProgram(programId);
            Object params = program.get("parameters");
            if (!(params instanceof Map<?, ?> map)) {
                return false;
            }
            Object raw = map.get("invoiceDelete");
            if (raw == null) {
                return false;
            }
            String s = raw.toString().trim();
            return "true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s) || "y".equalsIgnoreCase(s);
        } catch (Exception e) {
            log.debug("Could not resolve invoiceDelete for program {}: {}", programId, e.getMessage());
            return false;
        }
    }

    private boolean resolveBorrowerPaymentAllowed(UUID programId) {
        if (programId == null) {
            return false;
        }
        try {
            Map<String, Object> program = plpBorrowerClient.getProgram(programId);
            Object params = program.get("parameters");
            if (!(params instanceof Map<?, ?> map)) {
                return false;
            }
            Object raw = map.get("enablePaymentForBorrower");
            if (raw == null) {
                return false;
            }
            String s = raw.toString().trim();
            return "true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s) || "y".equalsIgnoreCase(s);
        } catch (Exception e) {
            log.debug("Could not resolve enablePaymentForBorrower for program {}: {}", programId, e.getMessage());
            return false;
        }
    }

    /**
     * When PLP reports zero available (e.g. fully discounted) but a live loan has LMS outstanding,
     * surface that balance in the invoice list "Available" column.
     */
    private static List<BorrowerInvoiceItemResponse> enrichInvoicesWithLoanOutstanding(
            List<BorrowerInvoiceItemResponse> invoices,
            List<BorrowerInvoiceLoanResponse> loans) {
        Map<String, BigDecimal> outstandingByInvoice = new HashMap<>();
        for (BorrowerInvoiceLoanResponse loan : loans) {
            String invId = loan.getInvoiceId();
            if (invId == null || invId.isBlank()) {
                continue;
            }
            BigDecimal outstanding = loan.getOutstandingAmount();
            if (outstanding == null || outstanding.compareTo(BigDecimal.ZERO) <= 0) {
                outstanding = loan.getTotalRepayable();
            }
            if (outstanding != null && outstanding.compareTo(BigDecimal.ZERO) > 0) {
                outstandingByInvoice.merge(invId, outstanding, BigDecimal::add);
            }
        }
        if (outstandingByInvoice.isEmpty()) {
            return invoices;
        }
        List<BorrowerInvoiceItemResponse> enriched = new ArrayList<>();
        for (BorrowerInvoiceItemResponse inv : invoices) {
            BigDecimal outstanding = outstandingByInvoice.get(inv.getInvoiceId());
            if (outstanding == null) {
                enriched.add(inv);
                continue;
            }
            BigDecimal current = inv.getAvailableAmount();
            if (current != null && current.compareTo(BigDecimal.ZERO) > 0) {
                enriched.add(inv);
                continue;
            }
            enriched.add(copyInvoiceWithAvailable(inv, outstanding));
        }
        return enriched;
    }

    private static BorrowerInvoiceItemResponse copyInvoiceWithAvailable(
            BorrowerInvoiceItemResponse inv, BigDecimal availableAmount) {
        return BorrowerInvoiceItemResponse.builder()
                .invoiceId(inv.getInvoiceId())
                .invoiceNumber(inv.getInvoiceNumber())
                .invoiceDate(inv.getInvoiceDate())
                .dueDate(inv.getDueDate())
                .invoiceAmount(inv.getInvoiceAmount())
                .netAmount(inv.getNetAmount())
                .eligibleAmount(inv.getEligibleAmount())
                .availableAmount(availableAmount)
                .status(inv.getStatus())
                .friendlyStatus(inv.getFriendlyStatus())
                .programId(inv.getProgramId())
                .anchorId(inv.getAnchorId())
                .flowType(inv.getFlowType())
                .acceptable(inv.isAcceptable())
                .financeable(inv.isFinanceable())
                .maxFinanceableAmount(inv.getMaxFinanceableAmount())
                .suggestedFinanceAmount(inv.getSuggestedFinanceAmount())
                .pipAmount(inv.getPipAmount())
                .subProgramId(inv.getSubProgramId())
                .isEarlyPayAllowed(inv.getIsEarlyPayAllowed())
                .showEarlyPay(inv.getShowEarlyPay())
                .balDueAmount(inv.getBalDueAmount())
                .earlyPayable(inv.isEarlyPayable())
                .digitalInvoiceFileName(inv.getDigitalInvoiceFileName())
                .digitalInvoiceContentType(inv.getDigitalInvoiceContentType())
                .deletable(inv.isDeletable())
                .enablePaymentForBorrower(inv.isEnablePaymentForBorrower())
                .build();
    }

    private BorrowerInvoiceLoanResponse toLoan(Map<String, Object> loan) {
        String status = asString(loan.get("status"));
        BigDecimal requested = asBig(loan.get("requestedAmount"));
        BigDecimal sanctioned = asBig(loan.get("sanctionedAmount"));
        BigDecimal disbursed = asBig(loan.get("disbursedAmount"));
        BigDecimal principal = firstNonNullPositive(disbursed, sanctioned, requested);
        BigDecimal totalRepayable = asBig(loan.get("totalRepayable"));
        return BorrowerInvoiceLoanResponse.builder()
                .loanId(asString(loan.get("id")))
                .loanNumber(asString(loan.get("loanNumber")))
                .invoiceId(asString(loan.get("invoiceId")))
                .status(status)
                .friendlyStatus(friendlyLoanStatus(status))
                .requestedAmount(principal != null ? principal : requested)
                .sanctionedAmount(sanctioned != null ? sanctioned : principal)
                .disbursedAmount(disbursed)
                .outstandingAmount(asBig(loan.get("outstandingAmount")))
                .interestAmount(resolveInterestAmount(loan, principal, totalRepayable))
                .totalRepayable(totalRepayable)
                .totalRepaid(asBig(loan.get("totalRepaid")))
                .dueDate(asString(loan.get("dueDate")))
                .repayable(repayable(status))
                .build();
    }

    private static BigDecimal resolveInterestAmount(
            Map<String, Object> loan,
            BigDecimal principal,
            BigDecimal totalRepayable) {
        BigDecimal explicit = firstNonNullPositive(
                asBig(loan.get("interestAmount")),
                asBig(loan.get("totalNormalInterestDue")),
                asBig(loan.get("normalInterestDue")));
        if (explicit != null) {
            return explicit;
        }
        if (principal != null
                && totalRepayable != null
                && totalRepayable.compareTo(principal) > 0) {
            return totalRepayable.subtract(principal);
        }
        return null;
    }

    private static BorrowerInvoiceDiscountingResponse unavailable(String message) {
        return BorrowerInvoiceDiscountingResponse.builder()
                .available(false)
                .message(message)
                .invoices(List.of())
                .loans(List.of())
                .build();
    }

    private static String friendlyInvoiceStatus(String status) {
        if (status == null) {
            return "";
        }
        return switch (status.toUpperCase()) {
            case "UPLOADED" -> "Uploaded";
            case "VERIFIED" -> "Verified";
            case "ELIGIBLE" -> "Eligible for finance";
            case "BORROWER_ACCEPTED" -> "Accepted";
            case "FINANCING_REQUESTED" -> "Finance requested";
            case "PARTIALLY_DISCOUNTED" -> "Partially financed";
            case "FULLY_DISCOUNTED" -> "Fully financed";
            case "REJECTED" -> "Rejected";
            case "CLOSED" -> "Closed";
            default -> humanize(status);
        };
    }

    private static String friendlyLoanStatus(String status) {
        if (status == null) {
            return "";
        }
        return switch (status.toUpperCase()) {
            case "REQUESTED" -> "Finance requested";
            case "SANCTIONED" -> "Sanctioned";
            case "DISBURSEMENT_PENDING" -> "Disbursement pending";
            case "DISBURSED" -> "Disbursed";
            case "REPAYMENT_DUE" -> "Repayment due";
            case "OVERDUE" -> "Overdue";
            case "CLOSED" -> "Closed";
            case "REJECTED" -> "Rejected";
            default -> humanize(status);
        };
    }

    private static String humanize(String raw) {
        String s = raw.replace('_', ' ').trim().toLowerCase();
        return s.isEmpty() ? raw : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static BigDecimal firstNonNull(BigDecimal... values) {
        for (BigDecimal v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static BigDecimal firstNonNullPositive(BigDecimal... values) {
        for (BigDecimal v : values) {
            if (v != null && v.compareTo(BigDecimal.ZERO) > 0) {
                return v;
            }
        }
        return null;
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static UUID asUuid(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(o).trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static BigDecimal asBig(Object o) {
        if (o == null) {
            return null;
        }
        try {
            String s = String.valueOf(o).trim();
            return s.isEmpty() ? null : new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
