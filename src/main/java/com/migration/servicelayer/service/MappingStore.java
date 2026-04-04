package com.migration.servicelayer.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import com.migration.servicelayer.model.MappingContract;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MappingStore {

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
            Query query = Query.query(Criteria.where("_id").is(lakehouseTable));
            Update update = new Update()
                    .set("mapping", mapping)
                    .set("updatedAt", LocalDateTime.now());
            mongoTemplate.upsert(query, update, MappingContract.class);
            cache.put(lakehouseTable, mapping);
            log.info("Contrato salvo para tabela: {}", lakehouseTable);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao salvar contrato: " + e.getMessage(), e);
        }
    }

    public Map<String, String> getMapping(String lakehouseTable) {
        return cache.get(lakehouseTable);
    }

    private void loadAll() {
        List<MappingContract> contracts = mongoTemplate.findAll(MappingContract.class);
        contracts.forEach(c -> {
            if (c.getMapping() != null) {
                cache.put(c.getId(), c.getMapping());
            }
        });
        log.info("Carregados {} contratos de mapeamento do banco", cache.size());
    }
}