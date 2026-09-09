package com.fitnesshub.ai;

import com.fitnesshub.subscription.SubscriptionService;
import com.fitnesshub.user.Role;
import com.fitnesshub.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;

/**
 * Sliding-by-calendar-day AI usage cap, enforced in Redis (INCR + EXPIRE) so
 * it works correctly across multiple backend instances. See section 81/125.
 */
@Component
public class AiRateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final SubscriptionService subscriptionService;

    @Value("${fitnesshub.ai.rate-limits.free-per-day:5}")
    private int freePerDay;

    @Value("${fitnesshub.ai.rate-limits.premium-per-day:50}")
    private int premiumPerDay;

    @Value("${fitnesshub.ai.rate-limits.coach-per-day:100}")
    private int coachPerDay;

    public AiRateLimiter(StringRedisTemplate redisTemplate, SubscriptionService subscriptionService) {
        this.redisTemplate = redisTemplate;
        this.subscriptionService = subscriptionService;
    }

    public int dailyLimitFor(User user) {
        if (user.getRole() == Role.COACH || user.getRole() == Role.ADMIN) {
            return coachPerDay;
        }
        return subscriptionService.isPaid(user.getId()) ? premiumPerDay : freePerDay;
    }

    /** Returns true and consumes one request if the user is under their daily limit; false if they're at the cap. */
    public boolean tryConsume(User user) {
        int limit = dailyLimitFor(user);
        String key = "ai-rate:" + user.getId() + ":" + LocalDate.now();
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofHours(48));
        }
        return count != null && count <= limit;
    }
}
