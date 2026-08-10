package com.los.plp.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "program_approval_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProgramApprovalConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "l1_role", nullable = false, length = 50)
    @Builder.Default
    private String l1Role = "CREDIT_OFFICER";

    @Column(name = "l2_role", nullable = false, length = 50)
    @Builder.Default
    private String l2Role = "CREDIT_MANAGER";

    @Column(name = "l1_user_id")
    private UUID l1UserId;

    @Column(name = "l2_user_id")
    private UUID l2UserId;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
