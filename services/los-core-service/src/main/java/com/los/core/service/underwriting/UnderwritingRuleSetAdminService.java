package com.los.core.service.underwriting;

import com.los.core.audit.AdminConfigAuditSupport;
import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.dto.request.UnderwritingRuleSetRequest;
import com.los.core.model.dto.response.UnderwritingRuleSetResponse;
import com.los.core.model.entity.UnderwritingRuleSet;
import com.los.core.repository.UnderwritingRuleSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UnderwritingRuleSetAdminService {

    private final UnderwritingRuleSetRepository repository;
    private final AdminConfigAuditSupport adminConfigAuditSupport;

    public List<UnderwritingRuleSetResponse> list() {
        return repository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public UnderwritingRuleSetResponse create(UnderwritingRuleSetRequest r) {
        UnderwritingRuleSet e = new UnderwritingRuleSet();
        applyRequest(e, r);
        e.setActive(false);
        e.setUpdatedAt(Instant.now());
        UnderwritingRuleSetResponse saved = toResponse(repository.save(e));
        adminConfigAuditSupport.captureCreate("UNDERWRITING_RULE_SET", saved.getId().toString(), saved, "Underwriting rule set created");
        return saved;
    }

    @Transactional
    public UnderwritingRuleSetResponse update(UUID id, UnderwritingRuleSetRequest r) {
        UnderwritingRuleSet e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Underwriting rule set not found: " + id));
        UnderwritingRuleSetResponse before = toResponse(e);
        applyRequest(e, r);
        e.setUpdatedAt(Instant.now());
        UnderwritingRuleSetResponse after = toResponse(repository.save(e));
        adminConfigAuditSupport.captureUpdate("UNDERWRITING_RULE_SET", id.toString(), before, after, "Underwriting rule set updated");
        return after;
    }

    @Transactional
    public void delete(UUID id) {
        UnderwritingRuleSet e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Underwriting rule set not found: " + id));
        if (e.isActive()) {
            throw new BusinessRuleException(
                    "Cannot delete an active rule set. Deactivate it first.",
                    "UNDERWRITING_RULE_ACTIVE_DELETE_FORBIDDEN",
                    "DEACTIVATE_FIRST",
                    null);
        }
        UnderwritingRuleSetResponse before = toResponse(e);
        repository.delete(e);
        adminConfigAuditSupport.captureDelete("UNDERWRITING_RULE_SET", id.toString(), before, "Underwriting rule set deleted");
    }

    @Transactional
    public void activate(UUID id) {
        UnderwritingRuleSet e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Underwriting rule set not found: " + id));
        UnderwritingRuleSetResponse before = toResponse(e);
        e.setActive(true);
        e.setUpdatedAt(Instant.now());
        repository.save(e);
        adminConfigAuditSupport.captureAction("UNDERWRITING_RULE_SET", id.toString(), "ACTIVATE", before, toResponse(e), "Underwriting rule set activated");
    }

    @Transactional
    public void deactivate(UUID id) {
        UnderwritingRuleSet e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Underwriting rule set not found: " + id));
        UnderwritingRuleSetResponse before = toResponse(e);
        e.setActive(false);
        e.setUpdatedAt(Instant.now());
        repository.save(e);
        adminConfigAuditSupport.captureAction("UNDERWRITING_RULE_SET", id.toString(), "DEACTIVATE", before, toResponse(e), "Underwriting rule set deactivated");
    }

    private void applyRequest(UnderwritingRuleSet e, UnderwritingRuleSetRequest r) {
        e.setName(r.getName());
        e.setBorrowerType(r.getBorrowerType().name());
        e.setLoanProduct(r.getLoanProduct().trim());
        e.setMinAmount(r.getMinAmount());
        e.setMaxAmount(r.getMaxAmount());
        e.setGeography(r.getGeography());
        e.setMinTenureMonths(r.getMinTenureMonths());
        e.setMaxTenureMonths(r.getMaxTenureMonths());
        e.setPriority(r.getPriority() != null ? r.getPriority() : 0);
        Map<String, Object> j = r.getRulesJson() != null ? r.getRulesJson() : Map.of();
        e.setRulesJson(j);
    }

    private UnderwritingRuleSetResponse toResponse(UnderwritingRuleSet e) {
        return UnderwritingRuleSetResponse.builder()
                .id(e.getId())
                .name(e.getName())
                .borrowerType(e.getBorrowerType())
                .loanProduct(e.getLoanProduct())
                .minAmount(e.getMinAmount())
                .maxAmount(e.getMaxAmount())
                .geography(e.getGeography())
                .minTenureMonths(e.getMinTenureMonths())
                .maxTenureMonths(e.getMaxTenureMonths())
                .priority(e.getPriority())
                .active(e.isActive())
                .rulesJson(e.getRulesJson() != null ? e.getRulesJson() : Map.of())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
