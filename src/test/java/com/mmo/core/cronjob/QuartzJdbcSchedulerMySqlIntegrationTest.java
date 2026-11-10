package com.mmo.core.cronjob;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.TestInstance;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Date;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "MMO_MYSQL_TEST_URL", matches = "^jdbc:mysql:.*$")
class QuartzJdbcSchedulerMySqlIntegrationTest {

    private DataSource dataSource;
    private SchedulerFactoryBean activeFactory;

    @BeforeAll
    void initializeQuartzSchema() {
        dataSource = new DriverManagerDataSource(
                requiredEnvironmentValue("MMO_MYSQL_TEST_URL"),
                requiredEnvironmentValue("MMO_MYSQL_TEST_USERNAME"),
                requiredEnvironmentValue("MMO_MYSQL_TEST_PASSWORD"));
        ((DriverManagerDataSource) dataSource).setDriverClassName("com.mysql.cj.jdbc.Driver");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        if (jdbcTemplate.queryForList("SHOW TABLES LIKE 'QRTZ_JOB_DETAILS'").isEmpty()) {
            ResourceDatabasePopulator schema = new ResourceDatabasePopulator(
                    new ClassPathResource("org/quartz/impl/jdbcjobstore/tables_mysql_innodb.sql"));
            schema.setCommentPrefixes("--", "#");
            schema.execute(dataSource);
        }
    }

    @AfterEach
    void shutdownScheduler() throws Exception {
        if (activeFactory != null) {
            activeFactory.destroy();
            activeFactory = null;
        }
    }

    @Test
    void shouldRestoreJobDetailAndCronTriggerAfterSchedulerRestart() throws Exception {
        String group = "workflow-restart-" + UUID.randomUUID();
        JobKey jobKey = new JobKey("durable-workflow", group);
        String cronExpression = "0 0 0 1 1 ? 2099";

        Scheduler firstScheduler = startScheduler();
        JobDetail jobDetail = JobBuilder.newJob(PersistedProbeJob.class)
                .withIdentity(jobKey)
                .requestRecovery(true)
                .build();
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("durable-workflow-trigger", group)
                .forJob(jobKey)
                .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression)
                        .withMisfireHandlingInstructionDoNothing())
                .build();

        firstScheduler.scheduleJob(jobDetail, trigger);
        assertTrue(firstScheduler.checkExists(jobKey));
        assertEquals(cronExpression,
                ((CronTrigger) firstScheduler.getTrigger(trigger.getKey())).getCronExpression());

        activeFactory.destroy();
        activeFactory = null;

        Scheduler restartedScheduler = startScheduler();

        assertTrue(restartedScheduler.checkExists(jobKey),
                "Quartz JobDetail must survive scheduler process restart");
        JobDetail restoredJob = restartedScheduler.getJobDetail(jobKey);
        assertNotNull(restoredJob);
        assertTrue(restoredJob.requestsRecovery(),
                "Recovery metadata must be persisted with the JobDetail");

        Trigger restoredTrigger = restartedScheduler.getTrigger(trigger.getKey());
        assertNotNull(restoredTrigger,
                "Quartz trigger must survive scheduler process restart");
        assertInstanceOf(CronTrigger.class, restoredTrigger);
        assertEquals(cronExpression, ((CronTrigger) restoredTrigger).getCronExpression());
        assertNotNull(restoredTrigger.getNextFireTime());
        assertTrue(restoredTrigger.getNextFireTime().after(Date.from(Instant.now())),
                "Manual-only trigger must remain scheduled in the future");

        restartedScheduler.deleteJob(jobKey);
    }

    private Scheduler startScheduler() throws Exception {
        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setAutoStartup(false);
        factory.setWaitForJobsToCompleteOnShutdown(true);
        factory.setQuartzProperties(quartzProperties());
        factory.afterPropertiesSet();
        activeFactory = factory;

        Scheduler scheduler = factory.getScheduler();
        scheduler.start();
        return scheduler;
    }

    private Properties quartzProperties() {
        Properties properties = new Properties();
        properties.setProperty("org.quartz.scheduler.instanceName", "mmo-quartz-mysql-integration");
        properties.setProperty("org.quartz.scheduler.instanceId", "AUTO");
        properties.setProperty("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
        properties.setProperty("org.quartz.threadPool.threadCount", "1");
        properties.setProperty("org.quartz.threadPool.threadPriority", "5");
        properties.setProperty("org.quartz.jobStore.driverDelegateClass",
                "org.quartz.impl.jdbcjobstore.StdJDBCDelegate");
        properties.setProperty("org.quartz.jobStore.tablePrefix", "QRTZ_");
        properties.setProperty("org.quartz.jobStore.isClustered", "false");
        return properties;
    }

    private static String requiredEnvironmentValue(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Required MySQL integration-test environment variable is missing: " + name);
        }
        return value;
    }

    public static class PersistedProbeJob implements Job {
        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            // The test verifies persisted scheduler metadata; this job is never triggered.
        }
    }
}
