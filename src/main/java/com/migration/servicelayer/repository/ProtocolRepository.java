package com.migration.servicelayer.repository;

import com.migration.servicelayer.model.IngestionProtocol;
import com.migration.servicelayer.model.ProtocolStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ProtocolRepository extends MongoRepository<IngestionProtocol, String> {

    @Query("{ '_id': ?0 }")
    @Update("{ '$set': { 'status': ?1, 'updatedAt': ?2 } }")
    void updateStatus(String id, ProtocolStatus status, LocalDateTime updatedAt);

    Optional<IngestionProtocol> findByIdAndTenantId(String id, String tenantId);
}
