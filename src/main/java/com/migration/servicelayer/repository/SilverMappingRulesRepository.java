package com.migration.servicelayer.repository;

import com.migration.servicelayer.model.SilverMappingRules;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SilverMappingRulesRepository extends MongoRepository<SilverMappingRules, String> {

    Optional<SilverMappingRules> findByTenantIdAndEventType(String tenantId, String eventType);

    List<SilverMappingRules> findByTenantId(String tenantId);
}
