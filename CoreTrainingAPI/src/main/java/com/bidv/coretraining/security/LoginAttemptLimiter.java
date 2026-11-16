package com.bidv.coretraining.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows password guessing: too many failed logins for one username, or from one client IP,
 * within the window block further attempts until the block ends.
 * Counts are kept in memory, so they reset when the backend restarts and are per instance.
 */
@Component
public class LoginAttemptLimiter {
    private static final int MAX_TRACKED_KEYS = 100_000;

    private final int maxFailuresPerUsername;
    private final int maxFailuresPerIp;
    private final Duration window;
    private final Duration blockDuration;
    private final Clock clock;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    @Autowired
    public LoginAttemptLimiter(
            @Value("${app.login-limit.max-failures-per-username:5}") int maxFailuresPerUsername,
            @Value("${app.login-limit.max-failures-per-ip:30}") int maxFailuresPerIp,
            @Value("${app.login-limit.window:15m}") Duration window,
            @Value("${app.login-limit.block-duration:15m}") Duration blockDuration) {
        this(maxFailuresPerUsername, maxFailuresPerIp, window, blockDuration, Clock.systemUTC());
    }

    LoginAttemptLimiter(int maxFailuresPerUsername, int maxFailuresPerIp, Duration window,
            Duration blockDuration, Clock clock) {
        this.maxFailuresPerUsername = maxFailuresPerUsername;
        this.maxFailuresPerIp = maxFailuresPerIp;
        this.window = window;
        this.blockDuration = blockDuration;
        this.clock = clock;
    }

    /** Throws when either the username or the IP is currently blocked. */
    public void checkAllowed(String username, String ip) {
        Instant now = clock.instant();
        long wait = Math.max(remainingBlock(userKey(username), now), remainingBlock(ipKey(ip), now));
        if (wait > 0)
            throw new LoginThrottledException(wait);
    }

    public void recordFailure(String username, String ip) {
        Instant now = clock.instant();
        if (attempts.size() >= MAX_TRACKED_KEYS)
            attempts.values().removeIf(a -> a.expired(now));
        if (attempts.size() >= MAX_TRACKED_KEYS)
            return;
        attempts.compute(userKey(username), (k, a) -> fail(a, now, maxFailuresPerUsername));
        attempts.compute(ipKey(ip), (k, a) -> fail(a, now, maxFailuresPerIp));
    }

    /** A correct password clears the username's count; the IP's count stays. */
    public void recordSuccess(String username) {
        attempts.remove(userKey(username));
    }

    private long remainingBlock(String key, Instant now) {
        Attempts a = attempts.get(key);
        if (a == null || a.blockedUntil == null || !a.blockedUntil.isAfter(now))
            return 0;
        return Math.max(1, Duration.between(now, a.blockedUntil).toSeconds());
    }

    private Attempts fail(Attempts current, Instant now, int limit) {
        Attempts a = current == null || current.expired(now) ? new Attempts(now) : current;
        if (!a.windowStart.plus(window).isAfter(now)) {
            a.windowStart = now;
            a.failures = 0;
        }
        a.failures++;
        if (a.failures >= limit) {
            a.blockedUntil = now.plus(blockDuration);
            a.windowStart = now;
            a.failures = 0;
        }
        return a;
    }

    private static String userKey(String username) {
        return "u:" + (username == null ? "" : username.trim().toLowerCase(Locale.ROOT));
    }

    private static String ipKey(String ip) {
        return "i:" + (ip == null ? "" : ip);
    }

    private final class Attempts {
        Instant windowStart;
        int failures;
        // Written inside compute(), read by checkAllowed() without the map's lock.
        volatile Instant blockedUntil;

        Attempts(Instant now) {
            this.windowStart = now;
        }

        boolean expired(Instant now) {
            boolean windowOver = !windowStart.plus(window).isAfter(now);
            boolean blockOver = blockedUntil == null || !blockedUntil.isAfter(now);
            return windowOver && blockOver;
        }
    }

    public static class LoginThrottledException extends RuntimeException {
        private final long retryAfterSeconds;

        public LoginThrottledException(long retryAfterSeconds) {
            super("Bạn đã đăng nhập sai quá nhiều lần. Vui lòng thử lại sau "
                    + Math.max(1, (retryAfterSeconds + 59) / 60) + " phút.");
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}
