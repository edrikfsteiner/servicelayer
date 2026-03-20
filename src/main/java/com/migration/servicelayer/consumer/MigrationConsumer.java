package com.migration.servicelayer.consumer;

import com.migration.servicelayer.service.MappingStore;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MigrationConsumer {

    private final NamedParameterJdbcTemplate targetJdbcTemplate;
    private final MappingStore mappingStore;

    public MigrationConsumer(
            @Qualifier("targetJdbcTemplate") NamedParameterJdbcTemplate targetJdbcTemplate,
            MappingStore mappingStore
    ) {
        this.targetJdbcTemplate = targetJdbcTemplate;
        this.mappingStore = mappingStore;
    }

    @RabbitListener(queues = "migration.data.queue")
    public void processarMensagem(Map<String, Object> originPayload, @Header("targetTable") String targetTable) {
        System.out.println("Processando migração para a tabela: " + targetTable);

        Map<String, String> mapping = mappingStore.getMapping(targetTable);
        if (mapping == null) {
            throw new RuntimeException("Contrato de mapeamento não encontrado para a tabela: " + targetTable);
        }

        Map<String, Object> targetPayload = new HashMap<>();
        mapping.forEach((targetColumn, originColumn) -> {
            if (originPayload.containsKey(originColumn)) {
                targetPayload.put(targetColumn, originPayload.get(originColumn));
            }
        });

        String columns = String.join(", ", targetPayload.keySet());
        String params = targetPayload.keySet().stream()
                .map(key -> ":" + key)
                .collect(Collectors.joining(", "));

        String sql = String.format("INSERT INTO %s (%s) VALUES (%s)", targetTable, columns, params);

        targetJdbcTemplate.update(sql, new MapSqlParameterSource(targetPayload));
        System.out.println("Registro inserido com sucesso!");
    }
}