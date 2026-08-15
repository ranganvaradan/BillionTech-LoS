package com.los.core.requirement;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;
import com.los.core.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * W5 — projects W4 RequirementPlan into customer/RM actionable requirements.
 * RequirementPlan is the only UI authority. No source execution.
 */
@Service
@RequiredArgsConstructor
public class CustomerRequirementsViewService {

    private final RequirementPlanRepository planRepository;

    @Transactional(readOnly = true)
    public CustomerRequirementDtos.CustomerRequirementsView viewForApplication(UUID applicationId) {
        if (applicationId == null) {
            throw new BusinessRuleException("applicationId is required");
        }
        Optional<RequirementPlanEntity> opt = planRepository.findByApplicationIdWithItems(applicationId).stream()
                .filter(p -> p.getStatus() == RequirementPlanStatus.ACTIVE
                        || p.getStatus() == RequirementPlanStatus.DRAFT)
                .max(Comparator.comparingInt(RequirementPlanEntity::getPlanVersion));
        if (opt.isEmpty()) {
            return new CustomerRequirementDtos.CustomerRequirementsView(
                    applicationId, null, 0, false,
                    new CustomerRequirementDtos.CustomerSummary(0, 0, 0, 0, 0, 0),
                    List.of(), List.of(),
                    "No information is required from you right now.");
        }
        return project(opt.get());
    }

    @Transactional(readOnly = true)
    public CustomerRequirementDtos.CustomerRequirementsView viewForPlan(UUID planId) {
        RequirementPlanEntity plan = planRepository.findByIdWithItems(planId)
                .orElseThrow(() -> new BusinessRuleException("Requirement plan not found: " + planId));
        return project(plan);
    }

    @Transactional(readOnly = true)
    public List<CustomerRequirementDtos.AdminCustomerDebugRow> adminDebug(UUID planId) {
        RequirementPlanEntity plan = planRepository.findByIdWithItems(planId)
                .orElseThrow(() -> new BusinessRuleException("Requirement plan not found: " + planId));
        List<CustomerRequirementDtos.AdminCustomerDebugRow> rows = new ArrayList<>();
        for (RequirementItemEntity item : safeItems(plan)) {
            boolean customerAction = isCustomerFacingItem(item) && isOutstanding(item);
            String why = item.getSourceHints() != null && item.getSourceHints().get("whyRequired") != null
                    ? String.valueOf(item.getSourceHints().get("whyRequired"))
                    : "Required by Policy";
            FulfilmentMode preferred = preferredMode(item);
            rows.add(new CustomerRequirementDtos.AdminCustomerDebugRow(
                    item.getId(),
                    item.getItemKey(),
                    item.getCanonicalParameterId(),
                    customerAction,
                    item.getAllowedFulfilmentModes(),
                    preferred,
                    item.getCustomerFulfilmentState(),
                    item.getDataReadinessState(),
                    item.getSourceAcquisitionState(),
                    why,
                    item.getEvidenceRef(),
                    item.getDocumentRef()));
        }
        return rows;
    }

