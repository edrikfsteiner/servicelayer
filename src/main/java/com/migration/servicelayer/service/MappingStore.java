package com.migration.servicelayer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class MappingStore {

    private final Map<String, Map<String, String>> cache = new ConcurrentHashMap<>();
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public MappingStore(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        jdbc.getJdbcTemplate().execute("""
                CREATE TABLE IF NOT EXISTS mapping_contract (
                    lakehouse_table VARCHAR(255) PRIMARY KEY,
                    mapping_json TEXT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
        """);
        loadAll();
    }

    public void saveMapping(String lakehouseTable, Map<String, String> mapping) {
        try {
            String json = objectMapper.writeValueAsString(mapping);
            jdbc.update("""
                    INSERT INTO mapping_contract (lakehouse_table, mapping_json)
                    VALUES (:lakehouseTable, :json)
                    ON CONFLICT (lakehouse_table) DO UPDATE SET mapping_json = :json, created_at = CURRENT_TIMESTAMP
                    """,
                    new MapSqlParameterSource()
                            .addValue("lakehouseTable", lakehouseTable)
                            .addValue("json", json)
            );
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
        jdbc.query("SELECT lakehouse_table, mapping_json FROM mapping_contract", (rs, _) -> {
            try {
                String table = rs.getString("lakehouse_table");
                Map<String, String> mapping = objectMapper.readValue(
                        rs.getString("mapping_json"), new TypeReference<>() {}
                );
                cache.put(table, mapping);
            } catch (Exception e) {
                log.error("Erro ao carregar contrato do banco: {}", e.getMessage());
            }
            return null;
        });
        log.info("Carregados {} contratos de mapeamento do banco", cache.size());
    }
}