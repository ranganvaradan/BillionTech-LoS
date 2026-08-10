package com.los.core.service.underwriting;

import com.los.core.audit.AdminConfigAuditSupport;
import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.dto.request.AnchorRatingTemplateRequest;
import com.los.core.model.dto.response.AnchorRatingTemplateResponse;
import com.los.core.model.entity.AnchorRatingTemplate;
import com.los.core.repository.AnchorRatingTemplateRepository;
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
public class AnchorRatingTemplateAdminService {

    private final AnchorRatingTemplateRepository repository;
    private final AdminConfigAuditSupport adminConfigAuditSupport;

    public List<AnchorRatingTemplateResponse> list() {
        return repository.findAllByOrderByUpdatedAtDesc().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public AnchorRatingTemplateResponse create(AnchorRatingTemplateRequest request) {
        AnchorRatingTemplate entity = new AnchorRatingTemplate();
        apply(entity, request);
        entity.setActive(false);
        entity.setUpdatedAt(Instant.now());
        AnchorRatingTemplateResponse saved = toResponse(repository.save(entity));
        adminConfigAuditSupport.captureCreate("ANCHOR_RATING_TEMPLATE", saved.getId().toString(), saved, "Anchor rating template created");
        return saved;
    }

    @Transactional
    public AnchorRatingTemplateResponse update(UUID id, AnchorRatingTemplateRequest request) {
        AnchorRatingTemplate entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Anchor rating template not found: " + id));
        AnchorRatingTemplateResponse before = toResponse(entity);
        apply(entity, request);
        entity.setUpdatedAt(Instant.now());
        AnchorRatingTemplateResponse after = toResponse(repository.save(entity));
        adminConfigAuditSupport.captureUpdate("ANCHOR_RATING_TEMPLATE", id.toString(), before, after, "Anchor rating template updated");
        return after;
    }

    @Transactional
    public AnchorRatingTemplateResponse activate(UUID id) {
        repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Anchor rating template not found: " + id));
        List<AnchorRatingTemplate> all = repository.findAll();
        Instant now = Instant.now();
        for (AnchorRatingTemplate t : all) {
            if (t.getId().equals(id)) {
                adminConfigAuditSupport.captureAction(
                        "ANCHOR_RATING_TEMPLATE",
                        id.toString(),
                        "ACTIVATE",
                        toResponse(t),
                        null,
                        "Anchor rating template activated");
            }
            t.setActive(t.getId().equals(id));
            t.setUpdatedAt(now);
        }
        repository.saveAll(all);
        return toResponse(repository.findById(id).orElseThrow());
    }

    @Transactional
    public void delete(UUID id) {
        AnchorRatingTemplate entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Anchor rating template not found: " + id));
        if (entity.isActive()) {
            throw new BusinessRuleException(
                    "Cannot delete an active anchor rating template. Activate another template first.",
                    "ANCHOR_RATING_TEMPLATE_ACTIVE_DELETE_FORBIDDEN",
                    "DEACTIVATE_FIRST",
                    null);
        }
        AnchorRatingTemplateResponse before = toResponse(entity);
        repository.delete(entity);
        adminConfigAuditSupport.captureDelete("ANCHOR_RATING_TEMPLATE", id.toString(), before, "Anchor rating template deleted");
    }

    private void apply(AnchorRatingTemplate entity, AnchorRatingTemplateRequest request) {
        entity.setName(request.getName().trim());
        entity.setVersion(Math.max(1, request.getVersion()));
        entity.setConfigJson(request.getConfigJson() != null ? request.getConfigJson() : Map.of());
    }

    private AnchorRatingTemplateResponse toResponse(AnchorRatingTemplate entity) {
        return AnchorRatingTemplateResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .version(entity.getVersion())
                .active(entity.isActive())
                .configJson(entity.getConfigJson())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
