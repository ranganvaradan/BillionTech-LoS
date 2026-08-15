package com.los.core.customercategory.selection;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "application_category_disambiguation_answer")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationCategoryDisambiguationAnswerEntity {

    @Id
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "question_id", nullable = false, length = 80)
    private String questionId;

    @Column(name = "answer_value", nullable = false, length = 120)
    private String answerValue;

    @Column(length = 120)
    private String actor;

    @Column(name = "actor_role", length = 40)
    private String actorRole;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "candidate_before", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<String> candidateBefore = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "candidate_after", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<String> candidateAfter = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (candidateBefore == null) {
            candidateBefore = new ArrayList<>();
        }
        if (candidateAfter == null) {
            candidateAfter = new ArrayList<>();
        }
    }
}
