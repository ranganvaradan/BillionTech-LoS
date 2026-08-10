package com.los.core.creditintelligence.cutover.service;

import com.los.core.creditintelligence.cutover.domain.BindingCertificationStatus;
import com.los.core.creditintelligence.cutover.store.CutoverStore;
import com.los.core.creditintelligence.validation.domain.CiPolicyBinding;
import com.los.core.creditintelligence.validation.service.PolicyBindingCatalog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Certifies policy bindings (unit/period/path/missing policy/tests). Extends CiPolicyBinding fields.
 */
@Service
public class BindingCertificationService {

    private final CutoverStore store;
    private final PolicyBindingCatalog bindingCatalog;

    public BindingCertificationService(CutoverStore store) {
        this(store, new PolicyBindingCatalog());
    }

    @Autowired
    public BindingCertificationService(CutoverStore store, PolicyBindingCatalog bindingCatalog) {
        this.store = store;
        this.bindingCatalog = bindingCatalog != null ? bindingCatalog : new PolicyBindingCatalog();
    }

    public List<CiPolicyBinding> ensureBindings(UUID tenantId) {
        List<CiPolicyBinding> existing = store.listBindings(tenantId);
        if (!existing.isEmpty()) {
            return existing;
        }
        List<CiPolicyBinding> seeded = bindingCatalog.seedDefaults(tenantId);
        for (CiPolicyBinding b : seeded) {
            if (b.getCertificationStatus() == null) {
                b.setCertificationStatus(BindingCertificationStatus.UNMAPPED.name());
            }
            if (b.getEvidenceRefs() == null) {
                b.setEvidenceRefs(List.of());
            }
            store.saveBinding(b);
        }
        return store.listBindings(tenantId);
    }

    public CiPolicyBinding certify(
            UUID tenantId,
            String legacyParameter,
            String certifiedBy,
            Map<String, Object> checks) {
        ensureBindings(tenantId);
        CiPolicyBinding binding = store.findBinding(tenantId, legacyParameter)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "binding not found"));

        Map<String, Object> c = checks == null ? Map.of() : checks;
        List<String> blockers = new ArrayList<>();
        if (isBlank(binding.getCanonicalPath()) && !Boolean.TRUE.equals(c.get("pathOk"))) {
            blockers.add("missing canonical path");
        }
        if (isBlank(binding.getMissingDataPolicy()) && !Boolean.TRUE.equals(c.get("missingPolicyOk"))) {
            blockers.add("missing data policy undefined");
        }
        if (Boolean.FALSE.equals(c.get("unitsMatch"))) {
            blockers.add("unit mismatch");
        }
        if (Boolean.FALSE.equals(c.get("periodSemanticsMatch"))) {
            blockers.add("period semantics mismatch");
        }
        if (Boolean.FALSE.equals(c.get("testsPass"))) {
            blockers.add("tests failed");
        }

        List<String> evidence = new ArrayList<>(
                binding.getEvidenceRefs() == null ? List.of() : binding.getEvidenceRefs());
        Object refs = c.get("evidenceRefs");
        if (refs instanceof List<?> list) {
            list.forEach(r -> evidence.add(String.valueOf(r)));
        }

        if (!blockers.isEmpty()) {
            binding.setCertificationStatus(BindingCertificationStatus.BLOCKED.name());
            binding.setReady(false);
            Map<String, Object> meta = new LinkedHashMap<>(
                    binding.getMetadata() == null ? Map.of() : binding.getMetadata());
            meta.put("certificationBlockers", blockers);
            binding.setMetadata(meta);
            binding.setEvidenceRefs(evidence);
            return store.saveBinding(binding);
        }

        boolean pathOk = !isBlank(binding.getCanonicalPath()) || Boolean.TRUE.equals(c.get("pathOk"));
        boolean missingOk = !isBlank(binding.getMissingDataPolicy())
                || Boolean.TRUE.equals(c.get("missingPolicyOk"));
        boolean unitsOk = !Boolean.FALSE.equals(c.get("unitsMatch"));
        boolean periodOk = !Boolean.FALSE.equals(c.get("periodSemanticsMatch"));
        boolean testsOk = Boolean.TRUE.equals(c.get("testsPass"));

        if (pathOk && missingOk) {
            binding.setCertificationStatus(BindingCertificationStatus.MAPPED.name());
        }
        if (pathOk && missingOk && unitsOk && periodOk && testsOk) {
            binding.setCertificationStatus(BindingCertificationStatus.TESTED.name());
        }
        if (pathOk && missingOk && unitsOk && periodOk && testsOk
                && Boolean.TRUE.equals(c.get("replayPass"))
                && Boolean.TRUE.equals(c.get("shadowReviewed"))) {
            binding.setCertificationStatus(BindingCertificationStatus.CERTIFIED.name());
            binding.setCertifiedBy(certifiedBy);
            binding.setCertifiedAt(Instant.now());
            binding.setReady(true);
        }
        binding.setEvidenceRefs(evidence);
        return store.saveBinding(binding);
    }

    public Map<String, Object> coverage(UUID tenantId) {
        List<CiPolicyBinding> bindings = ensureBindings(tenantId);
        long certified = bindings.stream()
                .filter(b -> BindingCertificationStatus.CERTIFIED.name().equals(b.getCertificationStatus()))
                .count();
        long critical = bindings.stream().filter(CiPolicyBinding::isCritical).count();
        long criticalCertified = bindings.stream()
                .filter(CiPolicyBinding::isCritical)
                .filter(b -> BindingCertificationStatus.CERTIFIED.name().equals(b.getCertificationStatus()))
                .count();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("bindingCount", bindings.size());
        out.put("certifiedCount", certified);
        out.put("criticalCount", critical);
        out.put("criticalCertifiedCount", criticalCertified);
        out.put("allCriticalCertified", critical > 0 && critical == criticalCertified);
        return out;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
