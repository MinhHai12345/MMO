package com.mmo.module.fb.match.entity;

import com.mmo.core.entity.AbstractEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "fb_bookmakers",
        uniqueConstraints = @UniqueConstraint(name = "uk_fb_bookmaker_name", columnNames = "name"),
        indexes = @Index(name = "idx_fb_bookmaker_active", columnList = "is_active"))
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Bookmaker extends AbstractEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 255)
    private String website;

    /**
     * BOOKMAKER or MARKET_AGGREGATE. Kept as a string for existing row compatibility;
     * canonical values are defined by the ingestion/domain contract.
     */
    @Column(name = "source_type", length = 24)
    @Builder.Default
    private String sourceType = "BOOKMAKER";

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Version
    @Column(name = "version")
    private Long version;

    @PrePersist
    @PreUpdate
    private void normalize() {
        if (name != null) name = name.trim();
        if (name == null || name.isEmpty()) {
            throw new IllegalStateException("Bookmaker name must not be blank");
        }
        if (sourceType == null || sourceType.isBlank()) {
            sourceType = "BOOKMAKER";
        } else {
            sourceType = sourceType.trim().toUpperCase(java.util.Locale.ROOT);
        }
        if (isActive == null) isActive = true;
    }
}
