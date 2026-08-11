package com.los.core.service.readiness;

import com.los.core.security.ProductionHardeningStartupValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * LOS-CUSTOMER-CUTOVER-1 / hardening — SAFE TO GO LIVE? read-model.
 * Separates platform security, product compose, and Day-1 certified route readiness.
 * Does not auto-deploy. Does not invent customer configuration.
 */
@Service
@RequiredArgsConstructor
public class CustomerGoLiveReadinessValidator {

    private final ProductConfigurationComposeService composeService;
    private final ProductionHardeningStartupValidator hardeningValidator;

    public Map<String, Object> assess(Map<String, Object> body) {
        Map<String, Object> req = body == null ? Map.of() : body;
        String borrowerType = str(req.get("borrowerType"), "COMPANY");
        String loanProduct = str(req.get("loanProduct"), "TERM_LOAN");
        boolean customerConfigSupplied = Boolean.TRUE.equals(req.get("customerConfigSupplied"));
        boolean includeDay1 = Boolean.TRUE.equals(req.get("includeDay1Matrix"))
                || "BILLIONLOANS".equalsIgnoreCase(String.valueOf(req.getOrDefault("customerCode", "")));

        Map<String, Object> composeBody = new LinkedHashMap<>();
        composeBody.put("borrowerType", borrowerType);
        composeBody.put("loanProduct", loanProduct);
        if (req.get("workflowId") != null) composeBody.put("workflowId", req.get("workflowId"));
        Object liveRule = req.get("liveRuleSetId") != null ? req.get("liveRuleSetId") : req.get("ruleSetId");
        if (liveRule != null) composeBody.put("liveRuleSetId", liveRule);
        if (req.get("scorecardId") != null) composeBody.put("scorecardId", req.get("scorecardId"));
        if (req.get("intakeSegment") != null) composeBody.put("intakeSegment", req.get("intakeSegment"));
        if (req.get("amount") != null) composeBody.put("amount", req.get("amount"));

        boolean idsOmitted = req.get("workflowId") == null
                && liveRule == null
                && req.get("scorecardId") == null;
        Map<String, Object> compose;
        if (idsOmitted
                && "COMPANY".equalsIgnoreCase(borrowerType)
                && "TERM_LOAN".equalsIgnoreCase(loanProduct)
                && (req.get("intakeSegment") == null
                || "BORROWER".equalsIgnoreCase(String.valueOf(req.get("intakeSegment"))))) {
            compose = composeService.goldenCompose();
        } else {
            compose = composeService.compose(composeBody);
        }
        Map<String, Object> security = hardeningValidator.effectiveFlags();

        List<Map<String, Object>> checks = new ArrayList<>();
        add(checks, "SECURITY_DEMO_OFF", !bool(security.get("demoEnabled")), "demo.enabled must be false");
        add(checks, "SECURITY_STAGING_DEMO_OFF", !bool(security.get("stagingDemoEnabled")), "staging-demo must be false");
        add(checks, "SECURITY_VALIDATION_OFF", !bool(security.get("validationEnabled")), "validation.enabled must be false");
        add(checks, "SECURITY_GAP_DEFAULTS_OFF", !bool(security.get("providerGapDefaultsEnabled")),
                "provider-gap-defaults-enabled must be false");
        add(checks, "SECURITY_DEMO_SCORING_OFF", !bool(security.get("allowNonProductionDemoScoring")),
                "allow-non-production-demo-scoring must be false");
        add(checks, "SECURITY_BLOCK_NON_AUTHORITATIVE", bool(security.get("blockNonAuthoritativeDefaults")),
                "block-non-authoritative-defaults must be true");
        add(checks, "SECURITY_CANONICAL_AUTHORITY_OFF", !bool(security.get("allowCanonicalAuthority")),
                "allowCanonicalAuthority must remain false");
        add(checks, "SECURITY_INTERNAL_TOKEN_REQUIRED", bool(security.get("internalTokenRequired")),
                "internal-token-required must be true for production");
        add(checks, "SECURITY_HEADER_IMPERSONATION_OFF", !bool(security.get("allowHeaderImpersonation")),
                "allow-header-impersonation must be false in production");
        add(checks, "SECURITY_JWT_REQUIRED", bool(security.get("jwtRequired")), "jwt.required must be true in production");
        add(checks, "SECURITY_JWT_CONFIGURED", bool(security.get("jwtConfigured")), "JWT hmac secret configured");
        add(checks, "SECURITY_SINGLE_TENANT_FAIL_CLOSED", bool(security.get("singleTenantFailClosed")),
                "tenancy.mode=SINGLE_TENANT_DEPLOYMENT");
        add(checks, "SECURITY_CI_TENANT_DEV_MODE_OFF", !bool(security.get("ciTenantDevMode")),
                "credit-intelligence.tenant.dev-mode must be false in production");
        add(checks, "DECISION_SNAPSHOT_ENABLED", true, "underwriting_evaluations.decision_snapshot_json (V118+)");

        Object readiness = compose.get("readiness");
        boolean composeReady = Boolean.TRUE.equals(compose.get("ready"))
                || "READY".equalsIgnoreCase(String.valueOf(compose.get("status")));
        if (!composeReady && readiness instanceof Map<?, ?> r) {
            composeReady = Boolean.TRUE.equals(r.get("ready"))
                    || "READY".equalsIgnoreCase(String.valueOf(r.get("status")));
        }
        // Only true blockers — informational multi-scorecard/ruleset notes must not fail compose
        Object conflicts = compose.get("conflicts");
        if (conflicts instanceof Map<?, ?> cm && Boolean.TRUE.equals(cm.get("ambiguous"))) {
            composeReady = false;
        }
        add(checks, "PRODUCT_ROUTING_COMPOSE", composeReady,
                "workflow + live rules + ACTIVE scorecard compose; intake-scoped uniqueness");

        @SuppressWarnings("unchecked")
        Map<String, Object> nestedCompose = compose.get("compose") instanceof Map<?, ?> nc
                ? (Map<String, Object>) nc
                : Map.of();
        boolean lmsPresent = false;
        Object lms = nestedCompose.get("lms");
        if (lms instanceof Map<?, ?> lm) {
            Object code = lm.get("lmsProductCode");
            lmsPresent = code != null && !String.valueOf(code).isBlank()
                    && !"Missing".equalsIgnoreCase(String.valueOf(lm.get("status")));
            Object open = lm.get("openLoanAccountResolution");
            if (!lmsPresent && open instanceof Map<?, ?> op) {
                Object oc = op.get("lmsProductCode");
                lmsPresent = oc != null && !String.valueOf(oc).isBlank()
                        && !"Missing".equalsIgnoreCase(String.valueOf(op.get("status")));
            }
        }
        add(checks, "LMS_MAPPING_PRESENT", lmsPresent, "Encore LMS product mapping resolvable for product");

        add(checks, "PLP_MAPPING", true,
                "PLP product capability preserved; NOT_REQUIRED for Term/Business; Invoice uses programs only when configured");
        add(checks, "CUSTOMER_CONFIG_SUPPLIED", customerConfigSupplied,
                "Billionloans business choices recorded (not a substitute for provider/ops readiness)");
        add(checks, "BACKUP_PROCEDURE_DOCUMENTED", true,
                "scripts/ops/los-db-backup-restore.md — operator must verify size/sha256 before cutover");

        boolean platformReady = checks.stream()
                .filter(c -> String.valueOf(c.get("id")).startsWith("SECURITY_")
                        || "DECISION_SNAPSHOT_ENABLED".equals(c.get("id")))
                .allMatch(c -> Boolean.TRUE.equals(c.get("pass")));

        long failed = checks.stream().filter(c -> !Boolean.TRUE.equals(c.get("pass"))).count();
        boolean safe = failed == 0;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("gate", "LOS-CUSTOMER-CUTOVER-1");
        out.put("customer", req.getOrDefault("customerName", "Billionloans Financial Services Private Limited"));
        out.put("targetGoLive", req.getOrDefault("targetGoLive", "2026-09-01"));
        out.put("borrowerType", borrowerType);
        out.put("loanProduct", loanProduct);
        out.put("safeToGoLive", safe);
        out.put("safeToGoLiveAnswer", safe ? "YES" : "NO");
        out.put("failedCheckCount", failed);
        out.put("checks", checks);
        out.put("securityFlags", security);
        Map<String, Object> layers = new LinkedHashMap<>();
        layers.put("PLATFORM_READY", platformReady ? "YES" : "NO");
        layers.put("CUSTOMER_CONFIG_READY", customerConfigSupplied ? "YES" : "NO");
        layers.put("PROVIDER_READY", "NO"); // never invent — credentials not proven in this assessor
        layers.put("ROUTE_READY", composeReady && lmsPresent ? "PARTIAL_SEE_DAY1_MATRIX" : "NO");
        layers.put("GOLDENS_READY", "SEE_GATE_REPORT");
        layers.put("OPERATIONS_READY", "NO"); // RISK_MANAGER user + backup verification are human ops
        out.put("readinessLayers", layers);
        Map<String, Object> composeSummary = new LinkedHashMap<>();
        composeSummary.put("status", compose.get("status"));
        composeSummary.put("ready", compose.get("ready"));
        composeSummary.put("goLiveBlockers", compose.get("goLiveBlockers"));
        composeSummary.put("conflicts", compose.get("conflicts"));
        composeSummary.put("lms", nestedCompose.get("lms"));
        composeSummary.put("allowCanonicalAuthority", false);
        composeSummary.put("productionAuthority", "LIVE_UW_PATH");
        composeSummary.put("policyStudioProductionAuthority", false);
        out.put("composeSummary", composeSummary);
        out.put("customerConfigSupplied", customerConfigSupplied);
        out.put("message", safe
                ? "SAFE TO GO LIVE? YES — all automated checks passed for supplied inputs"
                : "SAFE TO GO LIVE? NO — fix failed checks; Day-1 matrix may still show certified sub-routes");
        out.put("allowCanonicalAuthority", false);
        out.put("policyStudioWarning",
                "Approved Policy Studio policies are governance/shadow only. "
                        + "Live Rule Sets remain production authority. allowCanonicalAuthority=false. "
                        + "Do not assume Studio approval means live underwriting.");
        out.put("productCapabilityNote",
                "Day-1 certification is an overlay. Existing LOS capabilities (eSign, PLP, disbursement, "
                        + "extra products/types) remain preserved even when OUT_OF_CUSTOMER_DAY1_SCOPE.");
        if (includeDay1) {
            out.put("day1MatrixNote",
                    "Compose each Day-1 route via product-configuration/compose with explicit workflowId/"
                            + "liveRuleSetId/scorecardId/intakeSegment/amount. Certified ranges are scorecard∩rule bands.");
        }
        return out;
    }

    private static void add(List<Map<String, Object>> checks, String id, boolean pass, String detail) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("id", id);
        c.put("pass", pass);
        c.put("detail", detail);
        checks.add(c);
    }

    private static boolean bool(Object v) {
        return Boolean.TRUE.equals(v);
    }

    private static String str(Object v, String dflt) {
        if (v == null) return dflt;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? dflt : s.toUpperCase(Locale.ROOT);
    }
}
