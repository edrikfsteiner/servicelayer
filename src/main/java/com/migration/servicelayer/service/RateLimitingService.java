package com.migration.servicelayer.service;

import com.github.benmanes.caffeine.cache.LoadingCache;
import io.github.bucket4j.Bucket;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Manages per-tenant rate-limit buckets backed entirely by Caffeine in-memory cache.
 * No database access is performed — bucket state lives 100% in RAM.
 */
@Service
@RequiredArgsConstructor
public class RateLimitingService {

    private final LoadingCache<String, Bucket> rateLimitCache;

    /**
     * Returns the existing Bucket for the given tenant, or atomically creates a new
     * one via the Caffeine loader if none exists yet.
     *
     * @param tenantId the tenant identifier extracted from the JWT claim
     * @return the Bucket associated with this tenant
     */
    public Bucket resolveBucket(String tenantId) {
        return rateLimitCache.get(tenantId);
    }
}
