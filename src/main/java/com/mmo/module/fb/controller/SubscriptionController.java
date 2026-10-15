//package com.mmo.module.fb.controller;
//
//import com.mmo.module.fb.domain.entity.Subscriber;
//import com.mmo.module.fb.domain.entity.SubscriptionPlan;
//import com.mmo.module.fb.monetization.SubscriptionService;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.List;
//import java.util.Map;
//import java.util.Optional;
//
//@Slf4j
//@RestController
//@RequestMapping("/api/subscriptions")
//@RequiredArgsConstructor
//public class SubscriptionController {
//
//    private final SubscriptionService subscriptionService;
//
//    @PostMapping("/create")
//    public ResponseEntity<Subscriber> createSubscription(
//            @RequestBody Map<String, Object> request) {
//        try {
//            String userId = (String) request.get("userId");
//            String email = (String) request.get("email");
//            Long planId = Long.valueOf(request.get("planId").toString());
//            String paymentMethodId = (String) request.get("paymentMethodId");
//
////            Subscriber subscriber = subscriptionService.createSubscription(
////                    userId, email, planId, paymentMethodId);
//
////            return ResponseEntity.ok(subscriber);
//            return ResponseEntity.ok(null);
//        } catch (Exception e) {
//            log.error("❌ Error creating subscription: {}", e.getMessage());
//            return ResponseEntity.internalServerError().build();
//        }
//    }
//
//    @PostMapping("/upgrade")
//    public ResponseEntity<Subscriber> upgradeSubscription(
//            @RequestBody Map<String, Object> request) {
//        try {
//            String userId = (String) request.get("userId");
//            Long newPlanId = Long.valueOf(request.get("newPlanId").toString());
//
////            Subscriber subscriber = subscriptionService.upgradeSubscription(userId, newPlanId);
//
////            return ResponseEntity.ok(subscriber);
//            return ResponseEntity.ok(null);
//        } catch (Exception e) {
//            log.error("❌ Error upgrading subscription: {}", e.getMessage());
//            return ResponseEntity.internalServerError().build();
//        }
//    }
//
//    @PostMapping("/cancel")
//    public ResponseEntity<Void> cancelSubscription(@RequestBody Map<String, String> request) {
//        try {
//            String userId = request.get("userId");
////            subscriptionService.cancelSubscription(userId);
//            return ResponseEntity.ok().build();
//        } catch (Exception e) {
//            log.error("❌ Error cancelling subscription: {}", e.getMessage());
//            return ResponseEntity.internalServerError().build();
//        }
//    }
//
//    @GetMapping("/user/{userId}")
//    public ResponseEntity<Subscriber> getSubscriber(@PathVariable String userId) {
//        try {
//            Optional<Subscriber> subscriber = subscriptionService.getSubscriber(userId);
//            return subscriber.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
//        } catch (Exception e) {
//            log.error("❌ Error fetching subscriber: {}", e.getMessage());
//            return ResponseEntity.internalServerError().build();
//        }
//    }
//
//    @GetMapping("/active")
//    public ResponseEntity<List<Subscriber>> getActiveSubscribers() {
//        try {
//            List<Subscriber> subscribers = subscriptionService.getActiveSubscribers();
//            return ResponseEntity.ok(subscribers);
//        } catch (Exception e) {
//            log.error("❌ Error fetching active subscribers: {}", e.getMessage());
//            return ResponseEntity.internalServerError().build();
//        }
//    }
//
//    @GetMapping("/plans")
//    public ResponseEntity<List<SubscriptionPlan>> getAllPlans() {
//        try {
//            List<SubscriptionPlan> plans = subscriptionService.getAllPlans();
//            return ResponseEntity.ok(plans);
//        } catch (Exception e) {
//            log.error("❌ Error fetching subscription plans: {}", e.getMessage());
//            return ResponseEntity.internalServerError().build();
//        }
//    }
//
//    @PostMapping("/plans")
//    public ResponseEntity<SubscriptionPlan> createPlan(@RequestBody Map<String, Object> request) {
//        try {
//            String name = (String) request.get("name");
//            String description = (String) request.get("description");
//            String price = (String) request.get("price");
//            String billingCycle = (String) request.get("billingCycle");
//            String stripePriceId = (String) request.get("stripePriceId");
//            String features = (String) request.get("features");
//
//            SubscriptionPlan plan = subscriptionService.createPlan(
//                    name, description, price, billingCycle, stripePriceId, features);
//
//            return ResponseEntity.ok(plan);
//        } catch (Exception e) {
//            log.error("❌ Error creating subscription plan: {}", e.getMessage());
//            return ResponseEntity.internalServerError().build();
//        }
//    }
//}
