package com.mmo.cronjob.job;

import com.mmo.cronjob.entity.CronJob;
import com.mmo.cronjob.entity.JobCompleteState;
import com.mmo.cronjob.service.CronJobService;
import jakarta.annotation.Resource;
import org.quartz.InterruptableJob;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.util.StopWatch;

public abstract class AbstractJob<T extends CronJob> extends QuartzJobBean implements InterruptableJob {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractJob.class);

    private boolean stop = false;

    @Resource
    private CronJobService cronJobService;

    @Override
    public void interrupt() {
        this.stop = true;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected final void executeInternal(final JobExecutionContext context) throws JobExecutionException {
        final String jobName = context.getJobDetail().getKey().getName();
        final String jobGroup = context.getJobDetail().getKey().getGroup();
        final T cronjob = (T) cronJobService.getJob(jobName, jobGroup);
        try {
            LOGGER.info("Starting [{}]", cronjob.getJobName());
            final StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            executeInternal(context, cronjob);
            cronjob.setLastCompleteStatus(JobCompleteState.SUCCESS);
            stopWatch.stop();
            LOGGER.info("Done [{}]", cronjob.getJobName());
            LOGGER.info("Execution time: {} ms", stopWatch.getTotalTimeMillis());
        } catch (Exception e) {
            cronjob.setLastCompleteStatus(JobCompleteState.ERROR);
            LOGGER.error("Error while executing {}", cronjob, e);
        }
    }

    /**
     * Execute internally.
     *
     * @param context JobExecutionContext
     * @param cronJob T
     */
    protected abstract void executeInternal(JobExecutionContext context, T cronJob);

    /**
     * If the cronjob is suggested to stop current execution.
     *
     * @return boolean
     */
    protected boolean shouldStop() {
        return this.stop;
    }

}
