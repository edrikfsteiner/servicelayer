package com.migration.servicelayer.service;

import com.github.benmanes.caffeine.cache.LoadingCache;
import io.github.bucket4j.Bucket;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class RateLimitService {

    private final LoadingCache<String, Bucket> rateLimitCache;

    public Bucket resolveBucket(String tenantId) {
        return rateLimitCache.get(tenantId);
    }
}
