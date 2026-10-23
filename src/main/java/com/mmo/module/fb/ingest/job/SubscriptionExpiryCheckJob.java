package com.mmo.module.fb.ingest.job;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import com.mmo.module.fb.monetization.entity.Subscriber;
import com.mmo.module.fb.monetization.repository.SubscriberRepository;
//import com.mmo.module.fb.monetization.SubscriptionService;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
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

    @Resource
    private SubscriberRepository subscriberRepository;

//    @Resource
//    private SubscriptionService subscriptionService;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        log.info("🔍 Starting subscription expiry check...");

        Instant now = Instant.now();
        Instant warningThreshold = now.plus(3, ChronoUnit.DAYS);

        List<Subscriber> activeSubscribers = subscriberRepository.findBySubscriptionStatus("ACTIVE");

        if (CollectionUtils.isEmpty(activeSubscribers)) {
            log.info("✅ No active subscribers to check");
            return;
        }

        int expiringSoonCount = 0;
        int expiredCount = 0;

        for (Subscriber subscriber : activeSubscribers) {
            try {
                if (subscriber.getEndDate() != null) {
                    if (subscriber.getEndDate().isBefore(now)) {
                        subscriber.setSubscriptionStatus("EXPIRED");
                        subscriber.setAutoRenew(false);
                        subscriberRepository.save(subscriber);
                        expiredCount++;
                        log.warn("⚠️ Subscription expired for user: {}", subscriber.getUserId());
                    } else if (subscriber.getEndDate().isBefore(warningThreshold)) {
                        expiringSoonCount++;
                        log.info("📢 Subscription expiring soon for user: {} (expires: {})", 
                                subscriber.getUserId(), subscriber.getEndDate());
                        // TODO: Send notification to user about upcoming expiry
                    }
                }
            } catch (Exception e) {
                log.error("❌ Error checking subscription for user {}: {}", 
                        subscriber.getUserId(), e.getMessage());
            }
        }

        log.info("✅ Subscription expiry check completed. Expiring soon: {}, Expired: {}", 
                expiringSoonCount, expiredCount);
    }
}
