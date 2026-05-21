package com.mmo.cronjob.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mmo.entity.AbstractEntity;
import jakarta.persistence.*;
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
