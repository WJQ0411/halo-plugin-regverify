package run.halo.regverify;

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Per client IP rate limiting for the verification gate.
 *
 * <p>Both the public verification API and the actual signup POST are throttled, so that
 * neither a script answer nor the script runtime can be abused for brute force or a
 * denial of service attempt.
 */
@Component
public class VerificationRateLimiter {

    private static final RateLimiterConfig CONFIG = RateLimiterConfig.custom()
        .limitForPeriod(10)
        .limitRefreshPeriod(Duration.ofMinutes(1))
        .timeoutDuration(Duration.ZERO)
        .build();

    private final RateLimiterRegistry registry;

    public VerificationRateLimiter(RateLimiterRegistry registry) {
        this.registry = registry;
    }

    /**
     * @param scope {@code verify} for the API call, {@code signup} for the form submit
     * @param ip client IP, may be {@code unknown}
     * @return {@code true} if the caller may proceed
     */
    public boolean tryAcquire(String scope, String ip) {
        return registry.rateLimiter("regverify-" + scope + "-" + ip, CONFIG).acquirePermission();
    }
}
