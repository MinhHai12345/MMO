package com.mmo.core.cronjob.job;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.entity.JobCompleteState;
import com.mmo.core.cronjob.service.CronJobService;
import jakarta.annotation.Resource;
import org.quartz.InterruptableJob;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.util.StopWatch;

public abstract class AbstractJob<T extends CronJob> extends QuartzJobBean implements InterruptableJob {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractJob.class);

    private volatile boolean stop;

    @Resource
    private CronJobService cronJobService;

    @Override
    public void interrupt() {
        stop = true;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected final void executeInternal(final JobExecutionContext context) {
        final String jobName = context.getJobDetail().getKey().getName();
        final String jobGroup = context.getJobDetail().getKey().getGroup();
        final StopWatch stopWatch = new StopWatch(jobName);
        stop = false;

        try {
            T cronJob = (T) cronJobService.getJob(jobName, jobGroup);
            LOGGER.info("Starting [{}]", jobName);
            stopWatch.start();
            executeInternal(context, cronJob);
            context.setResult(JobCompleteState.SUCCESS);
        } catch (Exception exception) {
            context.setResult(JobCompleteState.ERROR);
            LOGGER.error("Error while executing job [{}] in group [{}]", jobName, jobGroup, exception);
        } finally {
            if (stopWatch.isRunning()) {
                stopWatch.stop();
            }
            if (stopWatch.getTaskCount() > 0) {
                LOGGER.info("Finished [{}] in {} ms; stopRequested={}",
                        jobName, stopWatch.getTotalTimeMillis(), stop);
            }
        }
    }

    /**
     * Execute the business operation. Exceptions are recorded as ERROR in Quartz
     * execution context and logged here so the state listener can persist the result.
     */
    protected abstract void executeInternal(JobExecutionContext context, T cronJob);

    /** Return true after Quartz has requested this execution to stop. */
    protected boolean shouldStop() {
        return stop;
    }
}
