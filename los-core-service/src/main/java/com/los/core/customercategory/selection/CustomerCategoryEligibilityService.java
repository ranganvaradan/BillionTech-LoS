package com.los.core.customercategory.selection;

import com.los.core.customercategory.ConfigLifecycleStatus;
import com.los.core.customercategory.CustomerCategoryEntity;
import com.los.core.customercategory.CustomerCategoryRepository;
import com.los.core.customercategory.MatchWildcard;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.IntakeSegment;
import com.los.core.repository.LoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Authoritative Category eligibility — Role/Entity/Product/Amount/effectivity (+ programme tags).
 * Does not use Policy/Scorecard thresholds. Identical-dimension Categories are all retained.
 */
@Service
@RequiredArgsConstructor
public class CustomerCategoryEligibilityService {

    private final CustomerCategoryRepository categoryRepository;
    private final LoanApplicationRepository applicationRepository;

    @Transactional(readOnly = true)
    public List<CustomerCategoryEntity> findEligibleEntities(CategorySelectionDtos.EligibilityContext ctx) {
        Instant asOf = ctx.asOf() != null ? ctx.asOf() : Instant.now();
        List<CustomerCategoryEntity> pool = new ArrayList<>();
        pool.addAll(categoryRepository.findByStatus(ConfigLifecycleStatus.ACTIVE));
        if (ctx.allowDraftSimulation()) {
            // Staging/clean-room: include governed non-ACTIVE Categories without activating Day-1 seed.
            pool.addAll(categoryRepository.findByStatus(ConfigLifecycleStatus.DRAFT));
            pool.addAll(categoryRepository.findByStatus(ConfigLifecycleStatus.IN_REVIEW));
            pool.addAll(categoryRepository.findByStatus(ConfigLifecycleStatus.APPROVED));
        }

        List<CustomerCategoryEntity> matched = new ArrayList<>();
        for (CustomerCategoryEntity c : pool) {
            if (!matchesDimensions(c, ctx, asOf)) {
                continue;
            }
            if (!hasUsableBinds(c)) {
                continue;
            }
            if (!matchesProgrammeChannel(c, ctx)) {
                continue;
            }
            matched.add(c);
        }
        // Stable order by displayOrder then name — NOT used as priority winner
        matched.sort(Comparator
                .comparingInt(CategoryPropositionConfig::displayOrder)
                .thenComparing(CustomerCategoryEntity::getName, Comparator.nullsLast(String::compareToIgnoreCase)));
        return matched;
    }

