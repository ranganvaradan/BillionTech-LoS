package com.los.core.creditintelligence.cutover.service;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.cutover.domain.CiCutoverCohort;
import com.los.core.creditintelligence.cutover.domain.CiLegacyDefaultDefinition;
import com.los.core.creditintelligence.cutover.domain.CohortStatus;
import com.los.core.creditintelligence.cutover.domain.DefaultClassification;
import com.los.core.creditintelligence.cutover.domain.DefaultDefinitionStatus;
import com.los.core.creditintelligence.cutover.domain.QuarantineResult;
import com.los.core.creditintelligence.cutover.store.CutoverStore;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Opt-in quarantine for enabled cutover cohorts. Never mutates CreditControlService permanently.
 * Outside cohort / flags off → allowLegacy=true (unchanged production behavior).
 */
@Service
public class LegacyDefaultQuarantineService {

    private final CutoverStore store;
    private final LegacyDefaultCatalogService catalog;
    private final CreditIntelligenceProperties properties;
    private final CutoverObservability observability;

    public LegacyDefaultQuarantineService(
            CutoverStore store,
            LegacyDefaultCatalogService catalog,
            CreditIntelligenceProperties properties,
            CutoverObservability observability) {
        this.store = store;
        this.catalog = catalog;
        this.properties = properties;
        this.observability = observability;
    }

    public QuarantineResult intercept(
            UUID tenantId,
            String productCode,
            String legacyKey,
            Map<String, Object> canonicalMetrics) {
        catalog.seedFromInventory();
        observability.inc("default_invocation_count");

        if (!isQuarantineActive(tenantId, productCode)) {
            return QuarantineResult.allowLegacy(legacyKey);
        }

        Optional<CiLegacyDefaultDefinition> def = store.findDefault(legacyKey, null);
        if (def.isEmpty()) {
            def = catalog.listAll().stream()
                    .filter(d -> legacyKey.equals(d.getLegacyKey()))
                    .findFirst();
        }

        if (def.isPresent()) {
            CiLegacyDefaultDefinition d = def.get();
            if (DefaultClassification.BUSINESS_POLICY_DEFAULT.name().equals(d.getClassification())
                    || DefaultDefinitionStatus.APPROVED_EXCEPTION.name().equals(d.getStatus())) {
                observability.inc("approved_policy_default");
                return QuarantineResult.approvedPolicyDefault(legacyKey, d.getDefaultValue());
            }
            if (DefaultClassification.DEMO_ONLY.name().equals(d.getClassification())) {
                observability.inc("demo_default_blocked");
                return QuarantineResult.insufficient(legacyKey, "REFER");
            }
        }

        Object canonical = resolveCanonical(legacyKey, canonicalMetrics, def.orElse(null));
        if (canonical != null) {
            observability.inc("quarantine_canonical_hit");
            markQuarantined(legacyKey);
            return QuarantineResult.canonical(legacyKey, canonical);
        }

        String behavior = def.map(CiLegacyDefaultDefinition::getMissingDataBehavior)
                .orElse("DATA_INSUFFICIENT");
        observability.inc("quarantine_data_insufficient");
        markQuarantined(legacyKey);
        return QuarantineResult.insufficient(legacyKey, behavior);
    }

    public boolean isQuarantineActive(UUID tenantId, String productCode) {
        CreditIntelligenceProperties.Cutover cutover = properties.getCutover();
        if (cutover == null || !cutover.isEnabled() || !cutover.isQuarantineEnabled()) {
            return false;
        }
        if (tenantId != null && cutover.getTenantIds() != null && !cutover.getTenantIds().isEmpty()) {
            if (!cutover.getTenantIds().contains(tenantId.toString())) {
                return false;
            }
        }
        if (productCode != null && cutover.getProductCodes() != null && !cutover.getProductCodes().isEmpty()) {
            if (!cutover.getProductCodes().contains(productCode)) {
                return false;
            }
        }
        return findEnabledCohort(tenantId, productCode).isPresent();
    }

    public Optional<CiCutoverCohort> findEnabledCohort(UUID tenantId, String productCode) {
        return store.listCohorts().stream()
                .filter(c -> tenantId == null || tenantId.equals(c.getTenantId()))
                .filter(c -> productCode == null || productCode.equalsIgnoreCase(c.getProductCode())
                        || productAliasesMatch(c, productCode))
                .filter(c -> {
                    String s = c.getStatus();
                    return CohortStatus.VALIDATION.name().equals(s)
                            || CohortStatus.DUAL_RUN.name().equals(s)
                            || CohortStatus.READY.name().equals(s);
                })
                .findFirst();
    }

    private boolean productAliasesMatch(CiCutoverCohort c, String productCode) {
        if (c.getMetadata() == null) return false;
        Object aliases = c.getMetadata().get("productAliases");
        if (aliases instanceof List<?> list) {
            return list.stream().anyMatch(a -> productCode.equalsIgnoreCase(String.valueOf(a)));
        }
        return false;
    }

    private Object resolveCanonical(
            String legacyKey, Map<String, Object> metrics, CiLegacyDefaultDefinition def) {
        if (metrics == null || metrics.isEmpty()) {
            return null;
        }
        Object direct = metrics.get(legacyKey);
        if (direct != null) return coerce(direct);

        String path = def != null ? def.getCanonicalReplacement() : null;
        if (path != null) {
            String key = path.contains(":") ? path.substring(path.indexOf(':') + 1) : path;
            Object byPath = metrics.get(key);
            if (byPath != null) return coerce(byPath);
            // also try dotted metrics as-is
            byPath = metrics.get(path);
            if (byPath != null) return coerce(byPath);
        }
        return switch (legacyKey) {
            case "AVERAGE_BANK_BALANCE", "avgDailyBalance3m" -> coerce(metrics.get("bank.abb.average"));
            case "ANNUAL_BANKING_TURNOVER" -> coerce(metrics.get("bank.turnover.trailing_12m"));
            case "ANNUAL_GST_TURNOVER" -> coerce(metrics.get("gst.turnover.trailing_12m"));
            case "EMI_OBLIGATION" -> first(
                    coerce(metrics.get("bureau.emi.monthly")),
                    coerce(metrics.get("bank.emi.monthly")));
            case "ITR_INCOME" -> coerce(metrics.get("itr.income.total"));
            case "LIVE_UNSECURED_LOAN_COUNT" -> coerce(metrics.get("bureau.live_unsecured_count"));
            case "MONTHLY_INCOME" -> coerce(metrics.get("income.monthly"));
            case "OBLIGATION_RATIO" -> coerce(metrics.get("obligation.ratio"));
            case "DTI_RATIO" -> coerce(metrics.get("dti.ratio"));
            default -> null;
        };
    }

    private void markQuarantined(String legacyKey) {
        store.findDefault(legacyKey, null).ifPresent(d -> {
            if (DefaultClassification.UNSAFE_SILENT_DEFAULT.name().equals(d.getClassification())
                    || DefaultClassification.DATA_GAP_FALLBACK.name().equals(d.getClassification())
                    || DefaultClassification.DEMO_ONLY.name().equals(d.getClassification())) {
                d.setStatus(DefaultDefinitionStatus.QUARANTINED.name());
                store.saveDefault(d);
            }
        });
    }

    private static Object first(Object a, Object b) {
        return a != null ? a : b;
    }

    private static Object coerce(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal || v instanceof Number || v instanceof String) return v;
        return v;
    }
}
