package com.los.core.requirement.acquisition;

import com.los.core.model.entity.KycStepResult;
import com.los.core.requirement.AcquisitionDtos;
import com.los.core.requirement.AcquisitionExecutorPort;
import com.los.core.requirement.AcquisitionSourceResolver;
import com.los.core.requirement.SourceAcquisitionState;
import com.los.core.service.aa.AccountAggregatorService;
import com.los.core.service.document.OcrExtractionService;
import com.los.core.service.integration.gstanalysis.GstAnalysisService;
import com.los.core.service.integration.itr.ItrReturnFormsService;
import com.los.core.service.kyc.IKycOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Thin adapters over existing AA / KYC / GST / ITR / OCR / derivation executors.
 * No parallel engines — call existing services only.
 */
public final class ExistingSourceAcquisitionAdapters {

    private ExistingSourceAcquisitionAdapters() {}

    @Slf4j
    @Component
    @RequiredArgsConstructor
    public static class AccountAggregatorAcquisitionAdapter implements AcquisitionExecutorPort {
        private final AccountAggregatorService accountAggregatorService;

        @Override
        public String sourceKey() {
            return AcquisitionSourceResolver.ACCOUNT_AGGREGATOR;
        }

        @Override
        public boolean supports(String sourceKey) {
            return AcquisitionSourceResolver.ACCOUNT_AGGREGATOR.equals(
                    AcquisitionSourceResolver.normalize(sourceKey));
        }

        @Override
        public AcquisitionDtos.ExecutorOutcome execute(ExecutionContext ctx) {
            if (ctx.dryRun()) {
                return AcquisitionDtos.ExecutorOutcome.succeeded("AA_DRY_RUN", Map.of(), Map.of("dryRun", true));
            }
            try {
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("orchestratedBy", "W6");
                summary.put("reused", "AccountAggregatorService");
                // Force-terminal hint for tests / admin overlays (no silent bank-statement switch)
                Object force = ctx.item().getSourceHints() != null
                        ? ctx.item().getSourceHints().get("forceAaTerminal") : null;
                if (Boolean.TRUE.equals(force)) {
                    return AcquisitionDtos.ExecutorOutcome.terminal(
                            "ACCOUNT_AGGREGATOR", "AA terminal unavailable (forced)", summary);
                }
                boolean active = accountAggregatorService.hasActiveConsent(ctx.applicationId());
                summary.put("hasActiveConsent", active);
                if (active) {
                    String param = ctx.item().getCanonicalParameterId();
                    Map<String, Boolean> facts = param != null ? Map.of(param, true) : Map.of();
                    return AcquisitionDtos.ExecutorOutcome.succeeded("ACCOUNT_AGGREGATOR", facts, summary);
                }
                // Do NOT create bank statement upload in parallel. Consent creation needs customerId —
                // record AA path in progress; customer/RM completes consent via existing AA APIs.
                summary.put("nextAction", "AA_CONSENT_VIA_EXISTING_API");
                return new AcquisitionDtos.ExecutorOutcome(
                        SourceAcquisitionState.IN_PROGRESS,
                        "ACCOUNT_AGGREGATOR",
                        null,
                        null,
                        "AA consent not yet active — existing AccountAggregatorService path",
                        summary,
                        Map.of(),
                        true);
            } catch (Exception e) {
                log.warn("AA acquisition failed for {}: {}", ctx.applicationId(), e.toString());
                String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                String lower = msg.toLowerCase();
                if (lower.contains("terminal") || lower.contains("not configured")
                        || lower.contains("disabled") || lower.contains("unavailable")) {
                    return AcquisitionDtos.ExecutorOutcome.terminal("ACCOUNT_AGGREGATOR", msg, Map.of());
                }
                if (lower.contains("timeout") || lower.contains("503")) {
                    return AcquisitionDtos.ExecutorOutcome.retryable("ACCOUNT_AGGREGATOR", msg, Map.of());
                }
                return AcquisitionDtos.ExecutorOutcome.terminal("ACCOUNT_AGGREGATOR", msg, Map.of());
            }
        }
    }

    @Slf4j
    @Component
    @RequiredArgsConstructor
    public static class KycAcquisitionAdapter implements AcquisitionExecutorPort {
        private final IKycOrchestrationService kycOrchestrationService;

        @Override
        public String sourceKey() {
            return AcquisitionSourceResolver.KYC;
        }

        @Override
        public boolean supports(String sourceKey) {
            return AcquisitionSourceResolver.KYC.equals(AcquisitionSourceResolver.normalize(sourceKey));
        }

