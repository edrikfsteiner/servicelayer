package com.migration.servicelayer.repository;

import com.migration.servicelayer.model.IngestionProtocol;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProtocolRepository extends MongoRepository<IngestionProtocol, ObjectId> {}
