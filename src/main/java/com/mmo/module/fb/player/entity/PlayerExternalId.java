package com.mmo.module.fb.player.entity;

import com.mmo.core.entity.AbstractEntity;
import com.mmo.module.fb.ingest.enums.Provider;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "fb_player_external_ids",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_fb_player_provider_entity", columnNames = {"player_id", "provider"}),
                @UniqueConstraint(name = "uk_fb_player_provider_external", columnNames = {"provider", "external_id"})
        },
        indexes = @Index(name = "idx_player_external_provider", columnList = "provider"))
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PlayerExternalId extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private Provider provider;

    @Column(name = "external_id", nullable = false, length = 128)
    private String externalId;
}