        @Override
        public AcquisitionDtos.ExecutorOutcome execute(ExecutionContext ctx) {
            if (ctx.dryRun()) {
                return AcquisitionDtos.ExecutorOutcome.succeeded("KYC_DRY_RUN", Map.of(), Map.of("dryRun", true));
            }
            try {
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("orchestratedBy", "W6");
                summary.put("reused", "IKycOrchestrationService");
                summary.put("kycService", kycOrchestrationService.getClass().getSimpleName());
                Map<String, Object> outcome = kycOrchestrationService.computeKycOutcome(ctx.applicationId());
                if (outcome != null) {
                    summary.putAll(outcome);
                }
                String status = outcome != null && outcome.get("status") != null
                        ? String.valueOf(outcome.get("status")).toUpperCase()
                        : (outcome != null && outcome.get("outcome") != null
                        ? String.valueOf(outcome.get("outcome")).toUpperCase() : "");
                String param = ctx.item().getCanonicalParameterId();
                if (status.contains("REVIEW") || status.contains("MANUAL")) {
                    return new AcquisitionDtos.ExecutorOutcome(
                            SourceAcquisitionState.MANUAL_REVIEW_REQUIRED,
                            "KYC", null, "MANUAL_REVIEW", "KYC requires manual review",
                            summary, param != null ? Map.of(param, false) : Map.of(), true);
                }
                boolean ready = status.contains("PASS") || status.contains("SUCCESS") || status.contains("COMPLETE");
                Map<String, Boolean> facts = param != null ? Map.of(param, ready) : Map.of();
                if (ready) {
                    return AcquisitionDtos.ExecutorOutcome.succeeded("KYC", facts, summary);
                }
                return new AcquisitionDtos.ExecutorOutcome(
                        SourceAcquisitionState.IN_PROGRESS, "KYC", null, null, null, summary, facts, true);
            } catch (Exception e) {
                log.warn("KYC acquisition error: {}", e.toString());
                return AcquisitionDtos.ExecutorOutcome.retryable("KYC", e.getMessage(), Map.of());
            }
        }
    }

    @Slf4j
    @Component
    @RequiredArgsConstructor
    public static class GstAcquisitionAdapter implements AcquisitionExecutorPort {
        private final GstAnalysisService gstAnalysisService;

        @Override
        public String sourceKey() {
            return AcquisitionSourceResolver.GST;
        }

        @Override
        public boolean supports(String sourceKey) {
            return AcquisitionSourceResolver.GST.equals(AcquisitionSourceResolver.normalize(sourceKey));
        }

        @Override
        public AcquisitionDtos.ExecutorOutcome execute(ExecutionContext ctx) {
            if (ctx.dryRun()) {
                return AcquisitionDtos.ExecutorOutcome.succeeded("GST_DRY_RUN", Map.of(), Map.of("dryRun", true));
            }
            try {
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("orchestratedBy", "W6");
                summary.put("reused", "GstAnalysisService");
                Optional<KycStepResult> latest = gstAnalysisService.findLatest(ctx.applicationId());
                summary.put("hasLatest", latest.isPresent());
                boolean complete = false;
                try {
                    complete = gstAnalysisService.isReportCompleteForUnderwriting(ctx.applicationId());
                } catch (Exception e) {
                    summary.put("completeCheck", e.getMessage());
                }
                summary.put("reportComplete", complete);
                String param = ctx.item().getCanonicalParameterId();
                if (complete) {
                    Map<String, Boolean> facts = param != null ? Map.of(param, true) : Map.of();
                    return AcquisitionDtos.ExecutorOutcome.succeeded("GST", facts, summary);
                }
                return new AcquisitionDtos.ExecutorOutcome(
                        SourceAcquisitionState.IN_PROGRESS, "GST", null, null,
                        "GST report not yet complete — existing GstAnalysisService path",
                        summary, Map.of(), true);
            } catch (Exception e) {
                return AcquisitionDtos.ExecutorOutcome.retryable("GST", e.getMessage(), Map.of());
            }
        }
    }

    @Slf4j
    @Component
    @RequiredArgsConstructor
    public static class ItrAcquisitionAdapter implements AcquisitionExecutorPort {
        private final ItrReturnFormsService itrReturnFormsService;

        @Override
        public String sourceKey() {
            return AcquisitionSourceResolver.ITR;
        }

        @Override
        public boolean supports(String sourceKey) {
            return AcquisitionSourceResolver.ITR.equals(AcquisitionSourceResolver.normalize(sourceKey));
        }

