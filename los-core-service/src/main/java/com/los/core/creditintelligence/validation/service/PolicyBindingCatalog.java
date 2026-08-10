package com.los.core.creditintelligence.validation.service;

import com.los.core.creditintelligence.validation.domain.CiPolicyBinding;
import com.los.core.creditintelligence.validation.repository.CiPolicyBindingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Seeds and exposes policy bindings from legacy scorecard keys to canonical FACT/METRIC/RECON paths.
 */
@Service
public class PolicyBindingCatalog {

    private final CiPolicyBindingRepository repository;
    private final Map<UUID, List<CiPolicyBinding>> inMemory = new ConcurrentHashMap<>();

    public PolicyBindingCatalog() {
        this.repository = null;
    }

    @Autowired
    public PolicyBindingCatalog(
            @Autowired(required = false) CiPolicyBindingRepository repository) {
        this.repository = repository;
    }

    public List<CiPolicyBinding> seedDefaults(UUID tenantId) {
        List<CiPolicyBinding> existing = list(tenantId);
        if (!existing.isEmpty()) {
            return existing;
        }
        List<CiPolicyBinding> seeded = new ArrayList<>();
        seeded.add(binding(tenantId, "LIVE_UNSECURED_LOAN_COUNT", "METRIC",
                "bureau.live_unsecured_count", "DATA_INSUFFICIENT", "GAP_DEFAULT", "2", true));
        seeded.add(binding(tenantId, "ANNUAL_GST_TURNOVER", "METRIC",
                "gst.turnover.trailing_12m", "DATA_INSUFFICIENT", "SCF_GAP_DEFAULT", "52000000", true));
        seeded.add(binding(tenantId, "AVERAGE_BANK_BALANCE", "METRIC",
                "bank.abb.average", "DATA_INSUFFICIENT", "GAP_DEFAULT", "120000", true));
        seeded.add(binding(tenantId, "ANNUAL_BANKING_TURNOVER", "METRIC",
                "bank.turnover.trailing_12m", "DATA_INSUFFICIENT", "SCF_GAP_DEFAULT", "41000000", true));
        seeded.add(binding(tenantId, "EMI_OBLIGATION", "RECONCILIATION",
                "XSRC_BUREAU_BANK_OBLIGATION", "REFER", "GAP_DEFAULT", "15000", true));
        seeded.add(binding(tenantId, "ITR_INCOME", "METRIC",
                "itr.income.total", "DATA_INSUFFICIENT", "SCF_GAP_DEFAULT", "450000", true));
        seeded.add(binding(tenantId, "PAT", "METRIC",
                "itr.pat", "DATA_INSUFFICIENT", "SCF_GAP_DEFAULT", "500000", false));
        seeded.add(binding(tenantId, "TOL", "METRIC",
                "financials.tol", "DATA_INSUFFICIENT", "GAP_DEFAULT", "3500000", false));
        seeded.add(binding(tenantId, "TNW", "METRIC",
                "financials.tnw", "DATA_INSUFFICIENT", "GAP_DEFAULT", "5000000", false));
        seeded.add(binding(tenantId, "OBLIGATION_RATIO", "METRIC",
                "obligation.ratio", "DATA_INSUFFICIENT", "GAP_DEFAULT_FOIR", "25", true));
        seeded.add(binding(tenantId, "DTI_RATIO", "METRIC",
                "dti.ratio", "DATA_INSUFFICIENT", "GAP_DEFAULT", "18", true));
        seeded.add(binding(tenantId, "MONTHLY_INCOME", "METRIC",
                "income.monthly", "DATA_INSUFFICIENT", "GAP_DEFAULT", "85000", true));
        inMemory.put(tenantId, seeded);
        if (repository != null) {
            try {
                if (repository.countByTenantId(tenantId) == 0) {
                    repository.saveAll(seeded);
                }
            } catch (Exception ignored) {
                // persistence optional in unit tests
            }
        }
        return seeded;
    }

    public List<CiPolicyBinding> list(UUID tenantId) {
        if (repository != null) {
            try {
                List<CiPolicyBinding> db = repository.findByTenantId(tenantId);
                if (!db.isEmpty()) {
                    return db;
                }
            } catch (Exception ignored) {
            }
        }
        return inMemory.getOrDefault(tenantId, List.of());
    }

    public Map<String, Object> coverageSummary(UUID tenantId) {
        List<CiPolicyBinding> bindings = seedDefaults(tenantId);
        long ready = bindings.stream().filter(CiPolicyBinding::isReady).count();
        long critical = bindings.stream().filter(CiPolicyBinding::isCritical).count();
        long criticalReady = bindings.stream().filter(b -> b.isCritical() && b.isReady()).count();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("bindingCount", bindings.size());
        out.put("readyCount", ready);
        out.put("criticalCount", critical);
        out.put("criticalReadyCount", criticalReady);
        out.put("bindingCoveragePct", pct((int) ready, bindings.size()));
        out.put("criticalBindingReadyPct", pct((int) criticalReady, (int) Math.max(critical, 1)));
        out.put("note", "Bindings seeded; ready=false until silent defaults removed and canonical path proven");
        return out;
    }

    private static CiPolicyBinding binding(
            UUID tenantId, String legacy, String type, String path,
            String missing, String defaultOrigin, String defaultValue,
            boolean critical) {
        return CiPolicyBinding.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .legacyParameter(legacy)
                .canonicalType(type)
                .canonicalPath(path)
                .allowedClassifications(List.of("VERIFIED", "DERIVED", "RECONCILED", "DATA_INSUFFICIENT"))
                .missingDataPolicy(missing)
                .currentDefaultOrigin(defaultOrigin)
                .currentDefaultValue(defaultValue)
                .productionRules(List.of(legacy + "_RULE"))
                .ready(false)
                .critical(critical)
                .metadata(Map.of())
                .build();
    }

    private static BigDecimal pct(int num, int den) {
        if (den == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(num * 100.0 / den).setScale(2, RoundingMode.HALF_UP);
    }
}