    @Transactional(readOnly = true)
    public CategorySelectionDtos.EligibilityContext contextFromApplication(
            UUID applicationId, boolean allowDraftSimulation) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new com.los.core.exception.BusinessRuleException(
                        "Application not found: " + applicationId,
                        "APPLICATION_NOT_FOUND", "category-eligibility", Map.of()));
        String role = app.getIntakeSegment() != null ? app.getIntakeSegment().name() : IntakeSegment.BORROWER.name();
        String entity = app.getBorrowerType() != null ? app.getBorrowerType().name() : null;
        String programmeTag = null;
        if (app.getBusinessInfo() != null && app.getBusinessInfo().get("programmeTag") != null) {
            programmeTag = String.valueOf(app.getBusinessInfo().get("programmeTag"));
        }
        String channelTag = null;
        if (app.getBusinessInfo() != null && app.getBusinessInfo().get("channelTag") != null) {
            channelTag = String.valueOf(app.getBusinessInfo().get("channelTag"));
        }
        return new CategorySelectionDtos.EligibilityContext(
                applicationId,
                role,
                entity,
                app.getLoanProduct(),
                app.getCreditVintage(),
                app.getRequestedAmount(),
                Instant.now(),
                app.getSubProgramId(),
                programmeTag,
                channelTag,
                allowDraftSimulation);
    }

    public List<String> noMatchReasons(CategorySelectionDtos.EligibilityContext ctx, List<CustomerCategoryEntity> pool) {
        List<String> reasons = new ArrayList<>();
        if (ctx.customerRole() == null || ctx.customerRole().isBlank()) {
            reasons.add("ROLE");
        }
        if (ctx.entityType() == null || ctx.entityType().isBlank()) {
            reasons.add("ENTITY_TYPE");
        }
        if (ctx.loanProduct() == null || ctx.loanProduct().isBlank()) {
            reasons.add("PRODUCT");
        }
        if (ctx.requestedAmount() == null) {
            reasons.add("AMOUNT");
        }
        if (pool.isEmpty() && reasons.isEmpty()) {
            reasons.add("NO_MATCHING_CATEGORY");
            reasons.add("EFFECTIVITY_OR_CRITERIA");
        }
        return reasons;
    }

    public static CategorySelectionDtos.EligibleCategoryView toView(CustomerCategoryEntity c) {
        Map<String, Object> prop = CategoryPropositionConfig.proposition(c);
        return new CategorySelectionDtos.EligibleCategoryView(
                c.getId(),
                c.getCode(),
                c.getVersionNo(),
                c.getName(),
                CategoryPropositionConfig.customerFacingName(c),
                CategoryPropositionConfig.shortDescription(c),
                prop.get("requirementsSummary") != null ? String.valueOf(prop.get("requirementsSummary")) : null,
                CategoryPropositionConfig.displayOrder(c),
                c.getPolicyApplicabilityId(),
                c.getPolicyDocumentId(),
                c.getPolicyVersionLabel(),
                c.getWorkflowId(),
                c.getWorkflowVersion(),
                c.getStatus() != null ? c.getStatus().name() : null,
                CategoryPropositionConfig.disambiguationAttributes(c));
    }

    private boolean matchesDimensions(CustomerCategoryEntity c, CategorySelectionDtos.EligibilityContext ctx, Instant asOf) {
        if (!dimMatch(c.getIntakeSegment(), ctx.customerRole())) {
            return false;
        }
        if (!dimMatch(c.getBorrowerType(), ctx.entityType())) {
            return false;
        }
        if (!dimMatch(c.getLoanProduct(), ctx.loanProduct())) {
            return false;
        }
        if (!dimMatch(c.getCreditVintage(), ctx.creditVintage())) {
            return false;
        }
        if (!amountMatch(c.getMinAmount(), c.getMaxAmount(), ctx.requestedAmount())) {
            return false;
        }
        if (c.getEffectiveFrom() != null && asOf.isBefore(c.getEffectiveFrom())) {
            return false;
        }
        if (c.getEffectiveUntil() != null && !asOf.isBefore(c.getEffectiveUntil())) {
            return false;
        }
        return true;
    }

    private boolean matchesProgrammeChannel(CustomerCategoryEntity c, CategorySelectionDtos.EligibilityContext ctx) {
        List<String> tags = CategoryPropositionConfig.programmeTags(c);
        if (tags.isEmpty()) {
            return true; // no programme filter configured
        }
        String known = ctx.programmeTag();
        if (known == null && ctx.subProgramId() != null) {
            known = ctx.subProgramId().toString();
        }
        if (known == null || known.isBlank()) {
            // Category requires programme tag but app has none — still eligible until
            // progressive/programme question; do not silently exclude (ask later if configured)
            return true;
        }
        String k = known.trim().toUpperCase(Locale.ROOT);
        for (String t : tags) {
            if (t != null && t.trim().equalsIgnoreCase(k)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasUsableBinds(CustomerCategoryEntity c) {
        return CategoryConfigurationPinValidator.hasUsableBinds(c);
    }

    private static boolean dimMatch(String categoryDim, String appValue) {
        if (MatchWildcard.isAny(categoryDim)) {
            return true;
        }
        if (categoryDim == null || appValue == null) {
            return false;
        }
        return categoryDim.trim().equalsIgnoreCase(appValue.trim());
    }

    /** Inclusive bounds; null min/max = unbounded. */
    static boolean amountMatch(BigDecimal min, BigDecimal max, BigDecimal amount) {
        if (amount == null) {
            return min == null && max == null;
        }
        if (min != null && amount.compareTo(min) < 0) {
            return false;
        }
        if (max != null && amount.compareTo(max) > 0) {
            return false;
        }
        return true;
    }
}