        @Override
        public AcquisitionDtos.ExecutorOutcome execute(ExecutionContext ctx) {
            if (ctx.dryRun()) {
                return AcquisitionDtos.ExecutorOutcome.succeeded("ITR_DRY_RUN", Map.of(), Map.of("dryRun", true));
            }
            try {
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("orchestratedBy", "W6");
                summary.put("reused", "ItrReturnFormsService");
                boolean ok = itrReturnFormsService.hasSuccessfulPull(ctx.applicationId());
                summary.put("hasSuccessfulPull", ok);
                Optional<KycStepResult> latest = itrReturnFormsService.findLatest(ctx.applicationId());
                summary.put("hasLatest", latest.isPresent());
                String param = ctx.item().getCanonicalParameterId();
                if (ok) {
                    Map<String, Boolean> facts = param != null ? Map.of(param, true) : Map.of();
                    return AcquisitionDtos.ExecutorOutcome.succeeded("ITR", facts, summary);
                }
                return new AcquisitionDtos.ExecutorOutcome(
                        SourceAcquisitionState.IN_PROGRESS, "ITR", null, null,
                        "ITR pull not yet successful — existing ItrReturnFormsService path",
                        summary, Map.of(), true);
            } catch (Exception e) {
                return AcquisitionDtos.ExecutorOutcome.retryable("ITR", e.getMessage(), Map.of());
            }
        }
    }

    @Slf4j
    @Component
    @RequiredArgsConstructor
    public static class DocumentExtractionAcquisitionAdapter implements AcquisitionExecutorPort {
        private final OcrExtractionService ocrExtractionService;

        @Override
        public String sourceKey() {
            return AcquisitionSourceResolver.DOCUMENT_EXTRACTION;
        }

        @Override
        public boolean supports(String sourceKey) {
            String n = AcquisitionSourceResolver.normalize(sourceKey);
            return AcquisitionSourceResolver.DOCUMENT_EXTRACTION.equals(n)
                    || AcquisitionSourceResolver.BANK_STATEMENT_UPLOAD.equals(n);
        }

        @Override
        public AcquisitionDtos.ExecutorOutcome execute(ExecutionContext ctx) {
            if (ctx.dryRun()) {
                return AcquisitionDtos.ExecutorOutcome.succeeded("OCR_DRY_RUN", Map.of(), Map.of("dryRun", true));
            }
            String docRef = ctx.item().getDocumentRef();
            if (docRef == null || docRef.isBlank()) {
                return AcquisitionDtos.ExecutorOutcome.skipped("No documentRef — waiting for customer upload");
            }
            // Partial extraction overlay for multi-parameter documents (tests / OCR result projection)
            Object partial = ctx.item().getSourceHints() != null
                    ? ctx.item().getSourceHints().get("extractedParameters") : null;
            if (partial instanceof Map<?, ?> extractedMap) {
                String param = ctx.item().getCanonicalParameterId();
                boolean present = param != null && extractedMap.containsKey(param)
                        && extractedMap.get(param) != null;
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("orchestratedBy", "W6");
                summary.put("documentRef", docRef);
                summary.put("partialExtraction", true);
                summary.put("parameterPresent", present);
                Map<String, Boolean> facts = param != null ? Map.of(param, present) : Map.of();
                return new AcquisitionDtos.ExecutorOutcome(
                        SourceAcquisitionState.SUCCEEDED,
                        "DOCUMENT_EXTRACTION",
                        docRef,
                        present ? null : "DATA_INSUFFICIENT",
                        present ? null : "Parameter not present in extraction",
                        summary,
                        facts,
                        true);
            }
            try {
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("orchestratedBy", "W6");
                summary.put("reused", "OcrExtractionService");
                summary.put("documentRef", docRef);
                UUID docId;
                try {
                    docId = UUID.fromString(docRef);
                } catch (Exception e) {
                    // Group key — one job per document group; mark processing
                    summary.put("documentGroup", docRef);
                    return new AcquisitionDtos.ExecutorOutcome(
                            SourceAcquisitionState.IN_PROGRESS,
                            "DOCUMENT_EXTRACTION", docRef, null, null, summary, Map.of(), true);
                }
                String docType = ctx.item().getSourceHints() != null
                        && ctx.item().getSourceHints().get("documentType") != null
                        ? String.valueOf(ctx.item().getSourceHints().get("documentType"))
                        : "FINANCIAL_STATEMENT";
                Map<String, Object> extracted = ocrExtractionService.extractFromDocument(docId, docType, docRef);
                summary.put("extraction", extracted != null);
                return new AcquisitionDtos.ExecutorOutcome(
                        SourceAcquisitionState.IN_PROGRESS,
                        "DOCUMENT_EXTRACTION",
                        docRef,
                        null,
                        null,
                        summary,
                        Map.of(),
                        true);
            } catch (Exception e) {
                log.warn("Document extraction failed: {}", e.toString());
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("documentOutcome", "EXTRACTION_FAILED");
                summary.put("error", e.getMessage() != null ? e.getMessage() : e.toString());
                // EXTRACTION_FAILED ≠ REUPLOAD_REQUIRED — coordinator keeps PROVIDED
                return AcquisitionDtos.ExecutorOutcome.terminal("DOCUMENT_EXTRACTION", e.getMessage(), summary);
            }
        }
    }

