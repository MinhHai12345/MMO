package com.mmo.module.fb.monetization.job;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import com.mmo.module.fb.monetization.entity.Subscriber;
import com.mmo.module.fb.monetization.repository.SubscriberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
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

    private final SubscriberRepository subscriberRepository;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        Instant now = Instant.now();
        Instant warningThreshold = now.plus(EXPIRY_WARNING_DAYS, ChronoUnit.DAYS);
        List<Subscriber> subscribers = subscriberRepository.findBySubscriptionStatus(ACTIVE_STATUS);

        if (subscribers.isEmpty()) {
            log.debug("Subscription expiry check skipped: no active subscribers");
            return;
        }

        int expiringSoon = 0;
        int expired = 0;

        for (Subscriber subscriber : subscribers) {
            try {
                Instant endDate = subscriber.getEndDate();
                if (endDate == null) {
                    continue;
                }

                if (endDate.isBefore(now)) {
                    subscriber.setSubscriptionStatus(EXPIRED_STATUS);
                    subscriber.setAutoRenew(false);
                    subscriberRepository.save(subscriber);
                    expired++;
                } else if (endDate.isBefore(warningThreshold)) {
                    expiringSoon++;
                }
            } catch (Exception e) {
                log.error("Failed to process subscription for userId={}",
                        subscriber.getUserId(), e);
            }
        }

        log.info("Subscription expiry check completed: expiringSoon={}, expired={}",
                expiringSoon, expired);
    }
}