    public CustomerRequirementDtos.CustomerRequirementsView project(RequirementPlanEntity plan) {
        List<RequirementItemEntity> items = safeItems(plan);
        CanonicalParameterRegistry registry = PolicyStudioConvergencePresenter.registry();

        Map<String, List<RequirementItemEntity>> docGroups = new LinkedHashMap<>();
        List<RequirementItemEntity> directOrDual = new ArrayList<>();
        List<RequirementItemEntity> providedOrProcessing = new ArrayList<>();

        for (RequirementItemEntity item : items) {
            if (!isCustomerFacingItem(item)) {
                continue;
            }
            if (isProvidedOrProcessingDisplay(item)) {
                providedOrProcessing.add(item);
            }
            if (!isOutstanding(item) && item.getCustomerFulfilmentState() != CustomerFulfilmentState.REUPLOAD_REQUIRED) {
                continue;
            }
            String docGroup = documentGroup(item);
            boolean docOnly = item.allows(FulfilmentMode.DOCUMENT_UPLOAD)
                    && !item.allows(FulfilmentMode.DIRECT_INPUT)
                    && docGroup != null;
            boolean dual = item.allows(FulfilmentMode.DOCUMENT_UPLOAD) && item.allows(FulfilmentMode.DIRECT_INPUT);
            if (docOnly) {
                docGroups.computeIfAbsent(docGroup, k -> new ArrayList<>()).add(item);
            } else if (dual && docGroup != null && item.getFulfilmentModeUsed() == FulfilmentMode.DOCUMENT_UPLOAD) {
                docGroups.computeIfAbsent(docGroup, k -> new ArrayList<>()).add(item);
            } else {
                directOrDual.add(item);
            }
        }

        List<CustomerRequirementDtos.CustomerAction> actions = new ArrayList<>();
        Set<String> seenDocKeys = new LinkedHashSet<>();

        for (Map.Entry<String, List<RequirementItemEntity>> e : docGroups.entrySet()) {
            if (!seenDocKeys.add(e.getKey())) continue;
            actions.add(toDocumentAction(e.getKey(), e.getValue(), registry));
        }
        for (RequirementItemEntity item : directOrDual) {
            // Skip if already covered by a document group and chosen/prefer document without dual choice needed
            String dg = documentGroup(item);
            if (dg != null && docGroups.containsKey(dg)
                    && item.allows(FulfilmentMode.DOCUMENT_UPLOAD)
                    && !item.allows(FulfilmentMode.DIRECT_INPUT)) {
                continue;
            }
            actions.add(toItemAction(item, registry));
        }

        // Provided/processing cards (grouped documents once)
        List<CustomerRequirementDtos.CustomerAction> providedCards = new ArrayList<>();
        Map<String, List<RequirementItemEntity>> providedDocs = new LinkedHashMap<>();
        List<RequirementItemEntity> providedDirect = new ArrayList<>();
        for (RequirementItemEntity item : providedOrProcessing) {
            if (!isCustomerFacingItem(item)) continue;
            String dg = documentGroup(item);
            if (dg != null && item.getFulfilmentModeUsed() == FulfilmentMode.DOCUMENT_UPLOAD) {
                providedDocs.computeIfAbsent(dg, k -> new ArrayList<>()).add(item);
            } else if (item.getCustomerFulfilmentState() == CustomerFulfilmentState.PROVIDED
                    || item.getCustomerFulfilmentState() == CustomerFulfilmentState.REUPLOAD_REQUIRED) {
                providedDirect.add(item);
            }
        }
        for (Map.Entry<String, List<RequirementItemEntity>> e : providedDocs.entrySet()) {
            providedCards.add(toDocumentAction(e.getKey(), e.getValue(), registry));
        }
        for (RequirementItemEntity item : providedDirect) {
            providedCards.add(toItemAction(item, registry));
        }

        int infoRequired = 0;
        int docsRequired = 0;
        int reupload = 0;
        for (CustomerRequirementDtos.CustomerAction a : actions) {
            if (!a.actionable()) continue;
            if (a.reuploadRequired()) reupload++;
            if (a.allowedModes().contains(FulfilmentMode.DOCUMENT_UPLOAD)
                    && (!a.allowedModes().contains(FulfilmentMode.DIRECT_INPUT)
                    || a.chosenMode() == FulfilmentMode.DOCUMENT_UPLOAD
                    || a.preferredMode() == FulfilmentMode.DOCUMENT_UPLOAD && a.chosenMode() == null)) {
                if (a.documentGroup() != null && (a.chosenMode() == null || a.chosenMode() == FulfilmentMode.DOCUMENT_UPLOAD)) {
                    if (!a.showChoice() || a.chosenMode() == FulfilmentMode.DOCUMENT_UPLOAD) {
                        docsRequired++;
                        continue;
                    }
                }
            }
            if (a.showChoice() && a.chosenMode() == null) {
                // count as one remaining action (choice), prefer counting as information until chosen
                infoRequired++;
            } else if (a.allowedModes().contains(FulfilmentMode.DIRECT_INPUT)
                    && (a.chosenMode() == null || a.chosenMode() == FulfilmentMode.DIRECT_INPUT
                    || !a.allowedModes().contains(FulfilmentMode.DOCUMENT_UPLOAD))) {
                infoRequired++;
            } else if (a.documentGroup() != null) {
                docsRequired++;
            } else {
                infoRequired++;
            }
        }

        int providedCount = (int) items.stream()
                .filter(CustomerRequirementsViewService::isCustomerFacingItem)
                .filter(i -> i.getCustomerFulfilmentState() == CustomerFulfilmentState.PROVIDED)
                .map(this::displayGroupKey)
                .distinct()
                .count();
        int processingCount = (int) items.stream()
                .filter(CustomerRequirementsViewService::isCustomerFacingItem)
                .filter(i -> i.getCustomerFulfilmentState() == CustomerFulfilmentState.PROVIDED)
                .filter(i -> i.getDataReadinessState() == DataReadinessState.PROCESSING
                        || i.getDataReadinessState() == DataReadinessState.EXTRACTED
                        || i.getDataReadinessState() == DataReadinessState.VERIFIED)
                .map(this::displayGroupKey)
                .distinct()
                .count();

        int remaining = (int) actions.stream().filter(CustomerRequirementDtos.CustomerAction::actionable).count();

        CustomerRequirementDtos.CustomerSummary summary = new CustomerRequirementDtos.CustomerSummary(
                infoRequired, docsRequired, providedCount, processingCount, remaining, reupload);

        return new CustomerRequirementDtos.CustomerRequirementsView(
                plan.getApplicationId(),
                plan.getId(),
                plan.getPlanVersion(),
                true,
                summary,
                List.copyOf(actions),
                List.copyOf(providedCards),
                remaining == 0
                        ? "You're all set — nothing more is needed from you right now."
                        : null);
    }

