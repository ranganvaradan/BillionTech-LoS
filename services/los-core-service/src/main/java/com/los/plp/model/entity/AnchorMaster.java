package com.los.plp.model.entity;

import com.los.plp.model.enums.PlpSyncStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "anchor_masters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnchorMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 10)
    private String pan;

    @Column(length = 15)
    private String gstin;

    @Column(length = 255)
    private String email;

    @Column(length = 20)
    private String mobile;

    @Column(length = 500)
    private String address;

    @Column(name = "source_anchor_application_id")
    private UUID sourceAnchorApplicationId;

    @Column(name = "plp_anchor_id")
    private UUID plpAnchorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "plp_anchor_sync_status", nullable = false, length = 20)
    @Builder.Default
    private PlpSyncStatus plpAnchorSyncStatus = PlpSyncStatus.NOT_SYNCED;

    @Column(name = "plp_anchor_sync_error", length = 500)
    private String plpAnchorSyncError;

    @Column(name = "plp_anchor_synced_at")
    private Instant plpAnchorSyncedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
