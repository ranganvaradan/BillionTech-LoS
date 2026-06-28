package com.los.core.service.integration.webhook;

import com.los.core.service.audit.AuditService;
import com.los.core.service.esign.EsignRequestTrackingService;
import com.los.core.service.loan.LoanApplicationFlowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Application-level handling for integration callbacks. Invoked from
 * {@code IIntegrationRouterService#handleWebhookCallback} for supported providers
 * (e.g. eSign) so webhooks are not tied to a single controller class.
 */
@Slf4j
@Component
public class IntegrationCallbackProcessor {

    private final AuditService auditService;
    private final LoanApplicationFlowService flowService;
    private final EsignRequestTrackingService esignRequestTrackingService;

    public IntegrationCallbackProcessor(
            AuditService auditService,
            @Lazy LoanApplicationFlowService flowService,
            EsignRequestTrackingService esignRequestTrackingService) {
        this.auditService = auditService;
        this.flowService = flowService;
        this.esignRequestTrackingService = esignRequestTrackingService;
    }

    /**
     * @param providerName normalized (e.g. EMSIGNER)
     * @param payload      may include {@code webhookKind} = KFS | AGREEMENT | SANCTION | NACH
     */
    public Map<String, Object> handleEsignLikeCallback(String providerName, Map<String, Object> payload) {
        String kind = String.valueOf(payload.getOrDefault("webhookKind", "KFS"));
        return switch (kind) {
            case "AGREEMENT" -> processAgreementLike(payload, "ESIGN_WEBHOOK_AGREEMENT", "Agreement");
            case "KFS" -> processAgreementLike(payload, "ESIGN_WEBHOOK_KFS", "KFS");
            case "SANCTION" -> processSanction(payload);
            case "NACH" -> processNach(payload);
            default -> processAgreementLike(payload, "ESIGN_WEBHOOK_KFS", "KFS");
        };
    }

    private Map<String, Object> processAgreementLike(
            Map<String, Object> payload, String auditAction, String label) {
        String transactionId = String.valueOf(payload.getOrDefault("transactionId", ""));
        String status = String.valueOf(payload.getOrDefault("status", ""));
        String applicationId = String.valueOf(payload.getOrDefault("applicationId", ""));

        log.info("eSign callback [{}] — provider flow: {} txnId={}, status={}, appId={}", label, auditAction, transactionId, status, applicationId);

        boolean transitioned = false;
        if (!applicationId.isEmpty() && !"null".equals(applicationId)) {
            try {
                UUID appId = UUID.fromString(applicationId);
                auditService.logEvent(appId, auditAction,
                        Map.of("transactionId", transactionId, "status", status));

                if ("completed".equalsIgnoreCase(status) || "success".equalsIgnoreCase(status)) {
                    try {
                        flowService.completeESign(appId, transactionId);
                        transitioned = true;
                        log.info("eSign {} callback: application {} completed", label, applicationId);
                        esignRequestTrackingService.updateFromAgreementCallback(
                                appId, transactionId, status, payload);
                    } catch (Exception ex) {
                        log.warn("Could not transition application {} on eSign ({}): {}", applicationId, label, ex.getMessage());
                    }
                } else {
                    esignRequestTrackingService.updateFromAgreementCallback(appId, transactionId, status, payload);
                }
            } catch (Exception e) {
                log.warn("Could not process eSign {} webhook for appId={}: {}", label, applicationId, e.getMessage());
            }
        }

        return Map.of(
                "received", true,
                "transactionId", transactionId,
                "transitioned", transitioned,
                "status", "PROCESSED"
        );
    }

    private Map<String, Object> processSanction(Map<String, Object> payload) {
        String transactionId = String.valueOf(payload.getOrDefault("transactionId", ""));
        String status = String.valueOf(payload.getOrDefault("status", ""));
        String applicationId = String.valueOf(payload.getOrDefault("applicationId", ""));
        String signerType = String.valueOf(payload.getOrDefault("signerType", "LENDER"));

        log.info("eSign callback [SANCTION] txnId={}, status={}, appId={}", transactionId, status, applicationId);

        if (!applicationId.isEmpty() && !"null".equals(applicationId)) {
            try {
                auditService.logEvent(UUID.fromString(applicationId), "ESIGN_WEBHOOK_SANCTION",
                        Map.of("transactionId", transactionId, "status", status, "signerType", signerType));
            } catch (Exception e) {
                log.warn("Could not log audit event for eSign sanction webhook", e);
            }
        }

        return Map.of(
                "received", true,
                "transactionId", transactionId,
                "signerType", signerType,
                "status", "PROCESSED"
        );
    }

    private Map<String, Object> processNach(Map<String, Object> payload) {
        String transactionId = String.valueOf(payload.getOrDefault("transactionId", ""));
        String status = String.valueOf(payload.getOrDefault("status", ""));
        String mandateRef = String.valueOf(payload.getOrDefault("mandateReference", ""));
        String umrn = String.valueOf(payload.getOrDefault("umrn", ""));

        log.info("eSign callback [NACH] txnId={}, status={}, mandateRef={}, UMRN={}", transactionId, status, mandateRef, umrn);

        return Map.of(
                "received", true,
                "transactionId", transactionId,
                "mandateReference", mandateRef,
                "status", "PROCESSED"
        );
    }
}
