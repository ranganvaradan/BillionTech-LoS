package com.los.core.service.esign;

import com.los.core.service.integration.providers.IESignProvider;
import com.los.core.service.loan.LoanApplicationFlowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * EMSIGNER embedded flow: webhook / demo completion downloads the signed workflow document,
 * persists it under {@code ./uploads/signed/}, advances {@link com.los.core.model.entity.schema.los2.EsignRequest},
 * and moves the loan application beyond {@code ESIGN_PENDING} when applicable.
 */
@Slf4j
@Service
public class EsignEmsignerCompletionService {

    private final IESignProvider emsignerProvider;
    private final EsignSignedDocumentStorageService signedDocumentStorageService;
    private final EsignRequestTrackingService esignRequestTrackingService;
    private final LoanApplicationFlowService loanApplicationFlowService;

    public EsignEmsignerCompletionService(
            @Qualifier("emsignerESignProvider") IESignProvider emsignerProvider,
            EsignSignedDocumentStorageService signedDocumentStorageService,
            EsignRequestTrackingService esignRequestTrackingService,
            @Lazy LoanApplicationFlowService loanApplicationFlowService) {
        this.emsignerProvider = emsignerProvider;
        this.signedDocumentStorageService = signedDocumentStorageService;
        this.esignRequestTrackingService = esignRequestTrackingService;
        this.loanApplicationFlowService = loanApplicationFlowService;
    }

    /**
     * Production-style completion when EMSIGNER POSTs WorkflowID / WorkflowStatus.
     */
    @Transactional
    public Map<String, Object> handleWorkflowCallback(String workflowIdRaw, String workflowStatusRaw,
                                                      Map<String, Object> passthroughPayload) {
        String workflowId = workflowIdRaw != null ? workflowIdRaw.trim() : "";
        String workflowStatus = workflowStatusRaw != null ? workflowStatusRaw.trim() : "";
        log.info("[EMSIGNER webhook] WorkflowID={}, WorkflowStatus={}", workflowId, workflowStatus);

        if (workflowId.isEmpty()) {
            return failure("MISSING_WORKFLOW_ID", workflowId);
        }
        boolean completed =
                "Completed".equalsIgnoreCase(workflowStatus)
                        || "completed".equalsIgnoreCase(workflowStatus)
                        || "success".equalsIgnoreCase(workflowStatus)
                        || "signed".equalsIgnoreCase(workflowStatus);
        if (!completed) {
            log.debug("[EMSIGNER webhook] Ignoring non-terminal WorkflowStatus {}", workflowStatus);
            return Map.of(
                    "received", true,
                    "workflowId", workflowId,
                    "handled", false,
                    "message", "Ignored status: " + workflowStatus
            );
        }

        UUID applicationId = esignRequestTrackingService.findByWorkflowIdLatest(workflowId)
                .map(e -> e.getApplicationId())
                .orElse(null);
        if (applicationId == null) {
            log.warn("[EMSIGNER webhook] No esign_requests row for WorkflowID {}", workflowId);
            return failure("UNKNOWN_WORKFLOW_ID", workflowId);
        }

        return finalize(applicationId, workflowId, mergedPayload(workflowId, workflowStatus, passthroughPayload));
    }

    /**
     * Demo: assumes the borrower finished signing externally; pulls the workflow document locally.
     */
    @Transactional
    public Map<String, Object> simulateCompletion(UUID applicationId) {
        log.info("[EMSIGNER simulate] applicationId={}", applicationId);
        var row = esignRequestTrackingService.findLatestInitiatedLike(applicationId)
                .orElseThrow(() -> new IllegalStateException(
                        "No INITIATED/PENDING esign_requests row for application " + applicationId));
        String workflowId = row.getProviderRequestId();
        if (workflowId == null || workflowId.isBlank()) {
            throw new IllegalStateException("esign_requests.provider_request_id (WorkflowID) is missing");
        }
        Map<String, Object> meta = Map.of(
                "source", "SIMULATE_ESIGN_COMPLETION",
                "applicationId", applicationId.toString(),
                "WorkflowID", workflowId,
                "WorkflowStatus", "Completed"
        );
        return finalize(applicationId, workflowId, meta);
    }

    @Transactional
    public Map<String, Object> simulateCompletionByWorkflowId(String workflowId) {
        String wf = workflowId != null ? workflowId.trim() : "";
        if (wf.isBlank()) {
            throw new IllegalStateException("WorkflowID is required");
        }
        UUID applicationId = esignRequestTrackingService.findByWorkflowIdLatest(wf)
                .map(e -> e.getApplicationId())
                .orElseThrow(() -> new IllegalStateException("No esign_requests row for WorkflowID " + wf));
        log.info("[EMSIGNER simulate] workflowId={} resolved applicationId={}", wf, applicationId);
        return finalize(applicationId, wf, Map.of(
                "source", "INTERNAL_SIMULATE_BY_WORKFLOW",
                "WorkflowID", wf,
                "WorkflowStatus", "Completed"));
    }

    private Map<String, Object> finalize(UUID applicationId, String workflowId, Map<String, Object> rawMeta) {
        try {
            log.info("[EMSIGNER finalize] downloading WorkFlowId={} for application {}", workflowId, applicationId);
            byte[] pdfBytes = emsignerProvider.downloadSignedDocument(workflowId);
            if (pdfBytes != null && pdfBytes.length > 0) {
                String path = signedDocumentStorageService.saveSignedPdf(applicationId, pdfBytes);
                esignRequestTrackingService.applySignedLocalPath(applicationId, workflowId, path, rawMeta);
                log.info("[EMSIGNER finalize] stored signed PDF at {}", path);
            } else {
                log.warn("[EMSIGNER finalize] Empty download payload for WorkflowID {} — skipping file write", workflowId);
                esignRequestTrackingService.applySignedLocalPath(applicationId, workflowId,
                        null,
                        augment(rawMeta, "downloadWarning", "empty_or_missing_pdf"));
            }

            loanApplicationFlowService.completeESign(applicationId, workflowId);
            Map<String, Object> ok = successPayload(workflowId, applicationId);
            ok.put("rawWebhook", rawMeta);
            return ok;
        } catch (Exception ex) {
            log.error("[EMSIGNER finalize] Failed workflowId={}, app={}: {}",
                    workflowId, applicationId, ex.getMessage(), ex);
            esignRequestTrackingService.markWorkflowFailed(workflowId, ex.getMessage(), rawMeta);
            return failure(ex.getMessage() != null ? ex.getMessage() : "FAILURE", workflowId);
        }
    }

    private static Map<String, Object> mergedPayload(String workflowId, String workflowStatus,
                                                      Map<String, Object> passthroughPayload) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("WorkflowID", workflowId);
        m.put("WorkflowStatus", workflowStatus);
        if (passthroughPayload != null) {
            m.putAll(passthroughPayload);
        }
        return m;
    }

    private static Map<String, Object> augment(Map<String, Object> base, String key, String value) {
        Map<String, Object> m = new HashMap<>(base);
        m.put(key, value);
        return m;
    }

    private static Map<String, Object> failure(String message, String workflowId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("message", message);
        m.put("workflowId", workflowId);
        return m;
    }

    private static Map<String, Object> successPayload(String workflowId, UUID applicationId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", true);
        m.put("workflowId", workflowId);
        m.put("applicationId", applicationId.toString());
        m.put("message", "SIGNED_AND_STORED");
        return m;
    }
}
