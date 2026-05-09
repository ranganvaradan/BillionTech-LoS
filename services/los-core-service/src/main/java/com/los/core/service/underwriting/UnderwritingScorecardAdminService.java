package com.los.core.service.underwriting;

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

    public List<UnderwritingScorecardResponse> list() {
        return repository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public UnderwritingScorecardResponse create(UnderwritingScorecardRequest r) {
        UnderwritingScorecard e = new UnderwritingScorecard();
        apply(e, r);
        e.setActive(r.isActive());
        e.setUpdatedAt(Instant.now());
        return toResponse(repository.save(e));
    }

    @Transactional
    public UnderwritingScorecardResponse update(UUID id, UnderwritingScorecardRequest r) {
        UnderwritingScorecard e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Scorecard not found: " + id));
        apply(e, r);
        e.setActive(r.isActive());
        e.setUpdatedAt(Instant.now());
        return toResponse(repository.save(e));
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
        repository.delete(e);
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
