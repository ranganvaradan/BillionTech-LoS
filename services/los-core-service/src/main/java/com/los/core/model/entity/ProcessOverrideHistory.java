package com.los.core.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "process_override_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessOverrideHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID applicationId;

    @Column(nullable = false, length = 80)
    private String processCode;

    @Column(nullable = false, length = 120)
    private String failureCode;

    @Column(length = 40)
    private String previousStatus;

    @Column(length = 40)
    private String newStatus;

    @Column(nullable = false, length = 1000)
    private String overrideReason;

    @Column(length = 2000)
    private String remarks;

    @Column(length = 200)
    private String approvalReference;

    private UUID overriddenBy;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant overriddenAt;
}
