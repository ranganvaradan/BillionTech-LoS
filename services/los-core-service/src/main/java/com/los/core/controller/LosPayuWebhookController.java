package com.los.core.controller;

import com.los.core.payment.service.LosLoanPayuPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Public PayU browser callbacks for LOS personal/term loan repayments (no JWT). */
@RestController
@RequestMapping("/api/v1/webhooks/los-payments/payu")
@RequiredArgsConstructor
@Slf4j
public class LosPayuWebhookController {

    private final LosLoanPayuPaymentService paymentService;

    @PostMapping(value = "/success", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> success(@RequestParam Map<String, String> params) {
        log.info("LOS PayU success callback txn={}", params.get("txnid"));
        try {
            return htmlRedirect(paymentService.handlePayuSuccess(params));
        } catch (Exception e) {
            log.error("LOS PayU success handling failed: {}", e.getMessage(), e);
            return htmlRedirect(paymentService.handlePayuFailure(params));
        }
    }

    @PostMapping(value = "/failure", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> failure(@RequestParam Map<String, String> params) {
        return htmlRedirect(paymentService.handlePayuFailure(params));
    }

    private ResponseEntity<String> htmlRedirect(String redirectUrl) {
        String safe = redirectUrl.replace("\"", "%22");
        String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\">"
                + "<meta http-equiv=\"refresh\" content=\"0;url=" + safe + "\">"
                + "<title>Redirecting…</title></head><body><p>Redirecting to payment result…</p>"
                + "<script>location.href=" + jsonString(redirectUrl) + ";</script></body></html>";
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    private static String jsonString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
