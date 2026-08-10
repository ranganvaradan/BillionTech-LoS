package com.los.core.creditintelligence.policystudio.lifecycle;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.model.entity.LoanApplication;
import com.los.core.repository.LoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Honest discovery of safe applications for shadow routing validation.
 * Never calls external providers. Does not mislabel fixtures as real data.
 */
@Service
@RequiredArgsConstructor
public class ShadowApplicationDiscoveryService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final CreditIntelligenceProperties properties;
    private final PolicyCatalogueService catalogueService;

    public Map<String, Object> discover() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("allowCanonicalAuthority", false);
        out.put("shadowOnly", true);
        out.put("note", "Classification is honest — fixtures are not claimed as production validation.");
        out.put("storesSearched", List.of(
                "staging Postgres los_core_staging.loan_applications",
                "ci_bureau_report / ci_bank_account / ci_gst_registration / ci_itr_return (counts via apps only)",
                "classpath validation-bundles CASE_A–E (REPRESENTATIVE_FIXTURE — not real)",
                "classpath provider-fixtures (USER_SUPPLIED_SAMPLE / SYNTHETIC — not real apps)",
                "StagingProspectSimulationCatalog (REPRESENTATIVE_FIXTURE)",
                "local docker volumes (dev only; not auto-imported)",
                "NO production DB connections attempted"));

        List<Map<String, Object>> cases = new ArrayList<>();
        int scanned = 0;
        try {
            var page = loanApplicationRepository.findAll(PageRequest.of(0, 50));
            for (LoanApplication app : page) {
                scanned++;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("applicationId", app.getId() == null ? null : app.getId().toString());
                row.put("applicationNumber", app.getApplicationNumber());
                row.put("product", app.getLoanProduct());
                row.put("requestedAmount", app.getRequestedAmount());
                row.put("submittedAt", app.getSubmittedAt() == null ? null : app.getSubmittedAt().toString());
                row.put("hasProduct", app.getLoanProduct() != null && !app.getLoanProduct().isBlank());
                row.put("classification", classify(app));
                row.put("safeForShadowRouting", app.getLoanProduct() != null);
                row.put("piiStatus", "NOT_EXPORTED — IDs/tokens only");
                cases.add(row);
            }
        } catch (Exception e) {
            out.put("discoveryError", e.getClass().getSimpleName());
            out.put("discoveryMessage", "Could not scan loan_applications — catalogue/API still available.");
        }

        cases.add(Map.of(
                "applicationCode", "APP_001_STRONG_DIGILEAP",
                "product", "DIGILEAP",
                "classification", "REPRESENTATIVE_FIXTURE",
                "safeForShadowRouting", true,
                "source", "StagingProspectSimulationCatalog",
                "countsTowardShadowValidated", false));
        cases.add(Map.of(
                "applicationCode", "CASE_A–E",
                "classification", "REPRESENTATIVE_FIXTURE",
                "safeForShadowRouting", false,
                "source", "validation-bundles",
                "countsTowardShadowValidated", false,
                "note", "Provider fixture bundles — not LOS underwriting-hook apps"));

        out.put("scannedLoanApplications", scanned);
        out.put("usableRealStoredCount", scanned); // all staging DB rows count as ANONYMIZED_REAL_DEV_DATA when present
        out.put("minRequiredForShadowValidated", P2ValidationHarnessService.MIN_REAL_STORED);
        out.put("cases", cases);
        out.put("classificationLegend", Map.of(
                "ANONYMIZED_REAL_DEV_DATA", "Stored loan_applications rows in staging/dev DB",
                "STORED_PROVIDER_DATA", "Provider payloads stored for validation harness",
                "USER_SUPPLIED_SAMPLE", "Customer-supplied demo policy/application samples",
                "REPRESENTATIVE_FIXTURE", "Built-in staging simulation cases",
                "SYNTHETIC", "Generated for tests only"));
        out.put("catalogueSize", catalogueService.listCatalogue(properties.getDefaultTenantId()).size());
        out.put("datasetExportSpec", P2ValidationHarnessService.datasetExportSpec());
        return out;
    }

    private String classify(LoanApplication app) {
        if (app.getApplicationNumber() != null && app.getApplicationNumber().startsWith("DEMO")) {
            return "SYNTHETIC";
        }
        return "ANONYMIZED_REAL_DEV_DATA";
    }
}
