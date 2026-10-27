package com.mmo.module.fb.monetization.entity;

import com.mmo.core.entity.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "fb_campaign_links",
        uniqueConstraints = @UniqueConstraint(name = "uk_fb_campaign_link_code", columnNames = "campaign_code"),
        indexes = @Index(name = "idx_fb_campaign_link_publication", columnList = "publication_id"))
@Getter
@Setter
@NoArgsConstructor
public class CampaignLinkEntity extends AbstractEntity {
    @Column(name = "campaign_code", nullable = false, length = 100)
    private String campaignCode;

    @Column(name = "destination_url", nullable = false, length = 2048)
    private String destinationUrl;

    @Column(name = "publication_id")
    private Long publicationId;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
