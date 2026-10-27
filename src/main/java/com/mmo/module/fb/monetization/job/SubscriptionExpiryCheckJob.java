package com.mmo.module.fb.monetization.job;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import com.mmo.module.fb.monetization.entity.Subscriber;
import com.mmo.module.fb.monetization.repository.SubscriberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class SubscriptionExpiryCheckJob extends AbstractJob<CronJob> {

    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String EXPIRED_STATUS = "EXPIRED";
    private static final int EXPIRY_WARNING_DAYS = 3;
    private static final int BATCH_SIZE = 500;

    private final SubscriberRepository subscriberRepository;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        Instant now = Instant.now();
        Instant warningThreshold = now.plus(EXPIRY_WARNING_DAYS, ChronoUnit.DAYS);
        Long cursor = 0L;
        int scanned = 0;
        int expiringSoon = 0;
        int expired = 0;
        int failed = 0;

        log.info("Subscription expiry scan started: warningDays={}, batchSize={}",
                EXPIRY_WARNING_DAYS, BATCH_SIZE);

        while (!shouldStop()) {
            List<Subscriber> batch = subscriberRepository
                    .findBySubscriptionStatusAndIdGreaterThanOrderByIdAsc(
                            ACTIVE_STATUS, cursor, PageRequest.of(0, BATCH_SIZE));
            if (batch.isEmpty()) {
                break;
            }

            for (Subscriber subscriber : batch) {
                cursor = subscriber.getId();
                scanned++;

                if (shouldStop()) {
                    break;
                }

                Instant endDate = subscriber.getEndDate();
                if (endDate == null) {
                    log.warn("Active subscription has no endDate; subscriberId={}", subscriber.getId());
                    continue;
                }

                if (!endDate.isAfter(now)) {
                    try {
                        subscriber.setSubscriptionStatus(EXPIRED_STATUS);
                        subscriber.setAutoRenew(false);
                        subscriberRepository.save(subscriber);
                        expired++;
                    } catch (RuntimeException exception) {
                        failed++;
                        log.error("Failed to expire subscription subscriberId={}", subscriber.getId(), exception);
                    }
                } else if (!endDate.isAfter(warningThreshold)) {
                    expiringSoon++;
                }
            }

            if (batch.size() < BATCH_SIZE || shouldStop()) {
                break;
            }
        }

        log.info("Subscription expiry scan finished: scanned={}, expiringSoon={}, expired={}, failed={}, stopped={}",
                scanned, expiringSoon, expired, failed, shouldStop());
        if (failed > 0) {
            log.warn("Subscription expiry scan completed with {} failed record(s)", failed);
        }
    }
}
