package com.fitnesshub.subscription;

import com.fitnesshub.audit.AuditAction;
import com.fitnesshub.audit.AuditService;
import com.fitnesshub.user.Role;
import com.fitnesshub.user.User;
import com.fitnesshub.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public SubscriptionService(SubscriptionRepository subscriptionRepository, UserRepository userRepository,
                                AuditService auditService) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public Subscription getOrCreate(UUID userId) {
        return subscriptionRepository.findByUserId(userId).orElseGet(() -> {
            User user = userRepository.findById(userId).orElseThrow();
            SubscriptionType type = user.getRole() == Role.COACH ? SubscriptionType.COACH_PLAN : SubscriptionType.CLIENT_PLAN;
            return subscriptionRepository.save(new Subscription(userId, type));
        });
    }

    @Transactional(readOnly = true)
    public boolean isPaid(UUID userId) {
        return subscriptionRepository.findByUserId(userId)
                .map(s -> s.getTier() != SubscriptionTier.FREE && s.getStatus() == SubscriptionStatus.ACTIVE)
                .orElse(false);
    }

    @Transactional
    public Subscription activate(String stripeCustomerId, String stripeSubscriptionId, java.time.Instant periodEnd) {
        Subscription sub = subscriptionRepository.findByStripeCustomerId(stripeCustomerId)
                .orElseThrow(() -> new IllegalStateException("No subscription found for Stripe customer " + stripeCustomerId));
        sub.setTier(sub.getType() == SubscriptionType.COACH_PLAN ? SubscriptionTier.PRO : SubscriptionTier.PREMIUM);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setStripeSubscriptionId(stripeSubscriptionId);
        sub.setCurrentPeriodEnd(periodEnd);
        sub = subscriptionRepository.save(sub);
        auditService.record(sub.getUserId(), AuditAction.SUBSCRIPTION_STARTED, "Subscription", sub.getId());
        return sub;
    }

    @Transactional
    public void cancel(String stripeSubscriptionId) {
        subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId).ifPresent(sub -> {
            sub.setTier(SubscriptionTier.FREE);
            sub.setStatus(SubscriptionStatus.CANCELED);
            subscriptionRepository.save(sub);
            auditService.record(sub.getUserId(), AuditAction.SUBSCRIPTION_CANCELLED, "Subscription", sub.getId());
        });
    }

    @Transactional
    public void linkStripeCustomer(UUID userId, String stripeCustomerId) {
        Subscription sub = getOrCreate(userId);
        sub.setStripeCustomerId(stripeCustomerId);
        subscriptionRepository.save(sub);
    }
}
