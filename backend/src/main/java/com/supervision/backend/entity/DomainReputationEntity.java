package com.supervision.backend.entity;

import com.supervision.backend.domain.ClassificationCategory;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
    name = "domain_reputations",
    indexes = {
        @Index(name = "idx_domain", columnList = "domain", unique = true),
        @Index(name = "idx_category", columnList = "category"),
        @Index(name = "idx_updated_at", columnList = "last_verified_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DomainReputationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String domain;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClassificationCategory category;

    @Column(nullable = false)
    private Float confidence;

    @Column(name = "report_count", nullable = false)
    @Builder.Default
    private Integer reportCount = 1;

    @Column(nullable = false)
    private String source;

    @Column(name = "first_detected_at", nullable = false)
    @Builder.Default
    private Instant firstDetectedAt = Instant.now();

    @Column(name = "last_verified_at", nullable = false)
    @Builder.Default
    private Instant lastVerifiedAt = Instant.now();
}

