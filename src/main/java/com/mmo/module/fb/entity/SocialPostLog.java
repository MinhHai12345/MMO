package com.mmo.module.fb.entity;

import com.mmo.entity.AbstractEntity;
import com.mmo.module.fb.enums.TargetChannel;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "fb_social_post_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialPostLog extends AbstractEntity {

    @Column
    private Long matchId;

    @Column
    @Enumerated(EnumType.STRING)
    private TargetChannel channel;

    @Column
    private String externalPostId;

    @Column
    private Integer viewsCount;

    @Column
    private Integer likesCount;

    @Column
    private Integer sharesCount;

    @Column
    private Instant postedAt;
}
