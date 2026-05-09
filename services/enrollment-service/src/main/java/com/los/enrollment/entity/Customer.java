package com.los.enrollment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 15)
    private String mobile;

    @Column(unique = true, length = 255)
    private String email;

    @Column(nullable = false)
    @Builder.Default
    private boolean mobileVerified = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean consentGiven = false;

    private Instant consentTimestamp;

    @Column(length = 45)
    private String consentIpAddress;

    @Column(length = 200)
    private String consentDeviceId;

    @Column(length = 100)
    private String consentOtpSessionId;

    @Column(length = 10, unique = true)
    private String panNumber;

    @Column(length = 12, unique = true)
    private String aadhaarHash;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
