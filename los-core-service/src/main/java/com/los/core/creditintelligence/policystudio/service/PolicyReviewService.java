package com.los.core.creditintelligence.policystudio.service;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.domain.AmbiguityResolutionAction;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyAmbiguity;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyParameter;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyReview;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyTestCase;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyVocabulary;
import com.los.core.creditintelligence.policystudio.domain.ReviewState;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class PolicyReviewService {

    public static final String ROLE_POLICY_AUTHOR = "POLICY_AUTHOR";
    public static final String ROLE_CREDIT_MANAGER = "CREDIT_MANAGER";
    public static final String ROLE_POLICY_CHECKER = "POLICY_CHECKER";

    private final CreditIntelligenceProperties properties;
    private final PolicyVocabularyService vocabularyService;
    private final PolicyStudioPersistenceService persistenceService;
    private final PolicyTestCaseGenerator testCaseGenerator;

    public PolicyReviewService(
            CreditIntelligenceProperties properties,
            PolicyVocabularyService vocabularyService,
            PolicyStudioPersistenceService persistenceService,
            PolicyTestCaseGenerator testCaseGenerator) {
        this.properties = properties != null ? properties : new CreditIntelligenceProperties();
        this.vocabularyService = vocabularyService != null ? vocabularyService : new PolicyVocabularyService();
        this.persistenceService = persistenceService != null ? persistenceService : new PolicyStudioPersistenceService();
        this.testCaseGenerator = testCaseGenerator != null ? testCaseGenerator : new PolicyTestCaseGenerator();
    }

    public PolicyReviewService(CreditIntelligenceProperties properties) {
        this(properties, null, null, null);
    }

    public PolicyReviewService() {
        this(new CreditIntelligenceProperties(), null, null, null);
    }

    public CiPolicyReview review(
            PolicyStudioSession session,
            String subjectType,
            UUID subjectId,
            String reviewer,
            String reviewerRole,
            String newState,
            Map<String, Object> humanChanges,
            String reason) {
        if (properties.getPolicyStudio().isRequireMakerChecker()
                && ReviewState.CHECKER_APPROVED.name().equals(newState)) {
            String author = findAuthor(session, subjectId);
            if (author != null && author.equalsIgnoreCase(reviewer)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Maker-checker: author cannot be final checker");
            }
            if (session.getAuthoringSession() != null
                    && reviewer.equalsIgnoreCase(session.getAuthoringSession().getAuthor())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Maker-checker: author cannot be final checker");
            }
        }
        Map<String, Object> original = snapshot(session, subjectType, subjectId);
        boolean material = humanChanges != null && !humanChanges.isEmpty();
        applyReviewStatus(session, subjectType, subjectId, newState, reviewer);

        if (material) {
            invalidateCheckerApproval(session, reviewer);
        }

        CiPolicyReview review = CiPolicyReview.builder()
                .id(UUID.randomUUID())
                .tenantId(session.getDocument().getTenantId())
                .policyDocumentId(session.getDocument().getId())
                .subjectType(subjectType)
                .subjectId(subjectId)
                .reviewState(newState)
                .originalProposal(original)
                .humanChanges(humanChanges == null ? Map.of() : humanChanges)
                .reviewer(reviewer)
                .reviewerRole(reviewerRole)
                .reason(reason)
                .build();
        session.getReviews().add(review);

        if (ReviewState.CHECKER_APPROVED.name().equals(newState) && session.getDraftPackage() != null) {
            session.getDraftPackage().setCheckerApprovedAt(Instant.now());
            session.getDraftPackage().setCheckerApprovedBy(reviewer);
            session.getDraftPackage().setInvalidatedByEdit(false);
        }
        persistenceService.saveSessionSnapshot(session);
        return review;
    }

    public void invalidateCheckerApproval(PolicyStudioSession session, String editedBy) {
        if (session.getDraftPackage() != null
                && session.getDraftPackage().getCheckerApprovedAt() != null) {
            session.getDraftPackage().setInvalidatedByEdit(true);
            session.getDraftPackage().setCheckerApprovedAt(null);
            Map<String, Object> meta = new LinkedHashMap<>(session.getDraftPackage().getContent() == null
                    ? Map.of() : session.getDraftPackage().getContent());
            meta.put("checkerInvalidatedBy", editedBy);
            meta.put("checkerInvalidatedAt", Instant.now().toString());
        }
        session.getReviews().add(CiPolicyReview.builder()
                .id(UUID.randomUUID())
                .tenantId(session.getDocument().getTenantId())
                .policyDocumentId(session.getDocument().getId())
                .subjectType("DRAFT_PACKAGE")
                .subjectId(session.getDraftPackage() == null ? session.getDocument().getId()
                        : session.getDraftPackage().getId())
                .reviewState("CHECKER_INVALIDATED")
                .originalProposal(Map.of())
                .humanChanges(Map.of("reason", "material_edit"))
                .reviewer(editedBy)
                .reviewerRole(ROLE_POLICY_AUTHOR)
                .reason("Material edit invalidated checker approval")
                .build());
    }

    public CiPolicyAmbiguityResolveResult resolveAmbiguity(
            PolicyStudioSession session, UUID ambiguityId, String resolvedOption,
            String resolvedBy, String notes) {
        return resolveAmbiguity(session, ambiguityId, AmbiguityResolutionAction.SELECT_CANDIDATE,
                resolvedOption, resolvedBy, notes, Map.of());
    }

    public CiPolicyAmbiguityResolveResult resolveAmbiguity(
            PolicyStudioSession session,
            UUID ambiguityId,
            AmbiguityResolutionAction action,
            String resolvedOption,
            String resolvedBy,
            String notes,
            Map<String, Object> payload) {
        var amb = session.ambiguityById(ambiguityId);
        if (amb == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ambiguity not found");
        }
        appendPreviousResolution(amb);

        AmbiguityResolutionAction act = action == null ? AmbiguityResolutionAction.SELECT_CANDIDATE : action;
        Map<String, Object> resultExtras = new LinkedHashMap<>();

        switch (act) {
            case SELECT_CANDIDATE -> {
                amb.setResolutionStatus("RESOLVED");
                amb.setResolvedOption(resolvedOption);
            }
            case CREATE_NEW_METRIC -> {
                // Intent only — never claim a measure is implemented without an executable definition.
                amb.setResolutionStatus("CLARIFICATION_REQUESTED");
                amb.setResolvedOption(resolvedOption == null ? "CREATE_NEW_METRIC_REQUESTED" : resolvedOption);
                resultExtras.put("metricCreated", false);
                resultExtras.put("metricRequested", true);
                resultExtras.put("requiresBusinessMeasureDesigner", true);
                resultExtras.put("message",
                        "Business measure requested — open Data Readiness → Define Business Measure. "
                                + "metricCreated remains false until an executable, governed measure exists.");
            }
            case CREATE_POLICY_PARAMETER -> {
                CiPolicyParameter param = CiPolicyParameter.builder()
                        .id(UUID.randomUUID())
                        .tenantId(session.getDocument().getTenantId())
                        .productScope(session.getDocument().getProductScope())
                        .code("PROPOSED_EDI")
                        .displayName("Proposed EDI")
                        .description("POLICY_PARAMETER_REF created from EDI ambiguity; vocabulary did not resolve")
                        .valueType("DECIMAL")
                        .unit("INR")
                        .source("POLICY_STUDIO")
                        .required(true)
                        .status("ACTIVE")
                        .version(1)
                        .metadata(Map.of("fromAmbiguityId", amb.getId().toString()))
                        .build();
                persistenceService.saveParameter(session, param);
                amb.setResolutionStatus("RESOLVED");
                amb.setResolvedOption("POLICY_PARAMETER:PROPOSED_EDI");
                resultExtras.put("parameterCode", "PROPOSED_EDI");
            }
            case CREATE_VOCABULARY_TERM -> {
                String term = payload.get("term") != null ? String.valueOf(payload.get("term"))
                        : (amb.getPhrase() == null ? "TERM" : amb.getPhrase());
                String path = payload.get("canonicalPath") != null
                        ? String.valueOf(payload.get("canonicalPath")) : resolvedOption;
                String scope = payload.get("scopeLevel") != null
                        ? String.valueOf(payload.get("scopeLevel")) : "TENANT";
                CiPolicyVocabulary v = vocabularyService.approveTerm(
                        session.getDocument().getTenantId(),
                        scope,
                        payload.get("productCode") == null ? null : String.valueOf(payload.get("productCode")),
                        term,
                        payload.get("canonicalMeaning") == null ? notes : String.valueOf(payload.get("canonicalMeaning")),
                        path,
                        payload.get("objectType") == null ? null : String.valueOf(payload.get("objectType")),
                        resolvedBy,
                        payload.get("previouslyApprovedNote") == null ? null
                                : String.valueOf(payload.get("previouslyApprovedNote")));
                session.getVocabulary().add(v);
                amb.setResolutionStatus("RESOLVED");
                amb.setResolvedOption("VOCAB:" + term + "→" + path);
                resultExtras.put("vocabularyId", v.getId().toString());
                resultExtras.put("vocabularyVersion", v.getVersion());
            }
            case MARK_NOT_APPLICABLE -> {
                amb.setResolutionStatus("NOT_APPLICABLE");
                amb.setResolvedOption(resolvedOption == null ? "N/A" : resolvedOption);
            }
            case REJECT_INTERPRETATION -> {
                amb.setResolutionStatus("REJECTED");
                amb.setResolvedOption(resolvedOption);
            }
            case EDIT_INTERPRETATION -> {
                amb.setResolutionStatus("RESOLVED");
                amb.setResolvedOption(resolvedOption);
                resultExtras.put("editedInterpretation", payload);
                invalidateCheckerApproval(session, resolvedBy);
            }
            case REQUEST_CLARIFICATION -> {
                amb.setResolutionStatus("CLARIFICATION_REQUESTED");
                amb.setResolvedOption(resolvedOption == null ? "CUSTOMER_CONFIRMATION_REQUIRED" : resolvedOption);
                resultExtras.put("placeholder", "CUSTOMER_CONFIRMATION_REQUIRED");
            }
        }

        // Exactly-100 boundary: regenerate inward-return tests after human selection
        if (amb.getPhrase() != null && amb.getPhrase().toLowerCase(Locale.ROOT).contains("100")
                && ("RESOLVED".equals(amb.getResolutionStatus()))) {
            regenerateInwardReturnTests(session, resolvedOption);
            resultExtras.put("inwardReturnTestsRegenerated", true);
        }

        amb.setResolvedBy(resolvedBy);
        amb.setResolvedAt(Instant.now());
        amb.setResolutionNotes(notes);
        Map<String, Object> meta = new LinkedHashMap<>(amb.getMetadata() == null ? Map.of() : amb.getMetadata());
        meta.put("action", act.name());
        meta.putAll(resultExtras);
        amb.setMetadata(meta);

        persistenceService.saveSessionSnapshot(session);
        return new CiPolicyAmbiguityResolveResult(amb.getId(), amb.getResolutionStatus(), amb.getResolvedOption(), act.name(), resultExtras);
    }

    private void regenerateInwardReturnTests(PolicyStudioSession session, String selection) {
        session.getTestCases().removeIf(t ->
                t.getName() != null && t.getName().toUpperCase(Locale.ROOT).contains("INWARD")
                        && t.getName().toUpperCase(Locale.ROOT).contains("100"));
        var rule = session.getRuleCandidates().stream()
                .filter(r -> "BANK_INWARD_RETURN_BRANCHED_100".equals(r.getSystemRuleId()))
                .findFirst().orElse(null);
        if (rule == null) {
            return;
        }
        String mode = selection == null ? "ASK_CUSTOMER" : selection;
        List<CiPolicyTestCase> generated = new ArrayList<>();
        generated.add(CiPolicyTestCase.builder()
                .id(UUID.randomUUID())
                .clauseId(rule.getClauseId())
                .ruleCandidateId(rule.getId())
                .name("INWARD_RETURN_EXACTLY_100_" + mode)
                .inputFacts(Map.of("boundarySelection", mode))
                .inputMetrics(Map.of("banking.transaction_count_3m", 100,
                        "banking.inward_cheque_return_count_3m", 1))
                .expectedOutcome(mode.toUpperCase(Locale.ROOT).contains("ASK")
                        ? "DATA_INSUFFICIENT" : "PASS")
                .boundaryCase(true)
                .aiExpectedOutcome("DATA_INSUFFICIENT")
                .generatedBy("SYSTEM_AFTER_BOUNDARY_RESOLUTION")
                .reviewStatus(ReviewState.AI_DRAFTED.name())
                .metadata(Map.of("exactly100", mode))
                .build());
        session.getTestCases().addAll(generated);
    }

    private void appendPreviousResolution(CiPolicyAmbiguity amb) {
        List<Object> history = amb.getPreviousResolution() == null
                ? new ArrayList<>() : new ArrayList<>(amb.getPreviousResolution());
        Map<String, Object> prev = new LinkedHashMap<>();
        prev.put("resolutionStatus", amb.getResolutionStatus());
        prev.put("resolvedOption", amb.getResolvedOption());
        prev.put("resolvedBy", amb.getResolvedBy());
        prev.put("resolvedAt", amb.getResolvedAt() == null ? null : amb.getResolvedAt().toString());
        prev.put("resolutionNotes", amb.getResolutionNotes());
        prev.put("metadata", amb.getMetadata());
        history.add(prev);
        amb.setPreviousResolution(history);
    }

    private String findAuthor(PolicyStudioSession session, UUID subjectId) {
        for (CiPolicyReview r : session.getReviews()) {
            if (subjectId.equals(r.getSubjectId())
                    && (ROLE_POLICY_AUTHOR.equals(r.getReviewerRole())
                    || ROLE_CREDIT_MANAGER.equals(r.getReviewerRole()))) {
                return r.getReviewer();
            }
        }
        return session.getDocument().getUploadedBy();
    }

    private void applyReviewStatus(PolicyStudioSession session, String subjectType, UUID subjectId,
                                   String state, String reviewer) {
        if ("RULE".equalsIgnoreCase(subjectType)) {
            var r = session.ruleById(subjectId);
            if (r != null) {
                r.setReviewStatus(state);
                r.setVersion(r.getVersion() == null ? 1L : r.getVersion() + 1);
            }
        } else if ("METRIC".equalsIgnoreCase(subjectType)) {
            var m = session.metricById(subjectId);
            if (m != null) {
                m.setReviewStatus(state);
            }
        } else if ("TEST".equalsIgnoreCase(subjectType)) {
            var t = session.testById(subjectId);
            if (t != null) {
                if (t.getAiExpectedOutcome() == null) {
                    t.setAiExpectedOutcome(t.getExpectedOutcome());
                }
                t.setReviewStatus(state);
                t.setReviewedBy(reviewer);
                if (ReviewState.CHECKER_APPROVED.name().equals(state)
                        || ReviewState.CREDIT_MANAGER_APPROVED.name().equals(state)
                        || "APPROVED".equals(state)) {
                    t.setApprovedAt(Instant.now());
                }
                t.setVersion(t.getVersion() == null ? 1L : t.getVersion() + 1);
            }
        }
    }

    private Map<String, Object> snapshot(PolicyStudioSession session, String subjectType, UUID subjectId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("subjectType", subjectType);
        m.put("subjectId", subjectId.toString());
        if ("RULE".equalsIgnoreCase(subjectType)) {
            var r = session.ruleById(subjectId);
            if (r != null) {
                m.put("systemRuleId", r.getSystemRuleId());
                m.put("expression", r.getExpression());
                m.put("reviewStatus", r.getReviewStatus());
            }
        }
        return m;
    }

    public record CiPolicyAmbiguityResolveResult(
            UUID id, String status, String resolvedOption, String action, Map<String, Object> extras) {
        public CiPolicyAmbiguityResolveResult(UUID id, String status, String resolvedOption) {
            this(id, status, resolvedOption, AmbiguityResolutionAction.SELECT_CANDIDATE.name(), Map.of());
        }
    }
}
