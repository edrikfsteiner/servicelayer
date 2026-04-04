package com.migration.servicelayer.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class MappingStore {

    private static final String COLLECTION_NAME = "mapping_contracts";

    private final Map<String, Map<String, String>> cache = new ConcurrentHashMap<>();
    private final MongoTemplate mongoTemplate;

    public MappingStore(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    public void init() {
        loadAll();
    }

    public void saveMapping(String lakehouseTable, Map<String, String> mapping) {
        try {
            Query query = new Query(Criteria.where("_id").is(lakehouseTable));
            Update update = new Update()
                    .set("mapping", mapping)
                    .set("updatedAt", LocalDateTime.now());

            mongoTemplate.upsert(query, update, COLLECTION_NAME);
            cache.put(lakehouseTable, mapping);
            log.info("Contrato salvo para coleção: {}", lakehouseTable);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao salvar contrato: " + e.getMessage(), e);
        }
    }

    public Map<String, String> getMapping(String lakehouseTable) {
        return cache.get(lakehouseTable);
    }

    private void loadAll() {
        var documents = mongoTemplate.findAll(Map.class, COLLECTION_NAME);

        for (Map document : documents) {
            try {
                String id = (String) document.get("_id");
                Map<String, String> mapping = (Map<String, String>) document.get("mapping");
                cache.put(id, mapping);
            } catch (Exception e) {
                log.error("Erro ao carregar contrato do database: {}", e.getMessage());
            }
        }

        log.info("Carregados {} contratos de mapeamento do database", cache.size());
    }
}