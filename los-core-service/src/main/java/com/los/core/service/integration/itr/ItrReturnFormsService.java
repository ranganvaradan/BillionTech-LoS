package com.los.core.service.integration.itr;

import com.fasterxml.jackson.databind.JsonNode;
import com.los.core.creditintelligence.tax.service.TaxIngestionService;
import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.dto.response.DocumentResponse;
import com.los.core.model.dto.response.KycStepResultResponse;
import com.los.core.model.entity.KycStepResult;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.KycStepType;
import com.los.core.model.enums.ProviderType;
import com.los.core.model.enums.StepOutcome;
import com.los.core.repository.KycStepResultRepository;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.audit.AuditService;
import com.los.core.service.document.IDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Borrower-facing ITR return-forms orchestration: call Karza, map metrics, store reports, persist KYC step.
 * Passwords are used only for the outbound HTTP call and are never persisted.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItrReturnFormsService {

    private final KarzaItrReturnFormsClient karzaItrReturnFormsClient;
    private final KycStepResultRepository kycStepResultRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final IDocumentService documentService;
    private final AuditService auditService;
    private final ItrWorkflowRequirement itrWorkflowRequirement;
    private final TaxIngestionService taxIngestionService;

    public Optional<KycStepResult> findLatest(UUID applicationId) {
        return kycStepResultRepository
                .findTopByApplicationIdAndStepTypeOrderByCreatedAtDesc(applicationId, KycStepType.ITR_RETURN_FORMS);
    }

    public boolean hasSuccessfulPull(UUID applicationId) {
        return findLatest(applicationId)
                .filter(r -> r.getOutcome() == StepOutcome.SUCCESS || r.isOverridden())
                .isPresent();
    }

    public boolean isMandatoryForApplication(UUID applicationId) {
        LoanApplication app = loanApplicationRepository.findById(applicationId).orElse(null);
        if (app == null) {
            return false;
        }
        return itrWorkflowRequirement.isMandatoryInActiveWorkflow(app);
    }

    /**
     * Execute ITR pull with borrower credentials. Password is discarded after the call.
     */
    @Transactional
    public KycStepResultResponse submitCredentials(
            UUID applicationId,
            UUID borrowerUserId,
            String username,
            String password,
            boolean consent) {
        LoanApplication app = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
        if (borrowerUserId != null && app.getCustomerId() != null && !borrowerUserId.equals(app.getCustomerId())) {
            throw new BusinessRuleException("You can only complete ITR verification for your own application");
        }
        if (!consent) {
            throw new BusinessRuleException("Consent is required to pull ITR data");
        }
        if (username == null || username.isBlank()) {
            throw new BusinessRuleException("ITR username is required");
        }
        if (password == null || password.isBlank()) {
            throw new BusinessRuleException("ITR password is required");
        }

        int attemptNumber = findLatest(applicationId)
                .map(r -> r.getAttemptNumber() + 1)
                .orElse(1);

        KarzaItrReturnFormsClient.KarzaItrHttpResult httpResult =
                karzaItrReturnFormsClient.fetch(username.trim(), password, applicationId);
        // password falls out of scope — never store it

        Map<String, Object> parsedData = new LinkedHashMap<>();
        parsedData.put("username", username.trim());
        parsedData.put("consent", true);
        parsedData.put("requestId", httpResult.requestId());
        parsedData.put("karzaStatusCode", httpResult.karzaStatusCode());
        parsedData.put("simulated", httpResult.simulated());

        KycStepResult stepResult = KycStepResult.builder()
                .applicationId(applicationId)
                .stepType(KycStepType.ITR_RETURN_FORMS)
                .provider(ProviderType.KARZA)
                .attemptNumber(attemptNumber)
                .transactionId(httpResult.requestId())
                .rawResponse(null) // full body already in api_audit_log; keep entity lean
                .build();

        if (!httpResult.success()) {
            stepResult.setOutcome(StepOutcome.FAILURE);
            stepResult.setErrorMessage(httpResult.errorMessage() != null
                    ? httpResult.errorMessage()
                    : "ITR verification failed");
            parsedData.put("error", stepResult.getErrorMessage());
            stepResult.setParsedData(parsedData);
            stepResult.setCompletedAt(Instant.now());
            stepResult = kycStepResultRepository.save(stepResult);

            auditService.logEvent(applicationId, "KYC", "ITR_RETURN_FORMS FAILURE",
                    borrowerUserId, null,
                    Map.of(
                            "stepType", KycStepType.ITR_RETURN_FORMS.name(),
                            "outcome", StepOutcome.FAILURE.name(),
                            "attemptNumber", attemptNumber,
                            "requestId", httpResult.requestId() != null ? httpResult.requestId() : ""),
                    "ITR return-forms pull failed");
            return toResponse(stepResult);
        }

        JsonNode result = httpResult.result();
        Map<String, Object> metrics = ItrReturnFormsMapper.mapMetrics(result);
        parsedData.put("mappedMetrics", metrics);
        if (result != null) {
            parsedData.put("result", ItrReturnFormsMapper.jsonToMap(result));
        }
        if (httpResult.root() != null) {
            parsedData.put("statusCode", httpResult.karzaStatusCode());
        }

        List<Map<String, Object>> storedDocs = new ArrayList<>();
        List<String> downloadWarnings = new ArrayList<>();
        String pan = metrics.get("panNumber") != null
                ? String.valueOf(metrics.get("panNumber"))
                : username.trim().toUpperCase();
        for (Map<String, String> link : ItrReturnFormsMapper.collectDownloadLinks(result, pan, httpResult.requestId())) {
            try {
                DocumentResponse doc = downloadAndStore(
                        applicationId, link.get("url"), link.get("fileName"), link.get("contentType"));
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("documentId", doc.getId() != null ? doc.getId().toString() : null);
                summary.put("fileName", doc.getFileName());
                summary.put("documentType", doc.getDocumentType());
                storedDocs.add(summary);
            } catch (Exception e) {
                log.warn("ITR document download failed for {}: {}", link.get("fileName"), e.getMessage());
                downloadWarnings.add(link.get("fileName") + ": " + e.getMessage());
            }
        }
        parsedData.put("storedDocuments", storedDocs);
        if (!downloadWarnings.isEmpty()) {
            parsedData.put("documentDownloadWarnings", downloadWarnings);
        }

        stepResult.setOutcome(StepOutcome.SUCCESS);
        stepResult.setConfidenceScore(httpResult.simulated() ? 0.5 : 0.95);
        stepResult.setParsedData(parsedData);
        stepResult.setCompletedAt(Instant.now());
        stepResult = kycStepResultRepository.save(stepResult);

        try {
            taxIngestionService.ingestFromItrStep(
                    app, parsedData, stepResult.getId(), httpResult.requestId());
        } catch (Exception e) {
            log.warn("Tax canonicalization hook failed (non-fatal) for {}: {}", applicationId, e.getMessage());
        }

        auditService.logEvent(applicationId, "KYC", "ITR_RETURN_FORMS SUCCESS",
                borrowerUserId, null,
                Map.of(
                        "stepType", KycStepType.ITR_RETURN_FORMS.name(),
                        "outcome", StepOutcome.SUCCESS.name(),
                        "attemptNumber", attemptNumber,
                        "requestId", httpResult.requestId() != null ? httpResult.requestId() : "",
                        "documentsStored", storedDocs.size()),
                "ITR return-forms pull succeeded");
        return toResponse(stepResult);
    }

    private DocumentResponse downloadAndStore(
            UUID applicationId, String url, String fileName, String contentType) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(90))
                .GET()
                .build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        byte[] bytes = response.body();
        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException("Empty download body");
        }
        String ct = contentType;
        Optional<String> headerCt = response.headers().firstValue("Content-Type");
        if (headerCt.isPresent() && headerCt.get() != null && !headerCt.get().isBlank()) {
            ct = headerCt.get().split(";")[0].trim();
        }
        return documentService.storeDocumentBytes(applicationId, "ITR", fileName, ct, bytes);
    }

    private KycStepResultResponse toResponse(KycStepResult r) {
        return KycStepResultResponse.builder()
                .id(r.getId())
                .applicationId(r.getApplicationId())
                .stepType(r.getStepType())
                .provider(r.getProvider())
                .outcome(r.getOutcome())
                .confidenceScore(r.getConfidenceScore())
                .parsedData(r.getParsedData())
                .transactionId(r.getTransactionId())
                .errorMessage(r.getErrorMessage())
                .overridden(r.isOverridden())
                .overrideReason(r.getOverrideReason())
                .attemptNumber(r.getAttemptNumber())
                .createdAt(r.getCreatedAt())
                .completedAt(r.getCompletedAt())
                .build();
    }
}
