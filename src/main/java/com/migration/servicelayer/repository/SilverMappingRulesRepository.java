package com.migration.servicelayer.repository;

import com.migration.servicelayer.model.SilverMappingRules;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface SilverMappingRulesRepository extends MongoRepository<SilverMappingRules, String> {

    /**
     * Looks up existing rules so the profiler can upsert rather than duplicate.
     */
    Optional<SilverMappingRules> findByTenantIdAndEventType(String tenantId, String eventType);
}