    private CustomerRequirementDtos.CustomerAction toDocumentAction(
            String group,
            List<RequirementItemEntity> groupItems,
            CanonicalParameterRegistry registry) {
        RequirementItemEntity lead = groupItems.get(0);
        List<UUID> ids = groupItems.stream().map(RequirementItemEntity::getId).toList();
        List<String> labels = new ArrayList<>();
        for (RequirementItemEntity i : groupItems) {
            labels.add(businessLabel(i, registry));
        }
        boolean reupload = groupItems.stream()
                .anyMatch(i -> i.getCustomerFulfilmentState() == CustomerFulfilmentState.REUPLOAD_REQUIRED);
        boolean extractionFailed = groupItems.stream().anyMatch(this::isExtractionFailed);
        boolean outstanding = groupItems.stream().anyMatch(CustomerRequirementsViewService::isOutstanding)
                || reupload;
        CustomerFulfilmentState fulfilment = reupload
                ? CustomerFulfilmentState.REUPLOAD_REQUIRED
                : lead.getCustomerFulfilmentState();
        DataReadinessState readiness = worstReadiness(groupItems);
        String statusLabel = customerStatusLabel(fulfilment, readiness, reupload, extractionFailed);
        String processingLabel = processingLabel(fulfilment, readiness, extractionFailed);

        return new CustomerRequirementDtos.CustomerAction(
                "document:" + group,
                sectionForDocument(group),
                humanDocumentTitle(group),
                "Upload this document so we can complete your application review.",
                "We use this document to verify related business information.",
                List.of(FulfilmentMode.DOCUMENT_UPLOAD),
                FulfilmentMode.DOCUMENT_UPLOAD,
                lead.getFulfilmentModeUsed(),
                fulfilment,
                statusLabel,
                readiness,
                processingLabel,
                outstanding,
                false,
                reupload,
                extractionFailed,
                group,
                lead.getDocumentRef(),
                ids,
                labels,
                null,
                draftFrom(lead));
    }

