package com.mmo.core.cronjob.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mmo.core.entity.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;

import java.io.Serial;
import java.time.ZonedDateTime;

@Entity
@Table(
        name = "cron_jobs",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"job_name", "job_group"})
        }
)
@NoArgsConstructor
@Getter
@Setter
@FieldNameConstants
public class CronJob extends AbstractEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "validation.constraints.NotBlank.message")
    @Column(nullable = false)
    private String jobName;

    @JsonIgnore
    @Column(nullable = false)
    private String jobGroup;

    @Enumerated(EnumType.STRING)
    private JobState jobStatus;

    @JsonIgnore
    @Column(nullable = false)
    private String jobClass;

    @Column(nullable = false)
    private String cronExpression;

    @Column
    private String description;

    @Column
    private ZonedDateTime lastExecutedTime;

    @Enumerated(EnumType.STRING)
    private JobCompleteState lastCompleteStatus;
}
