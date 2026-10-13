package com.mmo.module.fb.monetization;

import com.mmo.module.fb.domain.entity.Subscriber;
import com.mmo.module.fb.domain.entity.SubscriptionPlan;
import com.mmo.module.fb.domain.repository.SubscriberRepository;
import com.mmo.module.fb.domain.repository.SubscriptionPlanRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Subscription;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.SubscriptionCreateParams;
import com.stripe.param.SubscriptionUpdateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriberRepository subscriberRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;

    @Value("${stripe.api.key}")
    private String stripeApiKey;

    public void initializeStripe() {
        Stripe.apiKey = stripeApiKey;
    }

    @Transactional
    public Subscriber createSubscription(String userId, String email, Long planId, String paymentMethodId) {
        initializeStripe();
        
        try {
            SubscriptionPlan plan = subscriptionPlanRepository.findById(planId)
                    .orElseThrow(() -> new RuntimeException("Subscription plan not found"));

            CustomerCreateParams customerParams = CustomerCreateParams.builder()
                    .setEmail(email)
                    .build();
            Customer customer = Customer.create(customerParams);

            SubscriptionCreateParams subscriptionParams = SubscriptionCreateParams.builder()
                    .setCustomer(customer.getId())
                    .addItem(
                            SubscriptionCreateParams.Item.builder()
                                    .setPrice(plan.getStripePriceId())
                                    .build()
                    )
                    .setDefaultPaymentMethod(paymentMethodId)
                    .build();

            Subscription stripeSubscription = Subscription.create(subscriptionParams);

            Subscriber subscriber = Subscriber.builder()
                    .userId(userId)
                    .subscriptionPlan(plan)
                    .email(email)
                    .subscriptionStatus("ACTIVE")
                    .startDate(Instant.now())
                    .endDate(Instant.ofEpochSecond(stripeSubscription.getCurrentPeriodEnd()))
                    .autoRenew(true)
                    .build();

            return subscriberRepository.save(subscriber);
        } catch (StripeException e) {
            log.error("❌ Failed to create subscription: {}", e.getMessage());
            throw new RuntimeException("Failed to create subscription", e);
        }
    }

    @Transactional
    public Subscriber upgradeSubscription(String userId, Long newPlanId) {
        initializeStripe();
        
        try {
            Subscriber subscriber = subscriberRepository.findByUserId(userId)
                    .orElseThrow(() -> new RuntimeException("Subscriber not found"));

            SubscriptionPlan newPlan = subscriptionPlanRepository.findById(newPlanId)
                    .orElseThrow(() -> new RuntimeException("Subscription plan not found"));

            Customer customer = Customer.retrieve(subscriber.getEmail());
            
            Subscription subscription = Subscription.retrieve(subscriber.getUserId());
            SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                    .addItem(
                            SubscriptionUpdateParams.Item.builder()
                                    .setPrice(newPlan.getStripePriceId())
                                    .build()
                    )
                    .build();
            
            subscription.update(params);

            subscriber.setSubscriptionPlan(newPlan);
            subscriber.setSubscriptionStatus("ACTIVE");
            
            return subscriberRepository.save(subscriber);
        } catch (StripeException e) {
            log.error("❌ Failed to upgrade subscription: {}", e.getMessage());
            throw new RuntimeException("Failed to upgrade subscription", e);
        }
    }

    @Transactional
    public void cancelSubscription(String userId) {
        initializeStripe();
        
        try {
            Subscriber subscriber = subscriberRepository.findByUserId(userId)
                    .orElseThrow(() -> new RuntimeException("Subscriber not found"));

            Subscription subscription = Subscription.retrieve(subscriber.getUserId());
            subscription.cancel();

            subscriber.setSubscriptionStatus("CANCELLED");
            subscriber.setAutoRenew(false);
            
            subscriberRepository.save(subscriber);
            log.info("✅ Subscription cancelled for user {}", userId);
        } catch (StripeException e) {
            log.error("❌ Failed to cancel subscription: {}", e.getMessage());
            throw new RuntimeException("Failed to cancel subscription", e);
        }
    }

    @Transactional
    public void renewSubscription(String userId) {
        try {
            Subscriber subscriber = subscriberRepository.findByUserId(userId)
                    .orElseThrow(() -> new RuntimeException("Subscriber not found"));

            if (subscriber.getEndDate().isBefore(Instant.now())) {
                subscriber.setSubscriptionStatus("EXPIRED");
                subscriberRepository.save(subscriber);
                log.warn("⚠️ Subscription expired for user {}", userId);
            } else {
                log.info("✅ Subscription is still active for user {}", userId);
            }
        } catch (Exception e) {
            log.error("❌ Failed to renew subscription: {}", e.getMessage());
        }
    }

    public Optional<Subscriber> getSubscriber(String userId) {
        return subscriberRepository.findByUserId(userId);
    }

    public List<Subscriber> getAllSubscribers() {
        return subscriberRepository.findAll();
    }

    public List<Subscriber> getActiveSubscribers() {
        return subscriberRepository.findBySubscriptionStatus("ACTIVE");
    }

    public List<SubscriptionPlan> getAllPlans() {
        return subscriptionPlanRepository.findByIsActiveTrue();
    }

    public Optional<SubscriptionPlan> getPlanById(Long planId) {
        return subscriptionPlanRepository.findById(planId);
    }

    @Transactional
    public SubscriptionPlan createPlan(String name, String description, String price, 
                                       String billingCycle, String stripePriceId, String features) {
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name(name)
                .description(description)
                .price(price)
                .billingCycle(billingCycle)
                .stripePriceId(stripePriceId)
                .features(features)
                .isActive(true)
                .build();
        
        return subscriptionPlanRepository.save(plan);
    }

    @Transactional
    public void deactivatePlan(Long planId) {
        SubscriptionPlan plan = subscriptionPlanRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("Subscription plan not found"));
        plan.setIsActive(false);
        subscriptionPlanRepository.save(plan);
    }
}
