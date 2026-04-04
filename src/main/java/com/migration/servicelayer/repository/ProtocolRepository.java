package com.migration.servicelayer.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import com.migration.servicelayer.model.IngestionProtocol;
import com.migration.servicelayer.model.ProtocolStatus;

@Repository
public interface ProtocolRepository extends MongoRepository<IngestionProtocol, String> {

    @Query("{ '_id': ?0 }")
    @Update("{ '$set': { 'status': ?1, 'updatedAt': ?2 } }")
    void updateStatus(String id, ProtocolStatus status, java.time.LocalDateTime updatedAt);
}
