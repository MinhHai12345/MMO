package com.mmo.module.fb.domain.entity;

import com.mmo.core.entity.AbstractEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.Lob;
import java.time.Instant;

@Entity
@Table(name = "fb_crawler_health")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CrawlerHealth extends AbstractEntity {

    @Column(name = "crawler_name", nullable = false)
    private String crawlerName;

    @Column(nullable = false)
    private String status;

    @Column(name = "last_run")
    private Instant lastRun;

    @Column(name = "last_success")
    private Instant lastSuccess;

    @Column(name = "error_count")
    @Builder.Default
    private Integer errorCount = 0;

    @Lob
    @Column(name = "last_error_message")
    private String lastErrorMessage;

    @Column(name = "success_rate")
    private String successRate;
}
