package com.los.core.customercategory.selection;

import com.los.core.customercategory.CustomerCategoryEntity;
import com.los.core.customercategory.CustomerCategoryRepository;
import com.los.core.customercategory.ConfigLifecycleStatus;
import com.los.core.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Application Category eligibility / disambiguation / selection APIs.
 * Does not run Policy, Scorecard, W4, or W6.
 */
@RestController
@RequestMapping("/api/v1/applications/{applicationId}/category-selection")
@RequiredArgsConstructor
public class ApplicationCategorySelectionController {

    private final CategorySelectionService selectionService;
    private final CustomerCategoryRepository categoryRepository;

    @Value("${los.category-selection.allow-draft-simulation:false}")
    private boolean allowDraftSimulationDefault;

    @GetMapping
    public CategorySelectionDtos.EligibilityResult evaluate(
            @PathVariable UUID applicationId,
            @RequestParam(value = "allowDraftSimulation", required = false) Boolean allowDraft) {
        try {
            return selectionService.evaluate(applicationId, resolveDraft(allowDraft));
        } catch (BusinessRuleException e) {
            throw map(e);
        }
    }

    @PostMapping("/answers")
    public CategorySelectionDtos.EligibilityResult answer(
            @PathVariable UUID applicationId,
            @RequestParam(value = "allowDraftSimulation", required = false) Boolean allowDraft,
            @RequestBody CategorySelectionDtos.AnswerRequest body) {
        try {
            return selectionService.answer(applicationId, body, resolveDraft(allowDraft));
        } catch (BusinessRuleException e) {
            throw map(e);
        }
    }

    @PostMapping("/auto-select")
    public CategorySelectionDtos.EligibilityResult autoSelect(
            @PathVariable UUID applicationId,
            @RequestParam(value = "allowDraftSimulation", required = false) Boolean allowDraft,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            String actor = body != null && body.get("actor") != null
                    ? String.valueOf(body.get("actor")) : "SYSTEM";
            return selectionService.autoSelectIfSingle(applicationId, actor, resolveDraft(allowDraft));
        } catch (BusinessRuleException e) {
            throw map(e);
        }
    }

    @PostMapping("/select")
    public CategorySelectionDtos.EligibilityResult select(
            @PathVariable UUID applicationId,
            @RequestParam(value = "allowDraftSimulation", required = false) Boolean allowDraft,
            @RequestBody CategorySelectionDtos.SelectRequest body) {
        try {
            return selectionService.select(applicationId, body, resolveDraft(allowDraft));
        } catch (BusinessRuleException e) {
            throw map(e);
        }
    }

    @GetMapping("/handoff")
    public CategorySelectionDtos.SelectedApplicationConfiguration handoff(
            @PathVariable UUID applicationId) {
        try {
            return selectionService.handoff(applicationId);
        } catch (BusinessRuleException e) {
            throw map(e);
        }
    }

    /**
     * Staging-only: attach SAFE disambiguation / proposition metadata to a DRAFT Category
     * without activating the Day-1 15 Categories.
     */
    @PutMapping("/admin/categories/{categoryId}/proposition-config")
    public Map<String, Object> updatePropositionConfig(
            @PathVariable UUID applicationId,
            @PathVariable UUID categoryId,
            @RequestBody CategorySelectionDtos.PropositionConfigUpdate body) {
        // applicationId unused — path nested for auth consistency; also expose under customer-categories
        return updateCategoryProposition(categoryId, body);
    }

    private Map<String, Object> updateCategoryProposition(
            UUID categoryId, CategorySelectionDtos.PropositionConfigUpdate body) {
        CustomerCategoryEntity cat = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        if (cat.getStatus() == ConfigLifecycleStatus.ACTIVE) {
            // Allow proposition cosmetic updates only via dedicated service later;
            // for now restrict to DRAFT/APPROVED to avoid live mutation surprises
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Use DRAFT Category for proposition/disambiguation config in this step");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        if (body.proposition() != null) {
            payload.put("proposition", body.proposition());
        }
        if (body.disambiguation() != null) {
            payload.put("disambiguation", body.disambiguation());
        }
        CategoryPropositionConfig.putPropositionConfig(cat, payload);
        categoryRepository.save(cat);
        return Map.of(
                "categoryId", cat.getId(),
                "code", cat.getCode(),
                "versionNo", cat.getVersionNo(),
                "governanceJson", cat.getGovernanceJson());
    }

    private boolean resolveDraft(Boolean allowDraft) {
        if (allowDraft != null) {
            return allowDraft;
        }
        return allowDraftSimulationDefault;
    }

    private ResponseStatusException map(BusinessRuleException e) {
        String reason = e.getReason() != null ? e.getReason() : "";
        HttpStatus status = switch (reason) {
            case "CATEGORY_NOT_ELIGIBLE", "CATEGORY_ALREADY_SELECTED", "CATEGORY_WORKFLOW_CONFLICT",
                 "UNSAFE_DISAMBIGUATION_QUESTION", "ANSWER_REQUIRED", "CATEGORY_BINDINGS_INCOMPLETE" ->
                    HttpStatus.BAD_REQUEST;
            case "APPLICATION_NOT_FOUND", "CATEGORY_NOT_FOUND", "CATEGORY_NOT_SELECTED",
                 "WORKFLOW_VERSION_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.BAD_REQUEST;
        };
        return new ResponseStatusException(status, e.getMessage());
    }
}
