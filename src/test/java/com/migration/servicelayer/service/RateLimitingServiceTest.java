package com.migration.servicelayer.service;

import com.github.benmanes.caffeine.cache.LoadingCache;
import com.migration.servicelayer.config.RateLimitConfig;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RateLimitingService.
 * No Spring context is needed — all objects are wired manually.
 * Uses a small capacity so tests run instantly without loops.
 */
class RateLimitingServiceTest {

    private RateLimitingService rateLimitingService;

    @BeforeEach
    void setUp() {
        RateLimitConfig config = new RateLimitConfig();
        config.setCapacity(1000);
        config.setMinutes(1);

        LoadingCache<String, Bucket> cache = config.rateLimitCache();
        rateLimitingService = new RateLimitingService(cache);
    }

    @Test
    void shouldAllowExactlyCapacityTokensInOneShot() {
        Bucket bucket = rateLimitingService.resolveBucket("tenant-allow");

        assertThat(bucket.tryConsume(1000)).isTrue();
    }

    @Test
    void shouldDenyThe1001stRequest() {
        Bucket bucket = rateLimitingService.resolveBucket("tenant-deny");

        // Drain all 1000 available tokens in a single atomic call
        assertThat(bucket.tryConsume(1000)).isTrue();

        // The very next request (1001st) must be denied
        assertThat(bucket.tryConsume(1)).isFalse();
    }

    @Test
    void shouldIsolateBucketsAcrossTenants() {
        Bucket tenantA = rateLimitingService.resolveBucket("tenant-a");
        Bucket tenantB = rateLimitingService.resolveBucket("tenant-b");

        // Exhaust tenant-a's quota
        assertThat(tenantA.tryConsume(1000)).isTrue();
        assertThat(tenantA.tryConsume(1)).isFalse();

        // tenant-b must be completely unaffected
        assertThat(tenantB.tryConsume(1)).isTrue();
    }

    @Test
    void shouldReturnSameBucketInstanceForSameTenant() {
        Bucket first = rateLimitingService.resolveBucket("tenant-cache");
        Bucket second = rateLimitingService.resolveBucket("tenant-cache");

        assertThat(first).isSameAs(second);
    }

    @Test
    void shouldProvideRetryAfterSecondsWhenLimitExceeded() {
        Bucket bucket = rateLimitingService.resolveBucket("tenant-retry");

        assertThat(bucket.tryConsume(1000)).isTrue();

        var probe = bucket.tryConsumeAndReturnRemaining(1);

        assertThat(probe.isConsumed()).isFalse();
        assertThat(probe.getNanosToWaitForRefill()).isPositive();
    }
}
