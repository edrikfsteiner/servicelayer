package com.migration.servicelayer.repository;

import com.migration.servicelayer.model.ApiClient;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApiClientRepository extends MongoRepository<ApiClient, String> {
    Optional<ApiClient> findByClientId(String clientId);
}