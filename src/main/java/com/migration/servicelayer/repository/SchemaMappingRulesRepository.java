package com.migration.servicelayer.repository;

import com.migration.servicelayer.model.SchemaMappingRules;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SchemaMappingRulesRepository extends MongoRepository<SchemaMappingRules, String> {

    Optional<SchemaMappingRules> findByTenantIdAndEventType(String tenantId, String eventType);

    List<SchemaMappingRules> findByTenantId(String tenantId);
}
