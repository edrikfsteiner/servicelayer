package com.migration.servicelayer.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.migration.servicelayer.security.filter.RateLimitFilter;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
@ConfigurationProperties(prefix = "rate-limit")
@Getter
@Setter
public class RateLimitConfig {

    private int capacity = 1000;
    private int minutes = 1;

    /**
     * In-memory Caffeine cache keyed by tenantId.
     * Entries expire after 1 hour of inactivity to prevent unbounded growth.
     * The loader creates a fresh Bucket on first access for any given tenant.
     */
    @Bean
    public LoadingCache<String, Bucket> rateLimitCache() {
        return Caffeine.newBuilder()
                .expireAfterAccess(1, TimeUnit.HOURS)
                .build(tenantId -> newBucket());
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, Duration.ofMinutes(minutes))
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Prevents Spring Boot from auto-registering RateLimitFilter as a raw servlet
     * filter. The filter is added exclusively inside the Spring Security filter chain
     * (after BearerTokenAuthenticationFilter) so the SecurityContext is populated.
     */
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter rateLimitFilter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(rateLimitFilter);
        registration.setEnabled(false);
        return registration;
    }
}
