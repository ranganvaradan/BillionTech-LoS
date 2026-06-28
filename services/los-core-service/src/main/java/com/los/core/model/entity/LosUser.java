package com.los.core.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "los_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LosUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(length = 32)
    private String mobile;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "password_reset_token", length = 64)
    private String passwordResetToken;

    @Column(name = "password_reset_token_expires_at")
    private Instant passwordResetTokenExpiresAt;

    /**
     * True when the account was created with a temporary password (e.g. a borrower auto-provisioned
     * from staff application submission) and the user must set a new password before continuing.
     */
    @Column(name = "password_reset_required", nullable = false)
    @Builder.Default
    private boolean passwordResetRequired = false;

    @Column(name = "primary_los_role", length = 50)
    private String primaryLosRole;
}
