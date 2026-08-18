package com.los.core.customercategory.selection;

import com.los.core.customercategory.CustomerCategoryEntity;
import com.los.core.customercategory.CustomerCategoryRepository;
import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.workflow.WorkflowContentHash;
import com.los.core.service.workflow.WorkflowResolutionSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Category selection orchestrator — eligibility → disambiguation → explicit/auto select → lock.
 * Does NOT run Policy, Scorecard, W4, W5, or W6.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategorySelectionService {

    private final LoanApplicationRepository applicationRepository;
    private final CustomerCategoryRepository categoryRepository;
    private final CustomerCategoryEligibilityService eligibilityService;
    private final CategoryDisambiguationService disambiguationService;
    private final CategoryConfigurationPinValidator pinValidator;

    @Transactional(readOnly = true)
    public CategorySelectionDtos.EligibilityResult evaluate(UUID applicationId, boolean allowDraftSimulation) {
        LoanApplication app = loadApp(applicationId);
        if (app.getSelectedCustomerCategoryId() != null) {
            return selectedResult(app);
        }
        CategorySelectionDtos.EligibilityContext ctx =
                eligibilityService.contextFromApplication(applicationId, allowDraftSimulation);
        List<CustomerCategoryEntity> eligible = eligibilityService.findEligibleEntities(ctx);
        Map<String, String> known = new LinkedHashMap<>(disambiguationService.priorAnswers(applicationId));
        // Apply prior answers to filter (save/resume)
        eligible = filterByPriorAnswers(eligible, known);

        return buildResult(app, ctx, eligible, known);
    }

    @Transactional
    public CategorySelectionDtos.EligibilityResult answer(
            UUID applicationId,
            CategorySelectionDtos.AnswerRequest req,
            boolean allowDraftSimulation) {
        LoanApplication app = loadApp(applicationId);
        assertNotSelected(app);
        CategorySelectionDtos.EligibilityContext ctx =
                eligibilityService.contextFromApplication(applicationId, allowDraftSimulation);
        List<CustomerCategoryEntity> eligible = eligibilityService.findEligibleEntities(ctx);
        Map<String, String> known = new LinkedHashMap<>(disambiguationService.priorAnswers(applicationId));
        eligible = filterByPriorAnswers(eligible, known);

        eligible = disambiguationService.applyAnswer(
                applicationId, eligible, req.questionId(), req.answerValue(),
                req.actor(), req.actorRole());
        known.put(
                SafeDisambiguationCatalogue.get(req.questionId()) != null
                        ? SafeDisambiguationCatalogue.get(req.questionId()).attributeKey()
                        : req.questionId(),
                req.answerValue());

        CategorySelectionDtos.EligibilityResult result = buildResult(app, ctx, eligible, known);
        app.setCategorySelectionState(result.state().name());
        applicationRepository.save(app);
        return result;
    }

    @Transactional
    public CategorySelectionDtos.EligibilityResult autoSelectIfSingle(
            UUID applicationId, String actor, boolean allowDraftSimulation) {
        CategorySelectionDtos.EligibilityResult eval = evaluate(applicationId, allowDraftSimulation);
        if (eval.state() != CategorySelectionState.AUTO_SINGLE_MATCH || eval.eligible().isEmpty()) {
            return eval;
        }
        CategorySelectionDtos.EligibleCategoryView only = eval.eligible().get(0);
        CustomerCategoryEntity cat = categoryRepository.findById(only.categoryId())
                .orElseThrow(() -> new BusinessRuleException("Category not found",
                        "CATEGORY_NOT_FOUND", "category-selection", null));
        if (!CategoryPropositionConfig.allowAutoSingleMatch(cat)) {
            return eval;
        }
        return select(applicationId, new CategorySelectionDtos.SelectRequest(
                only.categoryId(), actor, "SYSTEM", null,
                CategorySelectionSource.AUTO_SINGLE_ELIGIBLE), allowDraftSimulation);
    }

    @Transactional
    public CategorySelectionDtos.EligibilityResult select(
            UUID applicationId,
            CategorySelectionDtos.SelectRequest req,
            boolean allowDraftSimulation) {
        LoanApplication app = loadApp(applicationId);
        assertNotSelected(app);

        CategorySelectionDtos.EligibilityContext ctx =
                eligibilityService.contextFromApplication(applicationId, allowDraftSimulation);
        List<CustomerCategoryEntity> eligible = eligibilityService.findEligibleEntities(ctx);
        Map<String, String> known = disambiguationService.priorAnswers(applicationId);
        eligible = filterByPriorAnswers(eligible, known);

        CustomerCategoryEntity chosen = eligible.stream()
                .filter(c -> c.getId().equals(req.categoryId()))
                .findFirst()
                .orElse(null);
        if (chosen == null) {
            throw new BusinessRuleException(
                    "Category is not in the current eligible set",
                    "CATEGORY_NOT_ELIGIBLE",
                    "category-selection",
                    Map.of("categoryId", String.valueOf(req.categoryId())));
        }
        CategoryConfigurationPinValidator.ValidatedPin pin =
                pinValidator.validate(app, chosen, allowDraftSimulation);

        CategorySelectionSource source = req.selectionSource() != null
                ? req.selectionSource()
                : ("RM".equalsIgnoreCase(req.actorRole())
                ? CategorySelectionSource.RM_SELECTED
                : CategorySelectionSource.CUSTOMER_SELECTED);

        if (source == CategorySelectionSource.RM_SELECTED
                && (req.reason() == null || req.reason().isBlank())) {
            log.info("RM Category selection without reason applicationId={} categoryId={} actor={}",
                    applicationId, chosen.getId(), req.actor());
        }

        Instant now = Instant.now();
        WorkflowConfig wf = pin.workflow();
        app.setSelectedCustomerCategoryId(chosen.getId());
        app.setSelectedCustomerCategoryCode(chosen.getCode());
        app.setSelectedCustomerCategoryVersion(chosen.getVersionNo());
        app.setSelectedPolicyApplicabilityId(pin.applicability().getId());
        app.setSelectedPolicyDocumentId(pin.document().getId());
        app.setSelectedPolicyVersionLabel(chosen.getPolicyVersionLabel());
        app.setCategorySelectionSource(source.name());
        app.setCategorySelectedAt(now);
        app.setCategorySelectedBy(req.actor());
        app.setCategorySelectionReason(req.reason());
        app.setCategorySelectionState(CategorySelectionState.CATEGORY_SELECTED.name());

        app.setWorkflowId(wf.getId());
        app.setWorkflowVersion(wf.getVersion());
        app.setWorkflowResolutionSource(WorkflowResolutionSource.CATEGORY_SELECTION.name());
        app.setWorkflowResolvedAt(now);
        app.setWorkflowContentHash(WorkflowContentHash.of(wf));

        applicationRepository.save(app);
        log.info("Category selected applicationId={} category={}:{} source={} — W4/W6 NOT triggered",
                applicationId, chosen.getCode(), chosen.getVersionNo(), source);

        return selectedResult(app);
    }

    @Transactional(readOnly = true)
    public CategorySelectionDtos.SelectedApplicationConfiguration handoff(UUID applicationId) {
        LoanApplication app = loadApp(applicationId);
        if (app.getSelectedCustomerCategoryId() == null) {
            throw new BusinessRuleException(
                    "Category not selected",
                    "CATEGORY_NOT_SELECTED",
                    "category-handoff",
                    null);
        }
        return toHandoff(app);
    }

    private CategorySelectionDtos.EligibilityResult buildResult(
            LoanApplication app,
            CategorySelectionDtos.EligibilityContext ctx,
            List<CustomerCategoryEntity> eligible,
            Map<String, String> known) {

        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("allowDraftSimulation", ctx.allowDraftSimulation());
        diagnostics.put("w4Triggered", false);
        diagnostics.put("w5Triggered", false);
        diagnostics.put("w6Triggered", false);
        diagnostics.put("policyExecuted", false);
        diagnostics.put("scorecardExecuted", false);
        diagnostics.put("priorAnswers", known);

        if (eligible.isEmpty()) {
            List<String> reasons = eligibilityService.noMatchReasons(ctx, List.of());
            app.setCategorySelectionState(CategorySelectionState.NO_ELIGIBLE_CATEGORY.name());
            return new CategorySelectionDtos.EligibilityResult(
                    CategorySelectionState.NO_ELIGIBLE_CATEGORY,
                    List.of(),
                    reasons,
                    null,
                    List.of(),
                    null,
                    diagnostics);
        }

        List<CategorySelectionDtos.EligibleCategoryView> views =
                eligible.stream().map(CustomerCategoryEligibilityService::toView).toList();

        if (eligible.size() == 1) {
            CustomerCategoryEntity only = eligible.get(0);
            if (CategoryPropositionConfig.allowAutoSingleMatch(only)) {
                return new CategorySelectionDtos.EligibilityResult(
                        CategorySelectionState.AUTO_SINGLE_MATCH,
                        views,
                        List.of(),
                        null,
                        List.of(),
                        null,
                        diagnostics);
            }
            // Single but auto disabled → explicit confirmation
            return new CategorySelectionDtos.EligibilityResult(
                    CategorySelectionState.EXPLICIT_PROPOSITION_SELECTION_REQUIRED,
                    views,
                    List.of(),
                    null,
                    propositions(eligible),
                    null,
                    diagnostics);
        }

        CategorySelectionDtos.DisambiguationQuestionView next =
                disambiguationService.nextQuestion(app.getId(), eligible, known);
        if (next != null) {
            return new CategorySelectionDtos.EligibilityResult(
                    CategorySelectionState.DISAMBIGUATION_REQUIRED,
                    views,
                    List.of(),
                    next,
                    List.of(),
                    null,
                    diagnostics);
        }

        return new CategorySelectionDtos.EligibilityResult(
                CategorySelectionState.EXPLICIT_PROPOSITION_SELECTION_REQUIRED,
                views,
                List.of(),
                null,
                propositions(eligible),
                null,
                diagnostics);
    }

    private List<CategorySelectionDtos.PropositionCard> propositions(List<CustomerCategoryEntity> cats) {
        List<CategorySelectionDtos.PropositionCard> cards = new ArrayList<>();
        for (CustomerCategoryEntity c : cats) {
            Map<String, Object> prop = CategoryPropositionConfig.proposition(c);
            List<String> benefits = new ArrayList<>();
            if (prop.get("benefits") instanceof List<?> list) {
                for (Object o : list) {
                    if (o != null) {
                        benefits.add(String.valueOf(o));
                    }
                }
            }
            cards.add(new CategorySelectionDtos.PropositionCard(
                    c.getId(),
                    c.getCode(),
                    c.getVersionNo(),
                    CategoryPropositionConfig.customerFacingName(c),
                    CategoryPropositionConfig.shortDescription(c),
                    prop.get("requirementsSummary") != null
                            ? String.valueOf(prop.get("requirementsSummary")) : null,
                    List.copyOf(benefits),
                    CategoryPropositionConfig.displayOrder(c)));
        }
        return cards;
    }

    private List<CustomerCategoryEntity> filterByPriorAnswers(
            List<CustomerCategoryEntity> eligible, Map<String, String> known) {
        if (known == null || known.isEmpty()) {
            return eligible;
        }
        List<CustomerCategoryEntity> filtered = new ArrayList<>(eligible);
        for (Map.Entry<String, String> e : known.entrySet()) {
            String attr = e.getKey();
            String answer = e.getValue();
            filtered = filtered.stream()
                    .filter(c -> {
                        List<String> accepted = CategoryPropositionConfig.disambiguationAttributes(c)
                                .getOrDefault(attr, List.of());
                        // If Category has no attr configured, keep it (question may not apply)
                        if (accepted.isEmpty()) {
                            return true;
                        }
                        return SafeDisambiguationCatalogue.answerRetains(attr, answer, accepted);
                    })
                    .toList();
        }
        return new ArrayList<>(filtered);
    }

    private CategorySelectionDtos.EligibilityResult selectedResult(LoanApplication app) {
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("w4Triggered", false);
        diagnostics.put("w6Triggered", false);
        diagnostics.put("policyExecuted", false);
        CategorySelectionDtos.SelectedApplicationConfiguration handoff = toHandoff(app);
        List<CategorySelectionDtos.EligibleCategoryView> views = List.of();
        if (app.getSelectedCustomerCategoryId() != null) {
            views = categoryRepository.findById(app.getSelectedCustomerCategoryId())
                    .map(c -> List.of(CustomerCategoryEligibilityService.toView(c)))
                    .orElse(List.of());
        }
        return new CategorySelectionDtos.EligibilityResult(
                CategorySelectionState.CATEGORY_SELECTED,
                views,
                List.of(),
                null,
                List.of(),
                handoff,
                diagnostics);
    }

    private CategorySelectionDtos.SelectedApplicationConfiguration toHandoff(LoanApplication app) {
        return new CategorySelectionDtos.SelectedApplicationConfiguration(
                app.getId(),
                app.getSelectedCustomerCategoryId(),
                app.getSelectedCustomerCategoryCode(),
                app.getSelectedCustomerCategoryVersion() != null
                        ? app.getSelectedCustomerCategoryVersion() : 0,
                app.getSelectedPolicyApplicabilityId(),
                app.getSelectedPolicyDocumentId(),
                app.getSelectedPolicyVersionLabel(),
                app.getWorkflowId(),
                app.getWorkflowVersion(),
                app.getCategorySelectionSource() != null
                        ? CategorySelectionSource.valueOf(app.getCategorySelectionSource())
                        : null,
                app.getCategorySelectedAt(),
                app.getCategorySelectedBy());
    }

    private LoanApplication loadApp(UUID id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException(
                        "Application not found: " + id,
                        "APPLICATION_NOT_FOUND", "category-selection", Map.of()));
    }

    private void assertNotSelected(LoanApplication app) {
        if (app.getSelectedCustomerCategoryId() != null) {
            throw new BusinessRuleException(
                    "Category already selected for this application",
                    "CATEGORY_ALREADY_SELECTED",
                    "category-selection",
                    Map.of("categoryId", app.getSelectedCustomerCategoryId().toString()));
        }
    }

}
