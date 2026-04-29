package com.billiontech.bankstatement.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "bank_parser_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankParserConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bank_code", nullable = false, unique = true)
    private String bankCode;

    @Column(name = "bank_name", nullable = false)
    private String bankName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "header_patterns", columnDefinition = "jsonb")
    private List<String> headerPatterns;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "date_formats", columnDefinition = "jsonb")
    private List<String> dateFormats;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "column_mappings", columnDefinition = "jsonb")
    private Map<String, Object> columnMappings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "narration_patterns", columnDefinition = "jsonb")
    private Map<String, Object> narrationPatterns;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detection_keywords", columnDefinition = "jsonb")
    private List<String> detectionKeywords;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "supported_formats", columnDefinition = "jsonb")
    private List<String> supportedFormats;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "version")
    @Builder.Default
    private Integer version = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
