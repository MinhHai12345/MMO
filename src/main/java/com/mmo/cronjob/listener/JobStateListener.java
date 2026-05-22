package com.mmo.cronjob.listener;

import com.mmo.cronjob.entity.CronJob;
import com.mmo.cronjob.entity.JobState;
import com.mmo.cronjob.repository.CronJobRepository;
import com.mmo.cronjob.service.CronJobService;
import com.mmo.utils.BeanUtils;
import lombok.RequiredArgsConstructor;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.listeners.JobListenerSupport;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
public class JobStateListener extends JobListenerSupport {

    @Override
    public String getName() {
        return "Job state listener";
    }

    @Override
    public void jobToBeExecuted(final JobExecutionContext context) {
        super.jobToBeExecuted(context);
        final JobKey jobKey = context.getJobDetail().getKey();
        final CronJob executingJob = BeanUtils.getBean(CronJobService.class).getJob(jobKey.getName(), jobKey.getGroup());
        executingJob.setJobStatus(JobState.RUNNING);
        BeanUtils.getBean(CronJobRepository.class).save(executingJob);
    }

    @Override
    public void jobWasExecuted(final JobExecutionContext context, final JobExecutionException jobException) {
        super.jobWasExecuted(context, jobException);
        final JobKey jobKey = context.getJobDetail().getKey();
        final CronJob executingJob = BeanUtils.getBean(CronJobService.class).getJob(jobKey.getName(), jobKey.getGroup());
        executingJob.setJobStatus(JobState.COMPLETE);
        executingJob.setLastExecutedTime(ZonedDateTime.now());
        BeanUtils.getBean(CronJobRepository.class).save(executingJob);
    }
}
