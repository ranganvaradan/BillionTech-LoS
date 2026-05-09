package com.los.core.service.flow.step;

import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.audit.AuditService;
import com.los.core.config.EsignNotificationProperties;
import com.los.core.service.esign.EsignSigningLinkNotifier;
import com.los.core.service.esign.EsignRequestTrackingService;
import com.los.core.service.integration.IIntegrationRouterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@link FlowStepType#ESIGN} — eSign initiation via {@link IIntegrationRouterService}. Moved from
 * {@code LoanApplicationFlowService#initiateESign}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EsignInitiateStepExecutor implements IStepExecutor {

    private final LoanApplicationRepository applicationRepository;
    private final IIntegrationRouterService integrationRouter;
    private final AuditService auditService;
    private final EsignRequestTrackingService esignRequestTrackingService;
    private final EsignSigningLinkNotifier esignSigningLinkNotifier;
    private final EsignNotificationProperties esignNotificationProperties;

    @Override
    public boolean supports(String stepType) {
        return FlowStepType.ESIGN.equals(stepType);
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public StepResult execute(UUID applicationId, Map<String, Object> context) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new com.los.core.exception.ResourceNotFoundException("Application not found: " + applicationId));
        if (app.getStatus() != ApplicationStatus.SANCTION_ISSUED && app.getStatus() != ApplicationStatus.KFS_GENERATED) {
            auditService.logEvent(applicationId, "PREREQUISITE_BLOCK", "ESIGN_BLOCKED",
                    null,
                    Map.of("status", app.getStatus().name(), "reason", "KFS_NOT_READY", "action", "ESIGN_INITIATE"),
                    null,
                    "eSign blocked: requires KFS_GENERATED (or legacy SANCTION_ISSUED)");
            throw new BusinessRuleException(
                    "Cannot initiate eSign — application must be in KFS_GENERATED or SANCTION_ISSUED status. Current: "
                            + app.getStatus(),
                    "ESIGN_STATUS_INVALID",
                    "ESIGN_INITIATE",
                    Map.of("status", app.getStatus().name())
            );
        }
        Map<String, Object> signerInfo = (context != null && context.get("signerInfo") != null)
                ? (Map<String, Object>) context.get("signerInfo")
                : Map.of();
        Map<String, Object> signerForRoute = new HashMap<>(signerInfo);
        Object email = signerForRoute.get("email");
        Object borrowerEmail = signerForRoute.get("borrowerEmail");
        if ((borrowerEmail == null || borrowerEmail.toString().isBlank())
                && email != null
                && !email.toString().isBlank()) {
            signerForRoute.put("borrowerEmail", email.toString().trim());
        }

        Map<String, Object> esignPayload = new HashMap<>();
        esignPayload.put("documentKey", "KFS_AGREEMENT");
        esignPayload.put("signerInfo", signerForRoute);
        esignPayload.put("esignStepType", "ESIGN_AGREEMENT");
        IIntegrationRouterService.ESignRouteResult result = integrationRouter.routeESignRequest(applicationId, esignPayload);

        Map<String, Object> meta = result.providerMetadata();
        boolean reusedSigningUrl = meta != null && Boolean.TRUE.equals(meta.get("reusedSigningUrl"));

        if (result.success()) {
            app.setStatus(ApplicationStatus.ESIGN_PENDING);
            app.setEsignTransactionId(result.transactionId());
            app.setCurrentStepStartedAt(Instant.now());
            app = applicationRepository.save(app);

            auditService.logEvent(applicationId, "FLOW", "ESIGN_INITIATED",
                    null, Map.of("status", app.getStatus().name()),
                    Map.of("status", "ESIGN_PENDING", "esignTransactionId", result.transactionId()),
                    "eSign initiated");

            log.info("eSign initiated for {} — txnId: {}, signingUrl: {}, reusedSigningUrl: {}",
                    app.getApplicationNumber(), result.transactionId(), result.signingUrl(), reusedSigningUrl);
            if (!reusedSigningUrl) {
                try {
                    esignRequestTrackingService.recordInitiationSuccess(
                            applicationId,
                            "KFS_AGREEMENT",
                            result.providerName(),
                            result.transactionId(),
                            result.signingUrl(),
                            signerForRoute,
                            meta,
                            "ESIGN_AGREEMENT");
                } catch (Exception ex) {
                    log.warn("Failed to persist esign_requests for application {}: {}", applicationId, ex.getMessage());
                }
            } else {
                log.info("[ESIGN_PERSIST] skipped persistence (reused existing esign_requests URL) appId={} workflowId={}",
                        applicationId, result.transactionId());
            }

            dispatchSigningLinkEmail(applicationId, app, signerForRoute, result, reusedSigningUrl);
        } else {
            log.error("eSign initiation failed for {}: {}", app.getApplicationNumber(), result.errorMessage());
        }

        Map<String, Object> out = Map.of(
                "applicationId", applicationId,
                "applicationNumber", app.getApplicationNumber(),
                "status", app.getStatus().name(),
                "esignSuccess", result.success(),
                "transactionId", result.transactionId() != null ? result.transactionId() : "",
                "signingUrl", result.signingUrl() != null ? result.signingUrl() : "",
                "errorMessage", result.errorMessage() != null ? result.errorMessage() : ""
        );
        return StepResult.ok(out);
    }

    private void dispatchSigningLinkEmail(
            UUID applicationId,
            LoanApplication app,
            Map<String, Object> signerForRoute,
            IIntegrationRouterService.ESignRouteResult result,
            boolean reusedSigningUrl) {
        if (!esignNotificationProperties.isEnabled()) {
            log.info("[ESIGN_EMAIL] skipped (los.esign.notification.enabled=false) applicationId={}", applicationId);
            return;
        }
        if (reusedSigningUrl) {
            log.info("[ESIGN_EMAIL] skipped (reusedSigningUrl=true) applicationId={}", applicationId);
            return;
        }
        String rawEmail = borrowerEmailRaw(signerForRoute);
        if (rawEmail == null || rawEmail.isBlank()) {
            log.warn("[ESIGN_EMAIL] skipped (no borrower email on signer info) applicationId={}", applicationId);
            return;
        }
        String name = signerForRoute.get("name") != null ? signerForRoute.get("name").toString().trim() : "Borrower";
        try {
            esignSigningLinkNotifier.publishSigningLinkEmail(
                    applicationId,
                    app.getApplicationNumber(),
                    name,
                    List.of(rawEmail.trim()),
                    result.signingUrl() != null ? result.signingUrl() : "",
                    esignNotificationProperties.getTemplateCode(),
                    esignNotificationProperties.getLinkExpiryHours(),
                    false);
        } catch (Exception ex) {
            log.error("[ESIGN_EMAIL] unexpected failure applicationId={}: {}", applicationId, ex.getMessage(), ex);
        }
    }

    private static String borrowerEmailRaw(Map<String, Object> signerInfo) {
        if (signerInfo == null) {
            return null;
        }
        Object v = firstNonNullObj(
                signerInfo.get("borrowerEmail"),
                signerInfo.get("email"),
                signerInfo.get("signerEmail"),
                signerInfo.get("EmailId"));
        if (v == null) {
            return null;
        }
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static Object firstNonNullObj(Object... o) {
        for (Object x : o) {
            if (x != null) {
                return x;
            }
        }
        return null;
    }
}
