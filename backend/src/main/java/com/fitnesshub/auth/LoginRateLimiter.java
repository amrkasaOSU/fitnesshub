package com.fitnesshub.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Throttles password guessing. Counted in Redis so the limit holds across
 * multiple backend instances rather than resetting per process.
 *
 * <p>Two independent counters, because each stops a different attack:
 * <ul>
 *   <li><b>per email</b> - someone hammering one account from many addresses;</li>
 *   <li><b>per client IP</b> - someone spraying one common password across many
 *       accounts, which never trips a per-email counter.</li>
 * </ul>
 *
 * <p>Counters only increment on <em>failure</em> and are cleared on a successful
 * login, so an ordinary user who mistypes a couple of times and then gets in is
 * never affected. The window is short enough not to strand a real person who
 * genuinely forgot, while making bulk guessing impractical.
 */
@Component
public class LoginRateLimiter {

    private final StringRedisTemplate redisTemplate;

    @Value("${fitnesshub.auth.login-rate-limit.max-attempts:10}")
    private int maxAttempts;

    @Value("${fitnesshub.auth.login-rate-limit.window-minutes:15}")
    private int windowMinutes;

    public LoginRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private String emailKey(String email) {
        return "login-fail:email:" + email;
    }

    private String ipKey(String ip) {
        return "login-fail:ip:" + ip;
    }

    /** True when either counter is already at the cap, meaning the attempt should be refused unseen. */
    public boolean isBlocked(String email, String ip) {
        return atCap(emailKey(email)) || atCap(ipKey(ip));
    }

    private boolean atCap(String key) {
        String current = redisTemplate.opsForValue().get(key);
        return current != null && Integer.parseInt(current) >= maxAttempts;
    }

    public void recordFailure(String email, String ip) {
        bump(emailKey(email));
        bump(ipKey(ip));
    }

    private void bump(String key) {
        Long count = redisTemplate.opsForValue().increment(key);
        // Set the TTL only when the counter is created, so a burst of failures
        // can't keep pushing the expiry out and extend the lockout indefinitely.
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofMinutes(windowMinutes));
        }
    }

    /** Clears both counters - the person proved they own the account. */
    public void recordSuccess(String email, String ip) {
        redisTemplate.delete(emailKey(email));
        redisTemplate.delete(ipKey(ip));
    }

    public int windowMinutes() {
        return windowMinutes;
    }
}
