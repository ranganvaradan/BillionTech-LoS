package com.los.core.service.esign;

import com.los.core.config.EsignNotificationProperties;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.schema.los2.EsignRequest;
import com.los.core.repository.LoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EsignEmailResendService {

    private final EsignRequestTrackingService esignRequestTrackingService;
    private final LoanApplicationRepository loanApplicationRepository;
    private final EsignSigningLinkNotifier esignSigningLinkNotifier;
    private final EsignNotificationProperties esignNotificationProperties;

    /**
     * Resends email only for existing active signing link. Never calls provider APIs.
     */
    public Map<String, Object> resendSigningLink(UUID applicationId) {
        if (applicationId == null) {
            return Map.of("success", false, "message", "applicationId is required");
        }
        Optional<EsignRequest> active = esignRequestTrackingService.findLatestInitiatedLike(applicationId);
        if (active.isEmpty()) {
            log.warn("[ESIGN_EMAIL] resend rejected applicationId={} reason=no_active_initiated_pending_row", applicationId);
            return Map.of("success", false, "message", "Signing link unavailable");
        }
        EsignRequest row = active.get();
        String status = row.getStatus() != null ? row.getStatus() : "";
        if (EsignRequestStatuses.SIGNED.equalsIgnoreCase(status)) {
            return Map.of("success", false, "message", "Document already signed");
        }
        if (EsignRequestStatuses.EXPIRED.equalsIgnoreCase(status)
                || "CANCELLED".equalsIgnoreCase(status)
                || EsignRequestStatuses.FAILED.equalsIgnoreCase(status)) {
            return Map.of("success", false, "message", "Signing link unavailable");
        }
        if (row.getSigningUrl() == null || row.getSigningUrl().isBlank()) {
            return Map.of("success", false, "message", "Signing link unavailable");
        }

        String to = resolveRecipient(row);
        if (to == null || to.isBlank()) {
            log.error("[ESIGN_EMAIL][ERROR] resend rejected applicationId={} esignRequestId={} reason=missing_recipient_email",
                    applicationId, row.getId());
            return Map.of("success", false, "message", "Signing link unavailable");
        }

        LoanApplication app = loanApplicationRepository.findById(applicationId).orElse(null);
        String appNo = app != null ? app.getApplicationNumber() : "";
        String borrowerName = row.getSignerName() != null && !row.getSignerName().isBlank() ? row.getSignerName() : "Borrower";

        log.info("[ESIGN_EMAIL] resend trigger started applicationId={} esignRequestId={} workflowId={} status={}",
                applicationId, row.getId(), row.getProviderRequestId(), status);
        esignSigningLinkNotifier.publishSigningLinkEmail(
                applicationId,
                appNo,
                borrowerName,
                List.of(to),
                row.getSigningUrl(),
                esignNotificationProperties.getTemplateCode(),
                esignNotificationProperties.getLinkExpiryHours(),
                true
        );
        log.info("[ESIGN_EMAIL] resend accepted applicationId={} esignRequestId={} recipient={}",
                applicationId, row.getId(), maskEmail(to));
        return Map.of(
                "success", true,
                "message", "Signing link resent successfully",
                "esignRequestId", row.getId(),
                "providerRequestId", row.getProviderRequestId()
        );
    }

    private static String resolveRecipient(EsignRequest row) {
        if (row == null || row.getRawResponse() == null) {
            return null;
        }
        Object email = row.getRawResponse().get("losSignerEmail");
        if (email == null) {
            email = row.getRawResponse().get("borrowerEmail");
        }
        if (email == null) {
            return null;
        }
        String s = email.toString().trim();
        return s.isBlank() ? null : s;
    }

    private static String maskEmail(String email) {
        if (email == null || email.isBlank()) return "";
        int at = email.indexOf('@');
        if (at <= 1) return "*@" + (at < 0 ? "" : email.substring(at + 1));
        return email.charAt(0) + "***@" + email.substring(at + 1);
    }
}

