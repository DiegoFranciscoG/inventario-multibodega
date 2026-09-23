package io.github.diegofranciscog.inventory.security;

import java.time.Duration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

import org.springframework.stereotype.Component;

import io.github.diegofranciscog.inventory.config.AppProperties;
import io.github.diegofranciscog.inventory.exception.TooManyRequestsException;

/**
 * Limita los intentos de login por IP y por correo (OWASP API2/API4). Los buckets viven en una caché acotada con
 * expiración, así un atacante no puede agotar la memoria creando claves nuevas.
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_KEYS = 10_000;

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(MAX_KEYS)
            .expireAfterAccess(Duration.ofMinutes(15))
            .build();
    private final int attemptsPerMinute;

    public LoginRateLimiter(AppProperties properties) {
        this.attemptsPerMinute = properties.security().loginAttemptsPerMinute();
    }

    /** Consume un intento para la IP y otro para el correo; lanza 429 si alguno se agotó. */
    public void check(String clientIp, String email) {
        consume("ip:" + clientIp);
        consume("email:" + email);
    }

    private void consume(String key) {
        Bucket bucket = buckets.get(key, k -> newBucket());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long seconds = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
            throw new TooManyRequestsException(seconds);
        }
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(attemptsPerMinute)
                .refillGreedy(attemptsPerMinute, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    /** Solo para tests: reinicia los contadores. */
    public void reset() {
        buckets.invalidateAll();
    }
}