    @Slf4j
    @Component
    @RequiredArgsConstructor
    public static class DerivationAcquisitionAdapter implements AcquisitionExecutorPort {
        private final org.springframework.beans.factory.ObjectProvider<
                com.los.core.creditintelligence.policystudio.parameters.derived.DerivedCalculationDefinitionService>
                derivedCalculationDefinitionService;

        @Override
        public String sourceKey() {
            return AcquisitionSourceResolver.DERIVATION;
        }

        @Override
        public boolean supports(String sourceKey) {
            return AcquisitionSourceResolver.DERIVATION.equals(AcquisitionSourceResolver.normalize(sourceKey));
        }

        @Override
        @SuppressWarnings("unchecked")
        public AcquisitionDtos.ExecutorOutcome execute(ExecutionContext ctx) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("orchestratedBy", "W6");
            summary.put("mode", "DERIVATION");
            summary.put("calculatorHint", ctx.item().getSourceHints() != null
                    ? ctx.item().getSourceHints().get("calculator") : null);
            if (ctx.dryRun()) {
                return AcquisitionDtos.ExecutorOutcome.succeeded("DERIVATION_DRY_RUN", Map.of(), summary);
            }
            Object derived = ctx.item().getSourceHints() != null
                    ? ctx.item().getSourceHints().get("derivedValue") : null;
            Object readyFlag = ctx.item().getSourceHints() != null
                    ? ctx.item().getSourceHints().get("derivationReady") : null;
            String param = ctx.item().getCanonicalParameterId();
            if (Boolean.TRUE.equals(readyFlag) || derived != null) {
                Map<String, Boolean> facts = param != null ? Map.of(param, true) : Map.of();
                summary.put("derivedValuePresent", derived != null);
                summary.put("calculatorVersion", ctx.item().getSourceHints() != null
                        ? ctx.item().getSourceHints().get("calculatorVersion") : null);
                return AcquisitionDtos.ExecutorOutcome.succeeded("DERIVATION", facts, summary);
            }
            // Safe typed GACAT derived calculation (if defined + tested/production-ready)
            var calcSvc = derivedCalculationDefinitionService.getIfAvailable();
            if (calcSvc != null && param != null && !param.isBlank()) {
                Map<String, Object> inputs = new LinkedHashMap<>();
                if (ctx.item().getSourceHints() != null
                        && ctx.item().getSourceHints().get("inputs") instanceof Map<?, ?> rawInputs) {
                    for (Map.Entry<?, ?> e : rawInputs.entrySet()) {
                        if (e.getKey() != null) {
                            inputs.put(String.valueOf(e.getKey()), e.getValue());
                        }
                    }
                }
                var eval = calcSvc.evaluateCanonical(param, null, inputs);
                summary.put("derivedCalculationEvaluation", eval.toMap());
                if (com.los.core.creditintelligence.policystudio.parameters.derived.SafeDerivedExpressionEvaluator
                        .STATUS_OK.equals(eval.status())) {
                    Map<String, Boolean> facts = Map.of(param, true);
                    summary.put("derivedValue", eval.value());
                    return AcquisitionDtos.ExecutorOutcome.succeeded("DERIVATION", facts, summary);
                }
                if (com.los.core.creditintelligence.policystudio.parameters.derived.SafeDerivedExpressionEvaluator
                        .STATUS_DATA_INSUFFICIENT.equals(eval.status())) {
                    // Fail closed — never invent zero defaults
                    return new AcquisitionDtos.ExecutorOutcome(
                            SourceAcquisitionState.IN_PROGRESS,
                            "DERIVATION", null, null, eval.reason(),
                            summary, Map.of(), true);
                }
            }
            // Do not invent default values when calculator output absent
            return new AcquisitionDtos.ExecutorOutcome(
                    SourceAcquisitionState.IN_PROGRESS,
                    "DERIVATION", null, null, "Awaiting calculator materialization — no default invented",
                    summary, Map.of(), true);
        }
    }
}
