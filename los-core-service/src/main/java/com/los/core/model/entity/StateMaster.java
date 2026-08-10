package com.los.core.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "state_master")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StateMaster {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "state_code", nullable = false, unique = true, length = 20)
    private String stateCode;

    @Column(name = "state_name", nullable = false, unique = true, length = 120)
    private String stateName;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
