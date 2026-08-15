package com.los.core.requirement;

import com.los.core.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * W5 — application-scoped customer requirements (staff/RM).
 * Driven solely by W4 RequirementPlan. No source execution.
 */
@RestController
@RequestMapping("/api/v1/applications/{applicationId}/customer-requirements")
@RequiredArgsConstructor
public class ApplicationCustomerRequirementsController {

    private final CustomerRequirementsViewService viewService;
    private final RequirementPlanRepository planRepository;
    private final RequirementItemTransitionService transitionService;

    @GetMapping
    public CustomerRequirementDtos.CustomerRequirementsView get(
            @PathVariable UUID applicationId) {
        try {
            return viewService.viewForApplication(applicationId);
        } catch (BusinessRuleException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/actions/{itemId}/direct-input")
    public CustomerRequirementDtos.CustomerRequirementsView submitDirect(
            @PathVariable UUID applicationId,
            @PathVariable UUID itemId,
            @RequestBody CustomerRequirementDtos.DirectInputSubmitRequest body) {
        try {
            RequirementPlanEntity plan = requirePlan(applicationId);
            String actor = resolveActor(body != null ? body.actor() : null, body != null ? body.actorRole() : null);
            if (body != null && Boolean.TRUE.equals(body.saveDraftOnly())) {
                transitionService.saveDirectInputDraft(plan.getId(), itemId, body.value(), actor,
                        body.actorRole() != null ? body.actorRole() : "RM");
            } else {
                boolean verified = body != null && Boolean.TRUE.equals(body.verified());
                transitionService.markDirectInput(plan.getId(), itemId,
                        body != null ? body.value() : null, verified, actor,
                        body != null ? body.reason() : null);
                stampActorRole(plan.getId(), itemId, body != null ? body.actorRole() : "RM");
            }
            return viewService.viewForApplication(applicationId);
        } catch (BusinessRuleException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/actions/{itemId}/document")
    public CustomerRequirementDtos.CustomerRequirementsView submitDocument(
            @PathVariable UUID applicationId,
            @PathVariable UUID itemId,
            @RequestBody CustomerRequirementDtos.DocumentFulfilRequest body) {
        try {
            RequirementPlanEntity plan = requirePlan(applicationId);
            if (body == null || body.documentRef() == null || body.documentRef().isBlank()) {
                throw new BusinessRuleException("documentRef is required");
            }
            String actor = resolveActor(body.actor(), body.actorRole());
            transitionService.markDocumentUploaded(plan.getId(), itemId, body.documentRef(),
                    null, actor, body.reason());
            stampActorRole(plan.getId(), itemId, body.actorRole() != null ? body.actorRole() : "RM");
            return viewService.viewForApplication(applicationId);
        } catch (BusinessRuleException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/actions/{itemId}/choose-mode")
    public CustomerRequirementDtos.CustomerRequirementsView chooseMode(
            @PathVariable UUID applicationId,
            @PathVariable UUID itemId,
            @RequestBody CustomerRequirementDtos.ChooseModeRequest body) {
        try {
            RequirementPlanEntity plan = requirePlan(applicationId);
            if (body == null || body.mode() == null) {
                throw new BusinessRuleException("mode is required");
            }
            String actor = resolveActor(body.actor(), body.actorRole());
            transitionService.chooseFulfilmentMode(plan.getId(), itemId, body.mode(), actor,
                    body.actorRole() != null ? body.actorRole() : "RM");
            return viewService.viewForApplication(applicationId);
        } catch (BusinessRuleException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/actions/{itemId}/document-outcome")
    public CustomerRequirementDtos.CustomerRequirementsView documentOutcome(
            @PathVariable UUID applicationId,
            @PathVariable UUID itemId,
            @RequestBody CustomerRequirementDtos.DocumentOutcomeRequest body) {
        try {
            RequirementPlanEntity plan = requirePlan(applicationId);
            String outcome = body != null ? body.outcome() : null;
            String actor = resolveActor(body != null ? body.actor() : null,
                    body != null ? body.actorRole() : null);
            if ("DOCUMENT_REJECTED".equalsIgnoreCase(outcome)) {
                transitionService.markDocumentRejected(plan.getId(), itemId, actor,
                        body != null ? body.reason() : null);
            } else if ("EXTRACTION_FAILED".equalsIgnoreCase(outcome)) {
                transitionService.markExtractionFailed(plan.getId(), itemId, actor,
                        body != null ? body.reason() : null);
            } else {
                throw new BusinessRuleException("outcome must be DOCUMENT_REJECTED or EXTRACTION_FAILED");
            }
            return viewService.viewForApplication(applicationId);
        } catch (BusinessRuleException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private RequirementPlanEntity requirePlan(UUID applicationId) {
        return planRepository.findByApplicationIdWithItems(applicationId).stream()
                .filter(p -> p.getStatus() == RequirementPlanStatus.ACTIVE
                        || p.getStatus() == RequirementPlanStatus.DRAFT)
                .max(java.util.Comparator.comparingInt(RequirementPlanEntity::getPlanVersion))
                .orElseThrow(() -> new BusinessRuleException("No active RequirementPlan for application"));
    }

    private void stampActorRole(UUID planId, UUID itemId, String actorRole) {
        if (actorRole == null || actorRole.isBlank()) return;
        planRepository.findByIdWithItems(planId).ifPresent(plan -> {
            for (RequirementItemEntity item : plan.getItems()) {
                if (itemId.equals(item.getId())
                        || (item.getSourceHints() != null
                        && item.getSourceHints().get("pendingDocumentGroup") != null)) {
                    // stamp only matching item below
                }
                if (itemId.equals(item.getId())) {
                    if (item.getSourceHints() == null) {
                        item.setSourceHints(new java.util.LinkedHashMap<>());
                    }
                    item.getSourceHints().put("lastActorRole", actorRole);
                }
            }
        });
    }

    private static String resolveActor(String actor, String role) {
        if (actor != null && !actor.isBlank()) {
            return role != null && !role.isBlank() ? actor + "|" + role : actor;
        }
        return role != null && !role.isBlank() ? role : "RM";
    }
}
