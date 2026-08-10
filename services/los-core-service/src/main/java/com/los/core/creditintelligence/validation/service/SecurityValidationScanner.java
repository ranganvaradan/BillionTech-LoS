package com.los.core.creditintelligence.validation.service;

import com.los.core.creditintelligence.provider.support.AccountNumberHasher;
import com.los.core.creditintelligence.provider.surepass.SurePassBsaAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.creditintelligence.provider.spi.ExtractionRequest;
import com.los.core.creditintelligence.provider.spi.ProviderExtractionResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Security validation scanner — static + adapter masking assertions. Severity-classified findings.
 */
@Component
public class SecurityValidationScanner {

    public record Finding(String code, String severity, String message, Map<String, Object> detail) {
    }

    public record SecurityReport(List<Finding> findings, int criticalOpen, boolean maskingOk) {
    }

    private final ObjectMapper objectMapper;

    public SecurityValidationScanner(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public SecurityValidationScanner() {
        this(new ObjectMapper());
    }

    public SecurityReport scan() {
        List<Finding> findings = new ArrayList<>();

        // Account masking via hasher
        Map<String, String> hashed = AccountNumberHasher.hashAndLast4("123456789012");
        if (hashed.get("accountNumberHash") == null || hashed.get("accountNumberLast4") == null) {
            findings.add(new Finding("ACCT_MASK_FAIL", "CRITICAL",
                    "AccountNumberHasher failed to produce hash/last4", Map.of()));
        } else if ("123456789012".equals(hashed.get("accountNumberHash"))) {
            findings.add(new Finding("ACCT_RAW_HASH", "CRITICAL",
                    "Account hash equals raw account number", Map.of()));
        }

        boolean maskingOk = assertBsaMasksAccount(findings);

        findings.add(new Finding("PAN_LOGGING", "MEDIUM",
                "Ensure PAN is never logged at INFO in providers; prefer last4/hash in observations",
                Map.of("recommendation", "Audit log statements in tax/bureau adapters")));
        findings.add(new Finding("RAW_TXN_LOGGING", "MEDIUM",
                "Avoid logging full bank transaction narratives/payloads in production",
                Map.of()));
        findings.add(new Finding("BUREAU_PAYLOAD", "HIGH",
                "Validation APIs must not expose raw bureau payloads — replay manifest hashes only",
                Map.of("enforcedIn", "ValidationAdminController")));
        findings.add(new Finding("INTERNAL_TOKEN", "INFO",
                "Internal CI APIs require X-Internal-Token when credit-intelligence.internal-token is set",
                Map.of()));
        findings.add(new Finding("AA_CREDENTIALS", "HIGH",
                "AA/provider tokens must never appear in validation findings or evidence views",
                Map.of()));
        findings.add(new Finding("CROSS_TENANT", "INFO",
                "Covered by TenantIsolationValidator — unknown tenant fails when requireExplicit/devMode=false",
                Map.of()));

        int critical = (int) findings.stream().filter(f -> "CRITICAL".equals(f.severity())).count();
        return new SecurityReport(findings, critical, maskingOk && critical == 0);
    }

    private boolean assertBsaMasksAccount(List<Finding> findings) {
        try (InputStream in = cl().getResourceAsStream("provider-fixtures/surepass/bsa/bsa_minimal.json")) {
            if (in == null) {
                findings.add(new Finding("BSA_FIXTURE_MISSING", "HIGH",
                        "BSA fixture missing for masking check", Map.of()));
                return false;
            }
            JsonNode payload = objectMapper.readTree(in);
            SurePassBsaAdapter adapter = new SurePassBsaAdapter(objectMapper);
            ProviderExtractionResult result = adapter.extract(
                    payload, new ExtractionRequest(UUID.randomUUID(), UUID.randomUUID(), null, Map.of()));
            for (Map<String, Object> fact : result.facts()) {
                Object data = fact.get("data");
                if (data instanceof Map<?, ?> m) {
                    if (m.containsKey("account_number") || m.containsKey("accountNumber")) {
                        findings.add(new Finding("BSA_RAW_ACCOUNT", "CRITICAL",
                                "BSA adapter retained raw account number field", Map.of()));
                        return false;
                    }
                }
            }
            findings.add(new Finding("BSA_MASK_OK", "INFO",
                    "BSA adapter strips raw account number; uses hash/last4", Map.of()));
            return true;
        } catch (Exception e) {
            findings.add(new Finding("BSA_MASK_ERROR", "HIGH",
                    "BSA masking check error: " + e.getMessage(), Map.of()));
            return false;
        }
    }

    private static ClassLoader cl() {
        ClassLoader c = Thread.currentThread().getContextClassLoader();
        return c != null ? c : SecurityValidationScanner.class.getClassLoader();
    }

    public Map<String, Object> toMap(SecurityReport report) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("criticalOpen", report.criticalOpen());
        out.put("maskingOk", report.maskingOk());
        List<Map<String, Object>> list = new ArrayList<>();
        for (Finding f : report.findings()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", f.code());
            m.put("severity", f.severity());
            m.put("message", f.message());
            m.put("detail", f.detail());
            list.add(m);
        }
        out.put("findings", list);
        return out;
    }
}
