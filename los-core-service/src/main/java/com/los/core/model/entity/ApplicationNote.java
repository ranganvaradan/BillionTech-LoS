package com.los.core.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * BR-2.8: Application notes/comments system.
 */
@Entity
@Table(name = "application_notes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationNote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID applicationId;

    @Column(nullable = false)
    private UUID authorId;

    @Column(nullable = false, length = 100)
    private String authorName;

    @Column(nullable = false, length = 30)
    private String authorRole;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(length = 30)
    @Builder.Default
    private String noteType = "GENERAL"; // GENERAL, INTERNAL, SYSTEM, CREDIT, LEGAL

    @Column(nullable = false)
    @Builder.Default
    private boolean internal = false; // true = visible only to internal staff

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}
