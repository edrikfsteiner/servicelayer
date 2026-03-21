package com.migration.servicelayer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
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

    public MappingStore(
            @Qualifier("targetJdbcTemplate") NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        jdbc.getJdbcTemplate().execute("""
                CREATE TABLE IF NOT EXISTS mapping_contract (
                    target_table VARCHAR(255) PRIMARY KEY,
                    mapping_json TEXT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        loadAll();
    }

    public void saveMapping(String targetTable, Map<String, String> mapping) {
        try {
            String json = objectMapper.writeValueAsString(mapping);
            jdbc.update("""
                    INSERT INTO mapping_contract (target_table, mapping_json)
                    VALUES (:targetTable, :json)
                    ON CONFLICT (target_table) DO UPDATE SET mapping_json = :json, created_at = CURRENT_TIMESTAMP
                    """,
                    new MapSqlParameterSource()
                            .addValue("targetTable", targetTable)
                            .addValue("json", json));
            cache.put(targetTable, mapping);
            log.info("Contrato salvo para tabela: {}", targetTable);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao salvar contrato: " + e.getMessage(), e);
        }
    }

    public Map<String, String> getMapping(String targetTable) {
        return cache.get(targetTable);
    }

    private void loadAll() {
        jdbc.query("SELECT target_table, mapping_json FROM mapping_contract", (rs, rowNum) -> {
            try {
                String table = rs.getString("target_table");
                Map<String, String> mapping = objectMapper.readValue(
                        rs.getString("mapping_json"), new TypeReference<>() {});
                cache.put(table, mapping);
            } catch (Exception e) {
                log.error("Erro ao carregar contrato do banco: {}", e.getMessage());
            }
            return null;
        });
        log.info("Carregados {} contratos de mapeamento do banco", cache.size());
    }
}