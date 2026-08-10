package com.los.core.creditintelligence.decisionpolicy.corpus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exact export specification for DP-V1 when real/stored evidence is insufficient.
 * DEV/UAT/STAGING only — never production.
 */
public final class DecisionPolicyCorpusExportSpec {

    public static final int MIN_REAL_STORED = 20;
    public static final String CERT_INSUFFICIENT = "INSUFFICIENT_EVIDENCE";
    public static final String CERT_BLOCKED = "BLOCKED_BY_DEFECTS";
    public static final String CERT_REAL_VALIDATED = "REAL_CORPUS_SHADOW_VALIDATED";
    public static final String PACKAGE_INCOMPLETE = "PACKAGE_INCOMPLETE_FOR_REAL_VALIDATION";

    private DecisionPolicyCorpusExportSpec() {}

    public static Map<String, Object> fullSpec() {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("phase", "DP-V1");
        spec.put("schemaVersion", DecisionPolicyCorpusSchemaValidator.SCHEMA_VERSION);
        spec.put("anonymisationVersion", DecisionPolicyCorpusAnonymizer.VERSION);
        spec.put("minRealStoredApplications", MIN_REAL_STORED);
        spec.put("preferredCount", "50+");
        spec.put("environment", "DEV / UAT / STAGING only — never production credentials");
        spec.put("note",
                "20 cases is minimum technical pilot evidence, not statistical proof of portfolio performance.");
        spec.put("originAllowedForCertification", List.of(
                "ANONYMIZED_REAL_DEV_DATA", "STORED_PROVIDER_DATA"));
        spec.put("originNotCounted", List.of(
                "USER_SUPPLIED_SAMPLE", "REPRESENTATIVE_FIXTURE", "SYNTHETIC"));

        Map<String, Object> perApp = new LinkedHashMap<>();
        perApp.put("required", List.of(
                "applicationToken (stable anonymised id)",
                "originClassification",
                "productCode",
                "borrowerType",
                "requestedAmount",
                "requestedTenureMonths (if known)",
                "evaluationBusinessDate",
                "applicationCore (anonymised fields only)"));
        perApp.put("kycEvidence", List.of(
                "KycStepResult-derived steps: stepType, outcome, technicalStatus, providerUnavailable, "
                        + "fallbackUsed, verified flags — NO raw PAN/Aadhaar/name",
                "manual KYC review outcome if any",
                "VKYC / PKYC completion flags",
                "production/current KYC outcome for comparison"));
        perApp.put("creditEvidence", List.of(
                "bureau facts/metrics or explicit absence",
                "banking/AA metrics or absence",
                "GST metrics or absence",
                "ITR/income metrics or absence",
                "collateral inputs if any",
                "reconciliations if any",
                "legacy underwriting outcome / scorecard / CAM / sanction if available"));
        perApp.put("provenance", List.of(
                "workflowConfigId/version/hash if reconstructable",
                "integration routing identity",
                "provider source labels",
                "fallback usage",
                "dataOrigin + anonymisationVersion",
                "WORKFLOW_PROVENANCE_INCOMPLETE flag when unknown"));
        spec.put("perApplication", perApp);

        spec.put("sourceTablesSuggested", List.of(
                "loan_applications",
                "kyc_step_results",
                "underwriting_evaluations (or equivalent)",
                "ci_bureau_report / ci_bank_account / ci_gst_* / ci_itr_* (stored)",
                "ci_source_record / artifacts",
                "workflow_config bindings",
                "sanction / CAM tables where safely exportable (read-only snapshot)"));

        spec.put("anonymize", List.of(
                "customer name", "PAN", "Aadhaar", "phone", "email", "address",
                "bank account number", "bureau account identifiers",
                "director identifiers", "document identifiers"));
        spec.put("preserve", List.of(
                "borrower type", "product", "amounts", "balances", "turnover", "DPD",
                "bureau score", "inquiries", "obligations", "transaction patterns",
                "dates", "vintage", "KYC verification statuses", "provider availability",
                "workflow outcomes", "manual review outcomes", "sanction/decision results"));

        spec.put("doNot", List.of(
                "Fabricate applications to reach 20",
                "Cherry-pick only approvals",
                "Call external providers during export/import/validation",
                "Connect to production",
                "Commit raw PII to Git",
                "Coerce missing values to zero",
                "Insert into live operational tables without explicit validation DB design",
                "Enable allowCanonicalAuthority"));

        Map<String, Object> sampleApp = new LinkedHashMap<>();
        sampleApp.put("applicationToken", "TOK_APP_…");
        sampleApp.put("originClassification", "ANONYMIZED_REAL_DEV_DATA");
        sampleApp.put("productCode", "DIGILEAP");
        sampleApp.put("borrowerType", "INDIVIDUAL");
        sampleApp.put("requestedAmount", 500000);
        sampleApp.put("requestedTenureMonths", 24);
        sampleApp.put("evaluationBusinessDate", "2024-06-15");
        sampleApp.put("applicationCore", Map.of("borrower_type", "INDIVIDUAL"));
        sampleApp.put("kycSteps", List.of());
        sampleApp.put("metrics", Map.of());
        sampleApp.put("legacyResults", Map.of("kycOutcome", "PASS"));
        sampleApp.put("provenance", Map.of("workflowProvenanceIncomplete", true));

        Map<String, Object> importProcess = new LinkedHashMap<>();
        importProcess.put("endpoint",
                "POST /api/v1/internal/credit-intelligence/staging-demo/decision-policy-corpus/import");
        importProcess.put("contentType", "application/json");
        importProcess.put("body", Map.of(
                "applications", List.of(sampleApp),
                "replaceExisting", false));
        importProcess.put("validation", "Schema + PII rejection before persist");
        importProcess.put("storage",
                "ci_dp_v1_corpus_application (validation-only — not loan_applications)");
        spec.put("importProcess", importProcess);

        spec.put("mixedSampleGuidance", List.of(
                "KYC PASS / FAIL / incomplete / manual review",
                "provider failure / fallback",
                "UW approve / decline / manual review",
                "missing bureau / banking",
                "GST mismatch",
                "capacity constrained / counter-offer-like",
                "incomplete data",
                "different products and customer types"));
        return spec;
    }
}
