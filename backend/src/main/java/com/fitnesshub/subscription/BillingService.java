package com.fitnesshub.subscription;

import com.fitnesshub.common.exception.ConflictException;
import com.fitnesshub.security.CurrentUser;
import com.fitnesshub.subscription.dto.CheckoutSessionDto;
import com.fitnesshub.user.Role;
import com.fitnesshub.user.User;
import com.fitnesshub.user.UserRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Stripe test-mode integration. Every entry point checks isConfigured() first
 * and returns a clear "billing not configured" error instead of a stack
 * trace when STRIPE_SECRET_KEY is blank - this app must run fully without it.
 */
@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;

    @Value("${fitnesshub.stripe.secret-key:}")
    private String secretKey;

    @Value("${fitnesshub.stripe.webhook-secret:}")
    private String webhookSecret;

    @Value("${fitnesshub.stripe.coach-pro-price-id:}")
    private String coachProPriceId;

    @Value("${fitnesshub.stripe.client-premium-price-id:}")
    private String clientPremiumPriceId;

    @Value("${fitnesshub.cors.allowed-origins}")
    private String frontendUrl;

    public BillingService(SubscriptionRepository subscriptionRepository, SubscriptionService subscriptionService,
                           UserRepository userRepository, CurrentUser currentUser) {
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionService = subscriptionService;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
    }

    @PostConstruct
    void configureStripe() {
        if (isConfigured()) {
            Stripe.apiKey = secretKey;
        }
    }

    public boolean isConfigured() {
        return secretKey != null && !secretKey.isBlank();
    }

    @Transactional
    public CheckoutSessionDto createCheckoutSession() {
        requireConfigured();
        User user = userRepository.findById(currentUser.id()).orElseThrow();
        Subscription subscription = subscriptionService.getOrCreate(user.getId());
        String priceId = user.getRole() == Role.COACH ? coachProPriceId : clientPremiumPriceId;
        if (priceId == null || priceId.isBlank()) {
            throw new ConflictException("No Stripe price configured for this plan yet.");
        }

        String origin = frontendUrl.split(",")[0];
        try {
            SessionCreateParams.Builder params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setSuccessUrl(origin + "/settings?checkout=success")
                    .setCancelUrl(origin + "/settings?checkout=cancelled")
                    .setClientReferenceId(user.getId().toString())
                    .addLineItem(SessionCreateParams.LineItem.builder().setPrice(priceId).setQuantity(1L).build());
            if (subscription.getStripeCustomerId() != null) {
                params.setCustomer(subscription.getStripeCustomerId());
            } else {
                params.setCustomerEmail(user.getEmail());
            }
            Session session = Session.create(params.build());
            return new CheckoutSessionDto(session.getUrl());
        } catch (StripeException e) {
            log.error("Stripe checkout session creation failed", e);
            throw new ConflictException("Could not start checkout: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public CheckoutSessionDto createPortalSession() {
        requireConfigured();
        Subscription subscription = subscriptionService.getOrCreate(currentUser.id());
        if (subscription.getStripeCustomerId() == null) {
            throw new ConflictException("No billing account yet - start a checkout first.");
        }
        String origin = frontendUrl.split(",")[0];
        try {
            com.stripe.param.billingportal.SessionCreateParams params = com.stripe.param.billingportal.SessionCreateParams.builder()
                    .setCustomer(subscription.getStripeCustomerId())
                    .setReturnUrl(origin + "/settings")
                    .build();
            com.stripe.model.billingportal.Session session = com.stripe.model.billingportal.Session.create(params);
            return new CheckoutSessionDto(session.getUrl());
        } catch (StripeException e) {
            log.error("Stripe portal session creation failed", e);
            throw new ConflictException("Could not open billing portal: " + e.getMessage());
        }
    }

    @Transactional
    public void handleWebhook(String payload, String signatureHeader) {
        requireConfigured();
        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
        } catch (Exception e) {
            log.warn("Stripe webhook signature verification failed", e);
            throw new ConflictException("Invalid webhook signature.");
        }

        switch (event.getType()) {
            case "checkout.session.completed" -> handleCheckoutCompleted(event);
            case "customer.subscription.updated" -> handleSubscriptionUpdated(event);
            case "customer.subscription.deleted" -> handleSubscriptionDeleted(event);
            default -> log.debug("Ignoring unhandled Stripe event type: {}", event.getType());
        }
    }

    private void handleCheckoutCompleted(Event event) {
        event.getDataObjectDeserializer().getObject().ifPresent(obj -> {
            if (!(obj instanceof Session session)) {
                return;
            }
            String userIdStr = session.getClientReferenceId();
            if (userIdStr == null) {
                log.warn("Stripe checkout.session.completed had no client_reference_id");
                return;
            }
            UUID userId = UUID.fromString(userIdStr);
            subscriptionService.linkStripeCustomer(userId, session.getCustomer());
            Instant periodEnd = Instant.now().plusSeconds(30L * 24 * 3600); // refined by the subsequent subscription.updated event
            subscriptionService.activate(session.getCustomer(), session.getSubscription(), periodEnd);
        });
    }

    private void handleSubscriptionUpdated(Event event) {
        event.getDataObjectDeserializer().getObject().ifPresent(obj -> {
            if (!(obj instanceof com.stripe.model.Subscription sub)) {
                return;
            }
            subscriptionRepository.findByStripeCustomerId(sub.getCustomer()).ifPresent(local -> {
                boolean active = "active".equals(sub.getStatus()) || "trialing".equals(sub.getStatus());
                local.setStatus(active ? SubscriptionStatus.ACTIVE : SubscriptionStatus.PAST_DUE);
                Long periodEnd = firstItemPeriodEnd(sub);
                if (periodEnd != null) {
                    local.setCurrentPeriodEnd(Instant.ofEpochSecond(periodEnd));
                }
                local.setStripeSubscriptionId(sub.getId());
                if (active) {
                    local.setTier(local.getType() == SubscriptionType.COACH_PLAN ? SubscriptionTier.PRO : SubscriptionTier.PREMIUM);
                }
                subscriptionRepository.save(local);
            });
        });
    }

    private Long firstItemPeriodEnd(com.stripe.model.Subscription sub) {
        if (sub.getItems() == null || sub.getItems().getData() == null || sub.getItems().getData().isEmpty()) {
            return null;
        }
        SubscriptionItem item = sub.getItems().getData().get(0);
        return item.getCurrentPeriodEnd();
    }

    private void handleSubscriptionDeleted(Event event) {
        event.getDataObjectDeserializer().getObject().ifPresent(obj -> {
            if (obj instanceof com.stripe.model.Subscription sub) {
                subscriptionService.cancel(sub.getId());
            }
        });
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new ConflictException("Billing is not configured on this server yet.");
        }
    }
}
