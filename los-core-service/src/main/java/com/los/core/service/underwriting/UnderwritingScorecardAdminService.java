package com.los.core.service.underwriting;

import com.los.core.audit.AdminConfigAuditSupport;
import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.dto.request.UnderwritingScorecardRequest;
import com.los.core.model.dto.response.UnderwritingScorecardResponse;
import com.los.core.model.entity.UnderwritingScorecard;
import com.los.core.repository.UnderwritingScorecardRepository;
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
public class UnderwritingScorecardAdminService {

    private final UnderwritingScorecardRepository repository;
    private final AdminConfigAuditSupport adminConfigAuditSupport;

    public List<UnderwritingScorecardResponse> list() {
        return repository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public UnderwritingScorecardResponse create(UnderwritingScorecardRequest r) {
        UnderwritingScorecard e = new UnderwritingScorecard();
        apply(e, r);
        e.setActive(r.isActive());
        e.setUpdatedAt(Instant.now());
        UnderwritingScorecardResponse saved = toResponse(repository.save(e));
        adminConfigAuditSupport.captureCreate("UNDERWRITING_SCORECARD", saved.getId().toString(), saved, "Underwriting scorecard created");
        return saved;
    }

    @Transactional
    public UnderwritingScorecardResponse update(UUID id, UnderwritingScorecardRequest r) {
        UnderwritingScorecard e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Scorecard not found: " + id));
        UnderwritingScorecardResponse before = toResponse(e);
        apply(e, r);
        e.setActive(r.isActive());
        e.setUpdatedAt(Instant.now());
        UnderwritingScorecardResponse after = toResponse(repository.save(e));
        adminConfigAuditSupport.captureUpdate("UNDERWRITING_SCORECARD", id.toString(), before, after, "Underwriting scorecard updated");
        return after;
    }

    @Transactional
    public void delete(UUID id) {
        UnderwritingScorecard e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Scorecard not found: " + id));
        if (e.isActive()) {
            throw new BusinessRuleException(
                    "Cannot delete an active scorecard. Deactivate it first.",
                    "SCORECARD_ACTIVE_DELETE_FORBIDDEN",
                    "DEACTIVATE_FIRST",
                    null);
        }
        UnderwritingScorecardResponse before = toResponse(e);
        repository.delete(e);
        adminConfigAuditSupport.captureDelete("UNDERWRITING_SCORECARD", id.toString(), before, "Underwriting scorecard deleted");
    }

    private void apply(UnderwritingScorecard e, UnderwritingScorecardRequest r) {
        e.setName(r.getName().trim());
        e.setBorrowerType(r.getBorrowerType().name());
        e.setLoanProduct(r.getLoanProduct().trim());
        e.setVersion(Math.max(1, r.getVersion()));
        e.setPriority(r.getPriority());
        e.setMinAmount(r.getMinAmount());
        e.setMaxAmount(r.getMaxAmount());
        e.setGeography(r.getGeography());
        e.setScorecardJson(safeMap(r.getScorecardJson()));
        e.setThresholdsJson(safeMap(r.getThresholdsJson()));
        e.setHardRulesJson(safeMap(r.getHardRulesJson()));
    }

    private static Map<String, Object> safeMap(Map<String, Object> m) {
        return m != null ? m : Map.of();
    }

    private UnderwritingScorecardResponse toResponse(UnderwritingScorecard e) {
        return UnderwritingScorecardResponse.builder()
                .id(e.getId())
                .name(e.getName())
                .borrowerType(e.getBorrowerType())
                .loanProduct(e.getLoanProduct())
                .version(e.getVersion())
                .priority(e.getPriority())
                .minAmount(e.getMinAmount())
                .maxAmount(e.getMaxAmount())
                .geography(e.getGeography())
                .scorecardJson(e.getScorecardJson())
                .thresholdsJson(e.getThresholdsJson())
                .hardRulesJson(e.getHardRulesJson())
                .active(e.isActive())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