    private CustomerRequirementDtos.CustomerAction toItemAction(
            RequirementItemEntity item,
            CanonicalParameterRegistry registry) {
        String label = businessLabel(item, registry);
        List<FulfilmentMode> modes = item.getAllowedFulfilmentModes() != null
                ? item.getAllowedFulfilmentModes() : List.of();
        boolean showChoice = modes.contains(FulfilmentMode.DIRECT_INPUT)
                && modes.contains(FulfilmentMode.DOCUMENT_UPLOAD)
                && item.getFulfilmentModeUsed() == null
                && item.getCustomerFulfilmentState() != CustomerFulfilmentState.PROVIDED
                && !(item.getSourceHints() != null && item.getSourceHints().get("chosenMode") != null);
        boolean reupload = item.getCustomerFulfilmentState() == CustomerFulfilmentState.REUPLOAD_REQUIRED;
        boolean extractionFailed = isExtractionFailed(item);
        boolean outstanding = isOutstanding(item) || reupload;
        FulfilmentMode preferred = preferredMode(item);
        FulfilmentMode chosenUsed = item.getFulfilmentModeUsed();
        if (chosenUsed == null && item.getSourceHints() != null && item.getSourceHints().get("chosenMode") != null) {
            try {
                chosenUsed = FulfilmentMode.valueOf(String.valueOf(item.getSourceHints().get("chosenMode")));
            } catch (IllegalArgumentException ignored) {
                // leave null
            }
        }
        String why = whyNeededBusiness(item, registry);
        CustomerRequirementDtos.FieldMeta field = null;
        if (modes.contains(FulfilmentMode.DIRECT_INPUT)) {
            field = fieldMeta(item, registry, label);
        }
        String docGroup = documentGroup(item);
        return new CustomerRequirementDtos.CustomerAction(
                "item:" + item.getItemKey(),
                sectionForItem(item),
                label,
                field != null && field.helpText() != null
                        ? field.helpText()
                        : "Please provide this information to continue.",
                why,
                modes,
                preferred,
                chosenUsed,
                item.getCustomerFulfilmentState(),
                customerStatusLabel(item.getCustomerFulfilmentState(), item.getDataReadinessState(),
                        reupload, extractionFailed),
                item.getDataReadinessState(),
                processingLabel(item.getCustomerFulfilmentState(), item.getDataReadinessState(), extractionFailed),
                outstanding,
                showChoice,
                reupload,
                extractionFailed,
                docGroup,
                item.getDocumentRef(),
                item.getId() != null ? List.of(item.getId()) : List.of(),
                List.of(label),
                field,
                draftFrom(item));
    }

    static boolean isCustomerFacingItem(RequirementItemEntity item) {
        if (item == null) return false;
        if (item.getRequirementClass() == RequirementClass.ALREADY_AVAILABLE
                || item.getRequirementClass() == RequirementClass.AUTO_SOURCE
                || item.getRequirementClass() == RequirementClass.DERIVABLE
                || item.getRequirementClass() == RequirementClass.UNAVAILABLE_BLOCKER) {
            return false;
        }
        if (item.allowsOnly(FulfilmentMode.AUTOMATIC_SOURCE)
                || item.allowsOnly(FulfilmentMode.DERIVATION)) {
            return false;
        }
        if (item.getDataReadinessState() == DataReadinessState.READY_FOR_POLICY
                && (item.getCustomerFulfilmentState() == CustomerFulfilmentState.NOT_APPLICABLE
                || item.getCustomerFulfilmentState() == CustomerFulfilmentState.WAIVED)) {
            return false;
        }
        if (item.getRequirementClass() == RequirementClass.MANUAL_REVIEW
                && !item.allows(FulfilmentMode.DIRECT_INPUT)
                && !item.allows(FulfilmentMode.DOCUMENT_UPLOAD)) {
            return false;
        }
        return item.getRequirementClass() == RequirementClass.CUSTOMER_PROVIDED
                || item.allows(FulfilmentMode.DIRECT_INPUT)
                || item.allows(FulfilmentMode.DOCUMENT_UPLOAD);
    }

    static boolean isOutstanding(RequirementItemEntity item) {
        CustomerFulfilmentState f = item.getCustomerFulfilmentState();
        if (f == CustomerFulfilmentState.PROVIDED
                || f == CustomerFulfilmentState.WAIVED
                || f == CustomerFulfilmentState.NOT_APPLICABLE) {
            return false;
        }
        return f == CustomerFulfilmentState.REQUIRED
                || f == CustomerFulfilmentState.REQUESTED
                || f == CustomerFulfilmentState.REUPLOAD_REQUIRED;
    }

    private boolean isProvidedOrProcessingDisplay(RequirementItemEntity item) {
        return item.getCustomerFulfilmentState() == CustomerFulfilmentState.PROVIDED
                || item.getCustomerFulfilmentState() == CustomerFulfilmentState.REUPLOAD_REQUIRED;
    }

    private boolean isExtractionFailed(RequirementItemEntity item) {
        if (item.getSourceHints() != null
                && "EXTRACTION_FAILED".equals(String.valueOf(item.getSourceHints().get("documentOutcome")))) {
            return true;
        }
        return item.getCustomerFulfilmentState() == CustomerFulfilmentState.PROVIDED
                && item.getDataReadinessState() == DataReadinessState.FAILED
                && !"DOCUMENT_REJECTED".equals(item.getSourceHints() != null
                ? String.valueOf(item.getSourceHints().get("documentOutcome")) : null);
    }

