package com.los.core.service.integration.gstanalysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.dto.response.DocumentResponse;
import com.los.core.model.dto.response.KycStepResultResponse;
import com.los.core.model.entity.Document;
import com.los.core.model.entity.KycStepResult;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.KycStepType;
import com.los.core.model.enums.ProviderType;
import com.los.core.model.enums.StepOutcome;
import com.los.core.repository.DocumentRepository;
import com.los.core.repository.KycStepResultRepository;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.creditintelligence.gst.service.GstIngestionService;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * GST PDF analysis lifecycle: prepare (gstin + consent), upload multiparts, admin generate report.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GstAnalysisService {

    public static final String PHASE_PREPARE = "PREPARE";
    public static final String PHASE_UPLOAD = "UPLOAD";
    public static final String PHASE_REPORT = "REPORT";

    private final KarzaGstDocsUploadClient karzaGstDocsUploadClient;
    private final KycStepResultRepository kycStepResultRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final DocumentRepository documentRepository;
    private final IDocumentService documentService;
    private final AuditService auditService;
    private final GstWorkflowRequirement gstWorkflowRequirement;
    private final GstIngestionService gstIngestionService;

    public Optional<KycStepResult> findLatest(UUID applicationId) {
        return kycStepResultRepository
                .findTopByApplicationIdAndStepTypeOrderByCreatedAtDesc(applicationId, KycStepType.GST_ANALYSIS);
    }

    public boolean isOnWorkflow(UUID applicationId) {
        LoanApplication app = loanApplicationRepository.findById(applicationId).orElse(null);
        return app != null && gstWorkflowRequirement.isOnActiveWorkflow(app);
    }

    public boolean isMandatory(UUID applicationId) {
        LoanApplication app = loanApplicationRepository.findById(applicationId).orElse(null);
        return app != null && gstWorkflowRequirement.isMandatoryInActiveWorkflow(app);
    }

    /** True when GST analysis has a report SUCCESS (or staff override) for underwriting gates. */
    public boolean isReportCompleteForUnderwriting(UUID applicationId) {
        return gstWorkflowRequirement.hasSuccessfulReport(applicationId);
    }

    /**
     * Save GSTIN + consent for later upload (also mirrors gstin into businessInfo when missing).
     */
    @Transactional
    public Map<String, Object> prepare(
            UUID applicationId,
            UUID borrowerUserId,
            String gstin,
            boolean consent) {
        LoanApplication app = loadOwnedApp(applicationId, borrowerUserId);
        if (!consent) {
            throw new BusinessRuleException("Consent is required for GST analysis");
        }
        String cleanGstin = requireGstin(gstin != null ? gstin : readPreparedGstin(app));
        List<Document> docs = listAnalysisDocuments(applicationId);
        if (docs.isEmpty()) {
            throw new BusinessRuleException("Upload at least one GST return PDF for GST analysis");
        }

        Map<String, Object> bi = app.getBusinessInfo() != null
                ? new LinkedHashMap<>(app.getBusinessInfo())
                : new LinkedHashMap<>();
        bi.put("gstAnalysisGstin", cleanGstin);
        bi.put("gstAnalysisConsent", true);
        if (bi.get("gstin") == null || String.valueOf(bi.get("gstin")).isBlank()) {
            bi.put("gstin", cleanGstin);
        }
        app.setBusinessInfo(bi);
        loanApplicationRepository.save(app);

        int attemptNumber = findLatest(applicationId).map(r -> r.getAttemptNumber() + 1).orElse(1);
        Map<String, Object> parsed = new LinkedHashMap<>();
        parsed.put("phase", PHASE_PREPARE);
        parsed.put("gstin", cleanGstin);
        parsed.put("consent", true);
        parsed.put("documentIds", docs.stream().map(d -> d.getId().toString()).toList());
        parsed.put("documentCount", docs.size());

        KycStepResult step = KycStepResult.builder()
                .applicationId(applicationId)
                .stepType(KycStepType.GST_ANALYSIS)
                .provider(ProviderType.KARZA)
                .outcome(StepOutcome.PENDING)
                .attemptNumber(attemptNumber)
                .parsedData(parsed)
                .completedAt(Instant.now())
                .build();
        step = kycStepResultRepository.save(step);

        auditService.logEvent(applicationId, "KYC", "GST_ANALYSIS PREPARE",
                borrowerUserId, null,
                Map.of("gstin", cleanGstin, "documentCount", docs.size()),
                "GST analysis prepare saved");
        return statusBody(applicationId, step);
    }

    /**
     * Fail-closed prepare gate when GST_ANALYSIS is on the workflow and mandatory.
     */
    public void requirePreparedIfMandatory(LoanApplication app, String context) {
        if (app == null || !gstWorkflowRequirement.isMandatoryInActiveWorkflow(app)) {
            return;
        }
        assertPrepared(app.getId(), app);
    }

    /**
     * Validate gstin + consent + ≥1 PDF. Throws if incomplete.
     */
    public void assertPrepared(UUID applicationId, LoanApplication app) {
        LoanApplication a = app != null ? app : loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
        String gstin = resolveGstin(a);
        if (gstin == null || gstin.isBlank()) {
            throw new BusinessRuleException(
                    "GST analysis requires GSTIN and consent with at least one GST return PDF before " + "submitting.");
        }
        if (!resolveConsent(a)) {
            throw new BusinessRuleException(
                    "GST analysis requires consent and at least one GST return PDF before submitting.");
        }
        if (listAnalysisDocuments(applicationId).isEmpty()) {
            throw new BusinessRuleException(
                    "GST analysis requires at least one GST return PDF before submitting.");
        }
    }

    /**
     * Call upload from stored docs. Does not throw on Karza failure — persists FAILURE result.
     */
    @Transactional
    public KycStepResultResponse uploadFromStoredDocs(UUID applicationId, UUID actingUserId, String actor) {
        LoanApplication app = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
        String gstin = resolveGstin(app);
        if (gstin == null || gstin.isBlank()) {
            throw new BusinessRuleException("GSTIN is required for GST analysis upload");
        }
        List<Document> docs = listAnalysisDocuments(applicationId);
        if (docs.isEmpty()) {
            throw new BusinessRuleException("No GST return PDFs found for upload");
        }

        List<KarzaGstDocsUploadClient.GstFilePart> parts = new ArrayList<>();
        List<String> docIds = new ArrayList<>();
        for (Document doc : docs) {
            try {
                byte[] bytes = documentService.downloadDocument(doc.getId());
                if (bytes == null || bytes.length == 0) {
                    continue;
                }
                parts.add(new KarzaGstDocsUploadClient.GstFilePart(
                        doc.getFileName(),
                        doc.getContentType() != null ? doc.getContentType() : "application/pdf",
                        bytes));
                docIds.add(doc.getId().toString());
            } catch (Exception e) {
                log.warn("Skip GST doc {} for upload: {}", doc.getId(), e.getMessage());
            }
        }
        if (parts.isEmpty()) {
            throw new BusinessRuleException("Could not read GST return PDF content for upload");
        }

        int attemptNumber = findLatest(applicationId).map(r -> r.getAttemptNumber() + 1).orElse(1);
        KarzaGstDocsUploadClient.KarzaGstHttpResult http =
                karzaGstDocsUploadClient.upload(gstin, parts, applicationId);

        Map<String, Object> parsed = new LinkedHashMap<>();
        parsed.put("phase", PHASE_UPLOAD);
        parsed.put("gstin", gstin);
        parsed.put("consent", true);
        parsed.put("documentIds", docIds);
        parsed.put("documentCount", parts.size());
        parsed.put("requestId", http.requestId());
        parsed.put("karzaStatusCode", http.karzaStatusCode());
        parsed.put("simulated", http.simulated());
        parsed.put("actor", actor == null ? "SYSTEM" : actor);
        if (http.result() != null) {
            parsed.put("uploadResult", GstAnalysisMapper.jsonToMap(http.result()));
        }
        if (http.root() != null) {
            parsed.put("statusCode", http.karzaStatusCode());
            // Full response for admin popup / verification history (no secrets)
            parsed.put("fullResponse", GstAnalysisMapper.jsonToMap(http.root()));
        }

        KycStepResult step = KycStepResult.builder()
                .applicationId(applicationId)
                .stepType(KycStepType.GST_ANALYSIS)
                .provider(ProviderType.KARZA)
                .attemptNumber(attemptNumber)
                .transactionId(http.requestId())
                .rawResponse(null)
                .build();

        if (!http.success()) {
            step.setOutcome(StepOutcome.FAILURE);
            step.setErrorMessage(http.errorMessage() != null ? http.errorMessage() : "GST upload failed");
            parsed.put("error", step.getErrorMessage());
            step.setParsedData(parsed);
            step.setCompletedAt(Instant.now());
            step = kycStepResultRepository.save(step);
            auditService.logEvent(applicationId, "KYC", "GST_ANALYSIS UPLOAD FAILURE",
                    actingUserId, null,
                    Map.of(
                            "requestId", http.requestId() != null ? http.requestId() : "",
                            "attemptNumber", attemptNumber,
                            "actor", actor == null ? "" : actor),
                    "GST analysis upload failed");
            return toResponse(step);
        }

        step.setOutcome(StepOutcome.SUCCESS);
        step.setConfidenceScore(http.simulated() ? 0.5 : 0.9);
        step.setParsedData(parsed);
        step.setCompletedAt(Instant.now());
        step = kycStepResultRepository.save(step);
        auditService.logEvent(applicationId, "KYC", "GST_ANALYSIS UPLOAD SUCCESS",
                actingUserId, null,
                Map.of(
                        "requestId", http.requestId() != null ? http.requestId() : "",
                        "attemptNumber", attemptNumber,
                        "fileCount", parts.size()),
                "GST analysis upload succeeded");
        return toResponse(step);
    }

    /**
     * Best-effort upload previously used on submit. Prefer admin-driven
     * {@link #uploadFromStoredDocs} / {@link #retryUpload} instead; kept for internal use only.
     */
    public void uploadOnSubmitSoft(UUID applicationId, String actor) {
        try {
            LoanApplication app = loanApplicationRepository.findById(applicationId).orElse(null);
            if (app == null || !gstWorkflowRequirement.isOnActiveWorkflow(app)) {
                return;
            }
            assertPrepared(applicationId, app);
            uploadFromStoredDocs(applicationId, null, actor);
        } catch (BusinessRuleException e) {
            LoanApplication app = loanApplicationRepository.findById(applicationId).orElse(null);
            if (app != null && gstWorkflowRequirement.isMandatoryInActiveWorkflow(app)) {
                throw e;
            }
            log.info("GST analysis soft-upload skipped/failed prepare: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("GST analysis soft-upload exception applicationId={}: {}", applicationId, e.getMessage());
            try {
                persistLocalFailure(applicationId, "UPLOAD", "GST upload error: " + e.getMessage(), actor);
            } catch (Exception ignored) {
                // keep caller path open
            }
        }
    }

    @Transactional
    public KycStepResultResponse retryUpload(UUID applicationId, UUID actingUserId) {
        return uploadFromStoredDocs(applicationId, actingUserId, "ADMIN");
    }

    @Transactional
    public KycStepResultResponse generateReport(UUID applicationId, UUID actingUserId) {
        LoanApplication app = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));

        KycStepResult uploadStep = findLatestSuccessfulUpload(applicationId)
                .orElseThrow(() -> new BusinessRuleException(
                        "GST upload must succeed before generating the report. Retry upload first."));

        String requestId = uploadStep.getTransactionId();
        if (requestId == null || requestId.isBlank()) {
            Object rid = uploadStep.getParsedData() != null ? uploadStep.getParsedData().get("requestId") : null;
            requestId = rid != null ? String.valueOf(rid) : null;
        }
        if (requestId == null || requestId.isBlank()) {
            throw new BusinessRuleException("Upload requestId missing — cannot generate GST report");
        }

        int attemptNumber = findLatest(applicationId).map(r -> r.getAttemptNumber() + 1).orElse(1);
        KarzaGstDocsUploadClient.KarzaGstHttpResult http =
                karzaGstDocsUploadClient.generateReport(requestId, applicationId);

        String gstin = resolveGstin(app);
        Map<String, Object> parsed = new LinkedHashMap<>();
        parsed.put("phase", PHASE_REPORT);
        parsed.put("gstin", gstin);
        parsed.put("uploadRequestId", requestId);
        parsed.put("requestId", http.requestId() != null ? http.requestId() : requestId);
        parsed.put("karzaStatusCode", http.karzaStatusCode());
        parsed.put("simulated", http.simulated());
        parsed.put("actor", "ADMIN");

        KycStepResult step = KycStepResult.builder()
                .applicationId(applicationId)
                .stepType(KycStepType.GST_ANALYSIS)
                .provider(ProviderType.KARZA)
                .attemptNumber(attemptNumber)
                .transactionId(http.requestId() != null ? http.requestId() : requestId)
                .build();

        if (!http.success()) {
            step.setOutcome(StepOutcome.FAILURE);
            step.setErrorMessage(http.errorMessage() != null ? http.errorMessage() : "GST report generation failed");
            parsed.put("error", step.getErrorMessage());
            if (http.root() != null) {
                parsed.put("fullResponse", GstAnalysisMapper.jsonToMap(http.root()));
            }
            step.setParsedData(parsed);
            step.setCompletedAt(Instant.now());
            step = kycStepResultRepository.save(step);
            auditService.logEvent(applicationId, "KYC", "GST_ANALYSIS REPORT FAILURE",
                    actingUserId, null,
                    Map.of("requestId", requestId, "attemptNumber", attemptNumber),
                    "GST analysis report failed");
            return toResponse(step);
        }

        JsonNode result = http.result();
        Map<String, Object> metrics = GstAnalysisMapper.mapMetrics(result);
        parsed.put("mappedMetrics", metrics);
        if (result != null) {
            // Store a compact summary in addition to fullResponse for UI; keep full tree via fullResponse
            parsed.put("resultSummary", Map.of(
                    "gstin", metrics.getOrDefault("gstin", ""),
                    "legalName", metrics.getOrDefault("legalName", ""),
                    "annualGstTurnover", metrics.getOrDefault("annualGstTurnover", ""),
                    "reportType", metrics.getOrDefault("reportType", "")));
        }
        if (http.root() != null) {
            parsed.put("fullResponse", GstAnalysisMapper.jsonToMap(http.root()));
            parsed.put("statusCode", http.karzaStatusCode());
        }

        List<Map<String, Object>> storedDocs = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        String gstToken = metrics.get("gstin") != null ? String.valueOf(metrics.get("gstin")) : gstin;
        for (Map<String, String> link : GstAnalysisMapper.collectDownloadLinks(
                result, gstToken, http.requestId() != null ? http.requestId() : requestId)) {
            try {
                DocumentResponse doc = downloadAndStore(
                        applicationId, link.get("url"), link.get("fileName"), link.get("contentType"));
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("documentId", doc.getId() != null ? doc.getId().toString() : null);
                summary.put("fileName", doc.getFileName());
                summary.put("documentType", doc.getDocumentType());
                storedDocs.add(summary);
            } catch (Exception e) {
                log.warn("GST analysis document download failed for {}: {}", link.get("fileName"), e.getMessage());
                warnings.add(link.get("fileName") + ": " + e.getMessage());
            }
        }
        parsed.put("storedDocuments", storedDocs);
        if (!warnings.isEmpty()) {
            parsed.put("documentDownloadWarnings", warnings);
        }

        step.setOutcome(StepOutcome.SUCCESS);
        step.setConfidenceScore(http.simulated() ? 0.5 : 0.95);
        step.setParsedData(parsed);
        step.setCompletedAt(Instant.now());
        step = kycStepResultRepository.save(step);
        auditService.logEvent(applicationId, "KYC", "GST_ANALYSIS REPORT SUCCESS",
                actingUserId, null,
                Map.of(
                        "requestId", requestId,
                        "documentsStored", storedDocs.size(),
                        "attemptNumber", attemptNumber),
                "GST analysis report succeeded");
        // Phase C2: shadow/flag-gated GST canonicalization — never fails report generation
        gstIngestionService.ingestFromReport(
                app,
                step.getParsedData(),
                http.requestId() != null ? http.requestId() : requestId,
                step.getId());
        return toResponse(step);
    }

    public Map<String, Object> statusBody(UUID applicationId) {
        return statusBody(applicationId, findLatest(applicationId).orElse(null));
    }

    public Map<String, Object> statusBody(UUID applicationId, KycStepResult latest) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("applicationId", applicationId.toString());
        body.put("onWorkflow", isOnWorkflow(applicationId));
        body.put("required", isMandatory(applicationId));
        LoanApplication app = loanApplicationRepository.findById(applicationId).orElse(null);
        body.put("gstin", app != null ? resolveGstin(app) : null);
        body.put("consent", app != null && resolveConsent(app));
        List<Document> analysisDocs = listAnalysisDocuments(applicationId);
        body.put("documentCount", analysisDocs.size());
        List<Map<String, Object>> docSummaries = new ArrayList<>();
        for (Document d : analysisDocs) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", d.getId() != null ? d.getId().toString() : null);
            row.put("fileName", d.getFileName());
            row.put("fileSize", d.getFileSize());
            row.put("contentType", d.getContentType());
            row.put("documentType", d.getDocumentType());
            row.put("kycStepType", d.getKycStepType() != null ? d.getKycStepType().name() : null);
            row.put("createdAt", d.getCreatedAt() != null ? d.getCreatedAt().toString() : null);
            docSummaries.add(row);
        }
        body.put("documents", docSummaries);

        if (latest == null) {
            body.put("status", "NOT_STARTED");
            body.put("phase", null);
            body.put("success", false);
            body.put("uploadSuccess", false);
            body.put("reportSuccess", false);
            return body;
        }
        Map<String, Object> parsed = latest.getParsedData() != null ? latest.getParsedData() : Map.of();
        String phase = parsed.get("phase") != null ? String.valueOf(parsed.get("phase")) : null;
        body.put("status", latest.getOutcome() != null ? latest.getOutcome().name() : "UNKNOWN");
        body.put("phase", phase);
        body.put("success", latest.getOutcome() == StepOutcome.SUCCESS || latest.isOverridden());
        body.put("errorMessage", latest.getErrorMessage());
        body.put("requestId", latest.getTransactionId());
        body.put("completedAt", latest.getCompletedAt() != null ? latest.getCompletedAt().toString() : null);
        body.put("attemptNumber", latest.getAttemptNumber());

        boolean uploadSuccess = findLatestSuccessfulUpload(applicationId).isPresent();
        boolean reportSuccess = findLatestSuccessfulReport(applicationId).isPresent();
        body.put("uploadSuccess", uploadSuccess);
        body.put("reportSuccess", reportSuccess);
        if (parsed.get("fullResponse") != null) {
            body.put("fullResponse", parsed.get("fullResponse"));
        }
        if (parsed.get("uploadResult") != null) {
            body.put("uploadResult", parsed.get("uploadResult"));
        }
        if (parsed.get("mappedMetrics") instanceof Map<?, ?> metrics) {
            body.put("mappedMetrics", metrics);
        }
        return body;
    }

    private Optional<KycStepResult> findLatestSuccessfulUpload(UUID applicationId) {
        List<KycStepResult> all = kycStepResultRepository.findByApplicationIdOrderByCreatedAtAsc(applicationId);
        for (int i = all.size() - 1; i >= 0; i--) {
            KycStepResult r = all.get(i);
            if (r.getStepType() != KycStepType.GST_ANALYSIS) {
                continue;
            }
            if (r.getOutcome() != StepOutcome.SUCCESS && !r.isOverridden()) {
                continue;
            }
            Map<String, Object> p = r.getParsedData();
            if (p == null) {
                continue;
            }
            String phase = p.get("phase") != null ? String.valueOf(p.get("phase")) : "";
            if (PHASE_UPLOAD.equalsIgnoreCase(phase) || PHASE_REPORT.equalsIgnoreCase(phase)) {
                // REPORT implies a prior successful upload; prefer explicit UPLOAD requestId
                if (PHASE_UPLOAD.equalsIgnoreCase(phase)) {
                    return Optional.of(r);
                }
            }
        }
        // Fall back: any SUCCESS with requestId from upload
        for (int i = all.size() - 1; i >= 0; i--) {
            KycStepResult r = all.get(i);
            if (r.getStepType() != KycStepType.GST_ANALYSIS) {
                continue;
            }
            if (r.getOutcome() != StepOutcome.SUCCESS && !r.isOverridden()) {
                continue;
            }
            Map<String, Object> p = r.getParsedData();
            if (p != null && PHASE_UPLOAD.equalsIgnoreCase(String.valueOf(p.get("phase")))) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    private Optional<KycStepResult> findLatestSuccessfulReport(UUID applicationId) {
        List<KycStepResult> all = kycStepResultRepository.findByApplicationIdOrderByCreatedAtAsc(applicationId);
        for (int i = all.size() - 1; i >= 0; i--) {
            KycStepResult r = all.get(i);
            if (r.getStepType() != KycStepType.GST_ANALYSIS) {
                continue;
            }
            if (r.getOutcome() != StepOutcome.SUCCESS && !r.isOverridden()) {
                continue;
            }
            Map<String, Object> p = r.getParsedData();
            if (p != null && PHASE_REPORT.equalsIgnoreCase(String.valueOf(p.get("phase")))) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    private void persistLocalFailure(UUID applicationId, String phase, String message, String actor) {
        int attemptNumber = findLatest(applicationId).map(r -> r.getAttemptNumber() + 1).orElse(1);
        Map<String, Object> parsed = new LinkedHashMap<>();
        parsed.put("phase", phase);
        parsed.put("error", message);
        parsed.put("actor", actor);
        KycStepResult step = KycStepResult.builder()
                .applicationId(applicationId)
                .stepType(KycStepType.GST_ANALYSIS)
                .provider(ProviderType.KARZA)
                .outcome(StepOutcome.FAILURE)
                .attemptNumber(attemptNumber)
                .errorMessage(message)
                .parsedData(parsed)
                .completedAt(Instant.now())
                .build();
        kycStepResultRepository.save(step);
    }

    private List<Document> listAnalysisDocuments(UUID applicationId) {
        List<Document> all = documentRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId);
        List<Document> tagged = new ArrayList<>();
        List<Document> gstReturns = new ArrayList<>();
        for (Document d : all) {
            if (d == null || d.getDocumentType() == null) {
                continue;
            }
            String type = d.getDocumentType().trim().toUpperCase(Locale.ROOT);
            boolean gstReturn = "GST_RETURN".equals(type) || "GST_RETURNS".equals(type);
            if (!gstReturn) {
                continue;
            }
            if (d.getKycStepType() == KycStepType.GST_ANALYSIS) {
                tagged.add(d);
            } else {
                gstReturns.add(d);
            }
        }
        return !tagged.isEmpty() ? tagged : gstReturns;
    }

    private String resolveGstin(LoanApplication app) {
        String fromPrepare = readPreparedGstin(app);
        if (fromPrepare != null) {
            return fromPrepare;
        }
        Optional<KycStepResult> latest = findLatest(app.getId());
        if (latest.isPresent() && latest.get().getParsedData() != null) {
            Object g = latest.get().getParsedData().get("gstin");
            if (g != null && !String.valueOf(g).isBlank()) {
                return String.valueOf(g).trim().toUpperCase(Locale.ROOT);
            }
        }
        return null;
    }

    private boolean resolveConsent(LoanApplication app) {
        Map<String, Object> bi = app.getBusinessInfo();
        if (bi != null && Boolean.TRUE.equals(bi.get("gstAnalysisConsent"))) {
            return true;
        }
        if (bi != null && "true".equalsIgnoreCase(String.valueOf(bi.get("gstAnalysisConsent")))) {
            return true;
        }
        return findLatest(app.getId())
                .map(r -> r.getParsedData() != null && Boolean.TRUE.equals(r.getParsedData().get("consent")))
                .orElse(false);
    }

    private String readPreparedGstin(LoanApplication app) {
        if (app.getBusinessInfo() == null) {
            return null;
        }
        Object g = app.getBusinessInfo().get("gstAnalysisGstin");
        if (g == null || String.valueOf(g).isBlank()) {
            g = app.getBusinessInfo().get("gstin");
        }
        if (g == null || String.valueOf(g).isBlank()) {
            return null;
        }
        return String.valueOf(g).trim().toUpperCase(Locale.ROOT);
    }

    private static String requireGstin(String gstin) {
        if (gstin == null || gstin.isBlank()) {
            throw new BusinessRuleException("GSTIN is required for GST analysis");
        }
        String clean = gstin.trim().toUpperCase(Locale.ROOT);
        if (clean.length() < 15) {
            throw new BusinessRuleException("Enter a valid 15-character GSTIN");
        }
        return clean;
    }

    private LoanApplication loadOwnedApp(UUID applicationId, UUID borrowerUserId) {
        LoanApplication app = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
        if (borrowerUserId != null && app.getCustomerId() != null && !borrowerUserId.equals(app.getCustomerId())) {
            throw new BusinessRuleException("You can only complete GST analysis for your own application");
        }
        return app;
    }

    private DocumentResponse downloadAndStore(
            UUID applicationId, String url, String fileName, String contentType) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(120))
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
        return documentService.storeDocumentBytes(applicationId, "GST_STATEMENT", fileName, ct, bytes);
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
