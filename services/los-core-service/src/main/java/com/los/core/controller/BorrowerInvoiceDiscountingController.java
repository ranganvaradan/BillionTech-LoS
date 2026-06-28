package com.los.core.controller;

import com.los.core.model.dto.request.BorrowerFinanceRequest;
import com.los.core.model.dto.request.BorrowerRepaymentRequest;
import com.los.core.model.dto.response.BorrowerInvoiceRepaymentItemResponse;
import com.los.core.model.dto.response.BorrowerInvoiceDiscountingResponse;
import com.los.core.service.borrower.BorrowerInvoiceDiscountingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Borrower Invoice discounting: list invoices, request finance, and repay invoice-discounting loans.
 * Backed by PLP (proxied server-side); degrades gracefully when PLP is unavailable.
 */
@RestController
@RequestMapping("/api/v1/borrower/invoice-discounting")
@RequiredArgsConstructor
@Tag(name = "Borrower invoice discounting", description = "List invoices, request finance, and repay (PLP-backed)")
public class BorrowerInvoiceDiscountingController {

    private final BorrowerInvoiceDiscountingService service;

    @GetMapping
    @Operation(summary = "Borrower invoices and invoice-discounting loans")
    public ResponseEntity<BorrowerInvoiceDiscountingResponse> overview(
            @RequestParam(required = false) String flowType,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        UUID uid = UUID.fromString(userId);
        service.requireBorrower(role);
        return ResponseEntity.ok(service.overview(uid, flowType));
    }

    @PostMapping("/invoices")
    @Operation(summary = "Create a seller-initiated invoice (SBD/PO)")
    public ResponseEntity<BorrowerInvoiceDiscountingResponse> createInvoice(
            @RequestBody java.util.Map<String, Object> body,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        UUID uid = UUID.fromString(userId);
        service.requireBorrower(role);
        return ResponseEntity.ok(service.createInvoice(uid, body));
    }

    @PostMapping("/invoices/{invoiceId}/accept")
    @Operation(summary = "Accept an eligible purchase-flow invoice before requesting finance")
    public ResponseEntity<BorrowerInvoiceDiscountingResponse> acceptInvoice(
            @PathVariable UUID invoiceId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        UUID uid = UUID.fromString(userId);
        service.requireBorrower(role);
        return ResponseEntity.ok(service.acceptInvoice(uid, invoiceId));
    }

    @GetMapping("/invoices/{invoiceId}/digital-invoice")
    @Operation(summary = "Download digital invoice copy (proxied from PLP)")
    public ResponseEntity<byte[]> downloadDigitalInvoice(
            @PathVariable UUID invoiceId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        UUID uid = UUID.fromString(userId);
        service.requireBorrower(role);
        com.los.plp.client.PlpBorrowerClient.DigitalInvoiceFile file =
                service.downloadDigitalInvoice(uid, invoiceId);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            if (file.contentType() != null && !file.contentType().isBlank()) {
                mediaType = MediaType.parseMediaType(file.contentType());
            }
        } catch (Exception ignored) {
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        boolean inline =
                MediaType.APPLICATION_PDF.equals(mediaType) || "image".equalsIgnoreCase(mediaType.getType());
        String safeName = file.fileName() != null ? file.fileName() : "digital-invoice";
        ContentDisposition disposition =
                (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                        .filename(safeName, StandardCharsets.UTF_8)
                        .build();
        headers.setContentDisposition(disposition);
        return ResponseEntity.ok().headers(headers).body(file.body());
    }

    @PostMapping("/invoices/{invoiceId}/finance")
    @Operation(summary = "Request finance against an eligible invoice")
    public ResponseEntity<BorrowerInvoiceDiscountingResponse> requestFinance(
            @PathVariable UUID invoiceId,
            @Valid @RequestBody BorrowerFinanceRequest request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        UUID uid = UUID.fromString(userId);
        service.requireBorrower(role);
        return ResponseEntity.ok(service.requestFinance(uid, invoiceId, request.getAmount()));
    }

    @PostMapping("/loans/{loanId}/repay")
    @Operation(summary = "Repay an invoice-discounting loan")
    public ResponseEntity<BorrowerInvoiceDiscountingResponse> repay(
            @PathVariable UUID loanId,
            @Valid @RequestBody BorrowerRepaymentRequest request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        UUID uid = UUID.fromString(userId);
        service.requireBorrower(role);
        return ResponseEntity.ok(service.repayLoan(uid, loanId, request.getAmount()));
    }

    @GetMapping("/loans/{loanId}/repayments")
    @Operation(summary = "Repayment history for an invoice-discounting loan")
    public ResponseEntity<java.util.List<BorrowerInvoiceRepaymentItemResponse>> repayments(
            @PathVariable UUID loanId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        UUID uid = UUID.fromString(userId);
        service.requireBorrower(role);
        return ResponseEntity.ok(service.listRepayments(uid, loanId));
    }

    @GetMapping("/payments/cart")
    @Operation(summary = "Payment cart lines (PayU)")
    public ResponseEntity<java.util.List<java.util.Map<String, Object>>> paymentCart(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        UUID uid = UUID.fromString(userId);
        service.requireBorrower(role);
        return ResponseEntity.ok(service.listPaymentCart(uid));
    }

    @GetMapping("/payments/cart/count")
    public ResponseEntity<Long> paymentCartCount(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        UUID uid = UUID.fromString(userId);
        service.requireBorrower(role);
        return ResponseEntity.ok(service.paymentCartCount(uid));
    }

    @PostMapping("/payments/cart/lines")
    public ResponseEntity<Void> addCartLine(
            @RequestBody java.util.Map<String, String> body,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        UUID uid = UUID.fromString(userId);
        service.requireBorrower(role);
        service.addPaymentCartLine(uid, UUID.fromString(body.get("invoiceId")));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/payments/cart/lines/bulk")
    public ResponseEntity<Void> addCartBulk(
            @RequestBody java.util.Map<String, java.util.List<String>> body,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        UUID uid = UUID.fromString(userId);
        service.requireBorrower(role);
        java.util.List<UUID> ids = body.getOrDefault("invoiceIds", java.util.List.of()).stream()
                .map(UUID::fromString)
                .toList();
        service.addPaymentCartBulk(uid, ids);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/payments/cart/lines/{lineId}")
    public ResponseEntity<Void> removeCartLine(
            @PathVariable UUID lineId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        UUID uid = UUID.fromString(userId);
        service.requireBorrower(role);
        service.removePaymentCartLine(uid, lineId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/payments/payu/initiate")
    public ResponseEntity<java.util.Map<String, Object>> initiatePayu(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        UUID uid = UUID.fromString(userId);
        service.requireBorrower(role);
        return ResponseEntity.ok(service.initiatePayuPayment(uid));
    }
}
