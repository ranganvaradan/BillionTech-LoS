package com.los.core.creditintelligence.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ci_source_artifact")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiSourceArtifact {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "source_record_id", nullable = false)
    private UUID sourceRecordId;

    @Column(name = "content_reference", nullable = false, length = 500)
    private String contentReference;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "checksum", nullable = false, length = 128)
    private String checksum;

    @Column(name = "immutable", nullable = false)
    @Builder.Default
    private boolean immutable = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
