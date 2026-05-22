package com.mmo.cronjob.service.impl;

import com.mmo.cronjob.entity.CronJob;
import com.mmo.cronjob.entity.JobState;
import com.mmo.cronjob.exception.ScheduleJobException;
import com.mmo.cronjob.job.JobScheduleCreator;
import com.mmo.cronjob.repository.CronJobRepository;
import com.mmo.cronjob.service.CronJobService;
import lombok.RequiredArgsConstructor;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Service
public class CronJobServiceImpl implements CronJobService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CronJobServiceImpl.class);
    private final Scheduler scheduler;
    private final ApplicationContext context;
    private final JobScheduleCreator scheduleCreator;
    private final CronJobRepository cronJobRepository;
    private static final String NO_JOB_MSG = "No job with id=%s found.";

    @Override
    public SchedulerMetaData getMetaData() throws SchedulerException {
        return scheduler.getMetaData();
    }

    @Transactional
    @Override
    public boolean deleteJob(final Long jobId) {
        final CronJob cronJob = cronJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException(String.format(NO_JOB_MSG, jobId)));
        try {
            cronJobRepository.delete(cronJob);
            LOGGER.info("Job {} was deleted.", cronJob.getJobName());
            return scheduler.deleteJob(new JobKey(cronJob.getJobName(), cronJob.getJobGroup()));
        } catch (SchedulerException e) {
            throw new ScheduleJobException(String.format("Failed to delete job %s", cronJob.getJobName()), e);
        }
    }

    @Transactional
    @Override
    public CronJob pauseJob(final Long jobId) {
        final CronJob cronJob = cronJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException(String.format(NO_JOB_MSG, jobId)));
        try {
            scheduler.pauseJob(new JobKey(cronJob.getJobName(), cronJob.getJobGroup()));
            cronJob.setJobStatus(JobState.PAUSED);
            cronJobRepository.save(cronJob);
            LOGGER.info("Job {} was paused.", cronJob.getJobName());
            return cronJob;
        } catch (SchedulerException e) {
            throw new ScheduleJobException(String.format(NO_JOB_MSG, cronJob.getJobName()), e);
        }
    }

    @Transactional
    @Override
    public CronJob resumeJob(final Long jobId) {
        final CronJob cronJob = cronJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException(String.format(NO_JOB_MSG, jobId)));
        try {
            scheduler.resumeJob(new JobKey(cronJob.getJobName(), cronJob.getJobGroup()));
            cronJob.setJobStatus(JobState.SCHEDULED);
            cronJobRepository.save(cronJob);
            LOGGER.info("Job {} was resumed.", cronJob.getJobName());
            return cronJob;
        } catch (SchedulerException e) {
            throw new ScheduleJobException(String.format("Failed to resume job %s", cronJob.getJobName()), e);
        }
    }

    @Override
    public CronJob stopJob(final Long jobId) {
        final CronJob cronJob = cronJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException(String.format(NO_JOB_MSG, jobId)));

        try {
            final boolean interrupted = scheduler.interrupt(new JobKey(cronJob.getJobName(), cronJob.getJobGroup()));
            if (interrupted) {
                cronJob.setJobStatus(JobState.SCHEDULED);
                cronJobRepository.save(cronJob);
                LOGGER.info("Job {} was stopped.", cronJob.getJobName());
            }
            return cronJob;
        } catch (UnableToInterruptJobException e) {
            throw new ScheduleJobException(String.format("Failed to stop job %s", cronJob.getJobName()), e);
        }
    }

    @Transactional
    @Override
    public CronJob startJobNow(final Long jobId) {
        final CronJob cronJob = cronJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException(String.format(NO_JOB_MSG, jobId)));

        try {
            scheduler.triggerJob(new JobKey(cronJob.getJobName(), cronJob.getJobGroup()));
            cronJob.setJobStatus(JobState.RUNNING);
            cronJobRepository.save(cronJob);
            LOGGER.info("Job {} is scheduled and started now.", cronJob.getJobName());
            return cronJob;
        } catch (SchedulerException e) {
            throw new ScheduleJobException(String.format("Failed to start job %s", cronJob.getJobName()), e);
        }
    }

    @Transactional
    @Override
    public void saveJob(final CronJob cronJob) {
        Long jobId = cronJob.getId();
        if (jobId != null) {
            final boolean existCronJob = cronJobRepository.existsById(jobId);
            if (existCronJob) {
                updateScheduleJob(cronJob);
                return;
            }
        }
        scheduleNewJob(cronJob);
    }


    @Override
    public void scheduleNewJob(final CronJob cronJob) {
        try {
            JobDetail jobDetail = JobBuilder
                    .newJob((Class<? extends QuartzJobBean>) Class.forName(cronJob.getJobClass()))
                    .withIdentity(cronJob.getJobName(), cronJob.getJobGroup()).build();

            if (!scheduler.checkExists(jobDetail.getKey())) {
                jobDetail = scheduleCreator.createJob(
                        (Class<? extends QuartzJobBean>) Class.forName(cronJob.getJobClass()), false, context,
                        cronJob.getJobName(), cronJob.getJobGroup());

                final Trigger trigger = scheduleCreator.createCronTrigger(cronJob.getJobName(), ZonedDateTime.now(),
                        cronJob.getCronExpression(), CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);
                scheduler.scheduleJob(jobDetail, trigger);
                cronJob.setJobStatus(JobState.SCHEDULED);
                cronJobRepository.save(cronJob);
            }
        } catch (SchedulerException e) {
            throw new ScheduleJobException(String.format("Failed to schedule job %s", cronJob.getJobName()), e);
        } catch (ClassNotFoundException e) {
            throw new ScheduleJobException(String.format("Invalid class %s for job %s", cronJob.getClass(),
                    cronJob.getJobName()), e);
        }
    }

    @Override
    public void updateScheduleJob(final CronJob cronJob) {
        try {
            final Trigger newTrigger = scheduleCreator.createCronTrigger(cronJob.getJobName(), ZonedDateTime.now(),
                    cronJob.getCronExpression(), CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);
            scheduler.rescheduleJob(TriggerKey.triggerKey(cronJob.getJobName()), newTrigger);
            cronJob.setJobStatus(JobState.SCHEDULED);
            cronJobRepository.save(cronJob);
        } catch (SchedulerException e) {
            throw new ScheduleJobException(String.format("Failed to reschedule job %s", cronJob.getJobName()), e);
        }
    }

    @Override
    public boolean unScheduleJob(final CronJob cronJob) {
        try {
            return scheduler.deleteJob(new JobKey(cronJob.getJobName(), cronJob.getJobGroup()));
        } catch (SchedulerException e) {
            throw new ScheduleJobException(String.format("Failed to unschedule job %s", cronJob.getJobName()), e);
        }
    }

    @Override
    public CronJob getJob(final String jobName, final String jobGroup) {
        return cronJobRepository.findByJobNameAndJobGroup(jobName, jobGroup).orElseThrow(() ->
                new RuntimeException(String.format("No job with name=%s and group=%s found.", jobName, jobGroup)));
    }
}
