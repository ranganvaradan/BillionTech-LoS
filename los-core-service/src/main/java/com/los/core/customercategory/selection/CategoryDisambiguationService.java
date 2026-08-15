package com.los.core.customercategory.selection;

import com.los.core.customercategory.CustomerCategoryEntity;
import com.los.core.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Progressive disambiguation over eligible Categories using SAFE catalogue questions.
 * Reuses intake-style keys — not a second questionnaire engine.
 */
@Service
@RequiredArgsConstructor
public class CategoryDisambiguationService {

    private final ApplicationCategoryDisambiguationAnswerRepository answerRepository;

    @Transactional(readOnly = true)
    public CategorySelectionDtos.DisambiguationQuestionView nextQuestion(
            UUID applicationId,
            List<CustomerCategoryEntity> candidates,
            Map<String, String> knownFacts) {

        if (candidates == null || candidates.size() <= 1) {
            return null;
        }

        Set<String> answered = answerRepository.findByApplicationIdOrderByCreatedAtAsc(applicationId).stream()
                .map(ApplicationCategoryDisambiguationAnswerEntity::getQuestionId)
                .collect(Collectors.toSet());

        CategorySelectionDtos.DisambiguationQuestionView best = null;
        int bestScore = -1;

        for (SafeDisambiguationCatalogue.QuestionDef q : SafeDisambiguationCatalogue.allSafe()) {
            if (!q.safeForCategoryDisambiguation()) {
                continue;
            }
            if (answered.contains(q.questionId())) {
                continue;
            }
            if (knownFacts != null && knownFacts.containsKey(q.attributeKey())
                    && knownFacts.get(q.attributeKey()) != null
                    && !knownFacts.get(q.attributeKey()).isBlank()) {
                continue; // already known — do not re-ask
            }

            // Categories must declare attribute values for this question to be useful
            int withAttr = 0;
            for (CustomerCategoryEntity c : candidates) {
                List<String> vals = CategoryPropositionConfig.disambiguationAttributes(c).get(q.attributeKey());
                if (vals != null && !vals.isEmpty()) {
                    withAttr++;
                }
            }
            if (withAttr < 2) {
                continue; // cannot partition
            }

            List<CategorySelectionDtos.AnswerOptionView> options = new ArrayList<>();
            int distinctPartitions = 0;
            for (SafeDisambiguationCatalogue.OptionDef opt : q.options()) {
                List<UUID> retains = new ArrayList<>();
                for (CustomerCategoryEntity c : candidates) {
                    List<String> accepted = CategoryPropositionConfig.disambiguationAttributes(c)
                            .getOrDefault(q.attributeKey(), List.of());
                    if (SafeDisambiguationCatalogue.answerRetains(q.attributeKey(), opt.value(), accepted)) {
                        retains.add(c.getId());
                    }
                }
                if (!retains.isEmpty() && retains.size() < candidates.size()) {
                    distinctPartitions++;
                }
                options.add(new CategorySelectionDtos.AnswerOptionView(opt.value(), opt.label(), List.copyOf(retains)));
            }

            // Partition score: prefer questions that split the set (not all-or-nothing)
            int score = distinctPartitions * 10 + withAttr;
            // Prefer intake-reusable keys
            if (q.intakeFieldKey() != null) {
                score += 5;
            }
            if (score > bestScore) {
                bestScore = score;
                best = new CategorySelectionDtos.DisambiguationQuestionView(
                        q.questionId(),
                        q.prompt(),
                        q.attributeKey(),
                        q.intakeFieldKey(),
                        true,
                        "SAFE_FOR_CATEGORY_DISAMBIGUATION — proposition/journey characteristic; not a credit threshold",
                        List.copyOf(options));
            }
        }
        return best;
    }

    @Transactional
    public List<CustomerCategoryEntity> applyAnswer(
            UUID applicationId,
            List<CustomerCategoryEntity> candidates,
            String questionId,
            String answerValue,
            String actor,
            String actorRole) {

        SafeDisambiguationCatalogue.QuestionDef q = SafeDisambiguationCatalogue.get(questionId);
        if (q == null || !q.safeForCategoryDisambiguation()) {
            throw new BusinessRuleException(
                    "Question is not a safe Category disambiguation question: " + questionId,
                    "UNSAFE_DISAMBIGUATION_QUESTION", "category-disambiguation", Map.of());
        }
        if (answerValue == null || answerValue.isBlank()) {
            throw new BusinessRuleException("answerValue required", "ANSWER_REQUIRED", "category-disambiguation", null);
        }

        List<String> before = candidates.stream().map(c -> c.getId().toString()).toList();
        List<CustomerCategoryEntity> after = new ArrayList<>();
        for (CustomerCategoryEntity c : candidates) {
            List<String> accepted = CategoryPropositionConfig.disambiguationAttributes(c)
                    .getOrDefault(q.attributeKey(), List.of());
            if (SafeDisambiguationCatalogue.answerRetains(q.attributeKey(), answerValue, accepted)) {
                after.add(c);
            }
        }

        ApplicationCategoryDisambiguationAnswerEntity row = answerRepository
                .findByApplicationIdAndQuestionId(applicationId, questionId)
                .orElseGet(() -> ApplicationCategoryDisambiguationAnswerEntity.builder()
                        .applicationId(applicationId)
                        .questionId(questionId)
                        .build());
        row.setAnswerValue(answerValue.trim().toUpperCase());
        row.setActor(actor);
        row.setActorRole(actorRole);
        row.setCandidateBefore(new ArrayList<>(before));
        row.setCandidateAfter(after.stream().map(c -> c.getId().toString()).collect(Collectors.toCollection(ArrayList::new)));
        answerRepository.save(row);

        return after;
    }

    @Transactional(readOnly = true)
    public Map<String, String> priorAnswers(UUID applicationId) {
        Map<String, String> m = new LinkedHashMap<>();
        for (ApplicationCategoryDisambiguationAnswerEntity a :
                answerRepository.findByApplicationIdOrderByCreatedAtAsc(applicationId)) {
            SafeDisambiguationCatalogue.QuestionDef q = SafeDisambiguationCatalogue.get(a.getQuestionId());
            String key = q != null ? q.attributeKey() : a.getQuestionId();
            m.put(key, a.getAnswerValue());
        }
        return m;
    }

    @Transactional(readOnly = true)
    public List<ApplicationCategoryDisambiguationAnswerEntity> listAnswers(UUID applicationId) {
        return answerRepository.findByApplicationIdOrderByCreatedAtAsc(applicationId);
    }
}
