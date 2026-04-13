package com.los.core.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID applicationId;

    @Column(nullable = false, length = 50)
    private String documentType;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false, length = 512)
    private String storageKey;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private long fileSize;

    @Column(length = 64)
    private String checksum;

    private UUID uploadedBy;

    @Column
    @Builder.Default
    private int versionNumber = 1;

    @Column
    private UUID previousVersionId;

    @Column
    @Builder.Default
    private boolean isLatest = true;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}
