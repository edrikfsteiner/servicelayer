package com.migration.servicelayer.consumer;

import com.migration.servicelayer.service.MappingStore;
import com.migration.servicelayer.service.ProtocolService;
import com.migration.servicelayer.util.NameValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MigrationConsumer {

    private final NamedParameterJdbcTemplate targetJdbcTemplate;
    private final MappingStore mappingStore;
    private final ProtocolService protocolService;

    public MigrationConsumer(
            @Qualifier("targetJdbcTemplate") NamedParameterJdbcTemplate targetJdbcTemplate,
            MappingStore mappingStore,
            ProtocolService protocolService
    ) {
        this.targetJdbcTemplate = targetJdbcTemplate;
        this.mappingStore = mappingStore;
        this.protocolService = protocolService;
    }

    @RabbitListener(queues = "migration.data.queue")
    public void processarMensagem(
            Map<String, Object> originPayload,
            @Header("targetTable") String targetTable,
            @Header("protocolId") String protocolId,
            @Header(value = "reprocessed", required = false) boolean reprocessed
    ) {
        try {
            NameValidator.validate(targetTable);

            Map<String, String> mapping = mappingStore.getMapping(targetTable);
            if (mapping == null) {
                throw new RuntimeException("Contrato de mapeamento não encontrado para: " + targetTable);
            }

            Map<String, Object> targetPayload = new HashMap<>();
            mapping.forEach((targetColumn, originColumn) -> {
                NameValidator.validate(targetColumn);
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

            if (!reprocessed) {
                protocolService.incrementProcessed(protocolId);
            }

            log.info("Protocolo {}: registro inserido em '{}'", protocolId, targetTable);
        } catch (Exception e) {
            log.error("Protocolo {}: erro ao processar registro - {}", protocolId, e.getMessage());
            if (!reprocessed) {
                protocolService.incrementFailed(protocolId);
            }
            throw e;
        }
    }
}