package com.los.core.creditintelligence.aiunderwriter.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ci_ai_review")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiAiReview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "suggestion_id", nullable = false)
    private UUID suggestionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "action", nullable = false, length = 40)
    private String action;

    @Column(name = "feedback_code", length = 40)
    private String feedbackCode;

    @Column(name = "edited_content", columnDefinition = "text")
    private String editedContent;

    @Column(name = "reviewer", length = 120)
    private String reviewer;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
