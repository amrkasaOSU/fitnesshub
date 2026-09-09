package com.fitnesshub.subscription;

/**
 * FREE applies to both plan types. PRO is only valid with COACH_PLAN,
 * PREMIUM only with CLIENT_PLAN - enforced in SubscriptionService, not by the
 * type system, to avoid a combinatorial enum explosion for two plan shapes.
 */
public enum SubscriptionTier {
    FREE,
    PRO,
    PREMIUM
}
