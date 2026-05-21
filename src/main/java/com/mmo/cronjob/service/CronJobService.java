package com.mmo.cronjob.service;

import com.mmo.cronjob.entity.CronJob;
import org.quartz.SchedulerException;
import org.quartz.SchedulerMetaData;

public interface CronJobService {

    /**
     * Get {@link SchedulerMetaData}.
     *
     * @return SchedulerMetaData
     */
    SchedulerMetaData getMetaData() throws SchedulerException;

    /**
     * Delete a job.
     *
     * @param jobId Long
     * @return boolean
     */
    boolean deleteJob(Long jobId);

    /**
     * Suggest stop a job's running execution immediately and pause the future executions until explicitly call {@link #resumeJob(Long)}.
     *
     * @param jobId Long
     * @return CronJob
     */
    CronJob pauseJob(Long jobId);

    /**
     * Resume a job.
     *
     * @param jobId Long
     * @return CronJob
     */
    CronJob resumeJob(Long jobId);

    /**
     * Start a job immediately.
     *
     * @param jobId Long
     * @return CronJob
     */
    CronJob startJobNow(Long jobId);

    /**
     * Save job
     *
     * @param schedulerJobInfo CronJob
     */
    void saveJob(CronJob schedulerJobInfo);

    /**
     * Schedule a new job.
     *
     * @param cronJob CronJob
     */
    void scheduleNewJob(CronJob cronJob);

    /**
     * Update a job schedule.
     *
     * @param cronJob CronJob
     */
    void updateScheduleJob(CronJob cronJob);

    /**
     * Unschedule a job.
     *
     * @param cronJob CronJob
     */
    boolean unScheduleJob(CronJob cronJob);

    /**
     * Get {@link CronJob} identified by its name and group.
     *
     * @param jobName  String
     * @param jobGroup String
     * @return CronJob
     */
    CronJob getJob(String jobName, String jobGroup);

    /**
     * Suggest stop a job's running execution immediately. But it has still been scheduled and will be executed
     * when the trigger gets fired next time.
     *
     * @param jobId Long
     * @return CronJob
     */
    CronJob stopJob(Long jobId);

}