    private String documentGroup(RequirementItemEntity item) {
        if (item.getSourceHints() == null) return null;
        Object g = item.getSourceHints().get("pendingDocumentGroup");
        if (g == null) g = item.getSourceHints().get("documentRequirement");
        return g != null ? String.valueOf(g) : null;
    }

    private String displayGroupKey(RequirementItemEntity item) {
        String g = documentGroup(item);
        return g != null ? "doc:" + g : "item:" + item.getItemKey();
    }

    private FulfilmentMode preferredMode(RequirementItemEntity item) {
        if (item.getSourceHints() != null && item.getSourceHints().get("chosenMode") != null) {
            try {
                return FulfilmentMode.valueOf(String.valueOf(item.getSourceHints().get("chosenMode")));
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        if (item.getSourceHints() != null && item.getSourceHints().get("preferredMode") != null) {
            try {
                return FulfilmentMode.valueOf(String.valueOf(item.getSourceHints().get("preferredMode")));
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        if (item.allows(FulfilmentMode.DOCUMENT_UPLOAD) && !item.allows(FulfilmentMode.DIRECT_INPUT)) {
            return FulfilmentMode.DOCUMENT_UPLOAD;
        }
        if (item.allows(FulfilmentMode.DIRECT_INPUT)) {
            return FulfilmentMode.DIRECT_INPUT;
        }
        if (item.allows(FulfilmentMode.DOCUMENT_UPLOAD)) {
            return FulfilmentMode.DOCUMENT_UPLOAD;
        }
        return null;
    }

    private String businessLabel(RequirementItemEntity item, CanonicalParameterRegistry registry) {
        if (item.getBusinessName() != null && !item.getBusinessName().isBlank()
                && !item.getBusinessName().equals(item.getCanonicalParameterId())) {
            return item.getBusinessName();
        }
        if (item.getCanonicalParameterId() != null) {
            Optional<CanonicalParameterDefinition> def = registry.findById(item.getCanonicalParameterId());
            if (def.isPresent() && def.get().businessName() != null && !def.get().businessName().isBlank()) {
                return def.get().businessName();
            }
        }
        String id = item.getCanonicalParameterId() != null ? item.getCanonicalParameterId() : item.getItemKey();
        if (id == null) return "Additional information";
        int dot = id.lastIndexOf('.');
        String leaf = dot >= 0 ? id.substring(dot + 1) : id;
        return leaf.replace('_', ' ');
    }

    private String whyNeededBusiness(RequirementItemEntity item, CanonicalParameterRegistry registry) {
        if (item.getCanonicalParameterId() != null) {
            Optional<CanonicalParameterDefinition> def = registry.findById(item.getCanonicalParameterId());
            if (def.isPresent() && def.get().calculationSummary() != null
                    && !def.get().calculationSummary().isBlank()) {
                return def.get().calculationSummary();
            }
        }
        return "This helps us complete your credit assessment.";
    }

    private CustomerRequirementDtos.FieldMeta fieldMeta(
            RequirementItemEntity item,
            CanonicalParameterRegistry registry,
            String label) {
        String datatype = "TEXT";
        String unit = null;
        String help = null;
        String inputType = "text";
        if (item.getCanonicalParameterId() != null) {
            Optional<CanonicalParameterDefinition> def = registry.findById(item.getCanonicalParameterId());
            if (def.isPresent()) {
                unit = def.get().unit();
                help = def.get().calculationSummary();
                if (def.get().capability() != null && def.get().capability().schema() != null) {
                    datatype = def.get().capability().schema();
                }
                if (unit != null && (unit.equalsIgnoreCase("INR") || unit.equalsIgnoreCase("MONTHS")
                        || unit.equalsIgnoreCase("SCORE") || "NUMBER".equalsIgnoreCase(datatype)
                        || datatype.contains("INT") || datatype.contains("DECIMAL"))) {
                    inputType = "number";
                }
                if ("BOOLEAN".equalsIgnoreCase(datatype) || "FLAG".equalsIgnoreCase(datatype)) {
                    inputType = "boolean";
                }
            }
        }
        return new CustomerRequirementDtos.FieldMeta(
                label, help, datatype, unit, List.of(), item.isRequired(), inputType);
    }

    private Map<String, Object> draftFrom(RequirementItemEntity item) {
        Map<String, Object> draft = new LinkedHashMap<>();
        if (item.getSourceHints() != null && item.getSourceHints().get("draftValue") != null) {
            draft.put("value", item.getSourceHints().get("draftValue"));
        }
        if (item.getEvidenceRef() != null && item.getCustomerFulfilmentState() == CustomerFulfilmentState.PROVIDED) {
            draft.put("valueRef", item.getEvidenceRef());
        }
        return draft;
    }

    private DataReadinessState worstReadiness(List<RequirementItemEntity> items) {
        DataReadinessState worst = DataReadinessState.READY_FOR_POLICY;
        int rank = 99;
        for (RequirementItemEntity i : items) {
            int r = readinessRank(i.getDataReadinessState());
            if (r < rank) {
                rank = r;
                worst = i.getDataReadinessState();
            }
        }
        return worst;
    }

    private int readinessRank(DataReadinessState s) {
        if (s == null) return 0;
        return switch (s) {
            case FAILED -> 0;
            case NOT_AVAILABLE -> 1;
            case DATA_INSUFFICIENT -> 2;
            case PROCESSING -> 3;
            case EXTRACTED -> 4;
            case VERIFIED -> 5;
            case READY_FOR_POLICY -> 6;
        };
    }

    private String customerStatusLabel(
            CustomerFulfilmentState fulfilment,
            DataReadinessState readiness,
            boolean reupload,
            boolean extractionFailed) {
        if (reupload) return "Please upload again";
        if (fulfilment == CustomerFulfilmentState.PROVIDED) {
            if (extractionFailed) return "Uploaded — we're reviewing it";
            if (readiness == DataReadinessState.PROCESSING
                    || readiness == DataReadinessState.EXTRACTED
                    || readiness == DataReadinessState.VERIFIED) {
                return "Uploaded";
            }
            if (readiness == DataReadinessState.READY_FOR_POLICY) {
                return "Completed";
            }
            return "Provided";
        }
        if (fulfilment == CustomerFulfilmentState.REQUESTED || fulfilment == CustomerFulfilmentState.REQUIRED) {
            return "Action needed";
        }
        return fulfilment != null ? fulfilment.name() : "";
    }

    private String processingLabel(
            CustomerFulfilmentState fulfilment,
            DataReadinessState readiness,
            boolean extractionFailed) {
        if (fulfilment != CustomerFulfilmentState.PROVIDED) {
            return null;
        }
        if (extractionFailed) {
            return "Processing… (manual review may be needed)";
        }
        if (readiness == DataReadinessState.PROCESSING
                || readiness == DataReadinessState.EXTRACTED
                || readiness == DataReadinessState.VERIFIED) {
            return "Processing…";
        }
        return null;
    }

    private String humanDocumentTitle(String group) {
        if (group == null) return "Supporting document";
        return switch (group.toUpperCase(Locale.ROOT)) {
            case "FINANCIAL_STATEMENTS" -> "Financial Statements";
            case "BANK_STATEMENT", "BANK_STATEMENT_DOCUMENT" -> "Bank Statement";
            case "ITR_RETURN" -> "Income Tax Return";
            case "GST_RETURN" -> "GST Return";
            default -> group.replace('_', ' ');
        };
    }

    private String sectionForDocument(String group) {
        if (group == null) return "Documents";
        String g = group.toUpperCase(Locale.ROOT);
        if (g.contains("KYC") || g.contains("AADHAAR") || g.contains("PAN")) return "KYC documents";
        if (g.contains("FINANCIAL") || g.contains("ITR") || g.contains("GST") || g.contains("BANK")) {
            return "Documents";
        }
        return "Documents";
    }

    private String sectionForItem(RequirementItemEntity item) {
        String id = item.getCanonicalParameterId() != null ? item.getCanonicalParameterId() : item.getItemKey();
        if (id == null) return "Additional information";
        String lower = id.toLowerCase(Locale.ROOT);
        if (lower.startsWith("application.") || lower.startsWith("business.") || lower.startsWith("product.")) {
            return "About your business";
        }
        if (lower.startsWith("financial.") || lower.startsWith("itr.")) {
            return "Financial information";
        }
        if (lower.startsWith("kyc.")) {
            return "KYC documents";
        }
        if (documentGroup(item) != null) {
            return "Documents";
        }
        return "Additional information";
    }

    private List<RequirementItemEntity> safeItems(RequirementPlanEntity plan) {
        return plan.getItems() != null ? plan.getItems() : List.of();
    }
}
