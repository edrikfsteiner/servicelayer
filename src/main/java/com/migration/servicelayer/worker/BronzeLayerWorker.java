package com.migration.servicelayer.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.migration.servicelayer.dto.IngestionMessage;
import com.migration.servicelayer.model.ProtocolStatus;
import com.migration.servicelayer.service.ProtocolService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BronzeLayerWorker {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ProtocolService protocolService;
    private final ObjectMapper objectMapper;

    public BronzeLayerWorker(NamedParameterJdbcTemplate jdbcTemplate, ProtocolService protocolService, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.protocolService = protocolService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initSchema() {
        jdbcTemplate.getJdbcTemplate().execute("""
                CREATE TABLE IF NOT EXISTS bronze_raw_data (
                    id BIGSERIAL PRIMARY KEY,
                    protocol_id VARCHAR(36) NOT NULL,
                    tenant_id VARCHAR(255) NOT NULL,
                    event_type VARCHAR(255) NOT NULL,
                    payload JSONB NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
        """);
    }

    @RabbitListener(queues = "${app.messaging.queue-bronze}")
    public void consume(IngestionMessage message, @Header(value = "reprocessed", required = false) boolean reprocessed) {
        String protocolId = message.protocolId();

        try {
            if (!reprocessed) {
                protocolService.updateStatus(protocolId, ProtocolStatus.IN_PROGRESS);
            }

            String jsonPayloadString = objectMapper.writeValueAsString(message.payload());
            String sql = """
                    INSERT INTO bronze_raw_data (protocol_id, tenant_id, event_type, payload)
                    VALUES (:protocolId, :tenantId, :eventType, :payload::jsonb)
            """;

            jdbcTemplate.update(sql, new MapSqlParameterSource()
                    .addValue("protocolId", protocolId)
                    .addValue("tenantId", message.tenantId())
                    .addValue("eventType", message.eventType())
                    .addValue("payload", jsonPayloadString)
            );

            protocolService.updateStatus(protocolId, ProtocolStatus.COMPLETED);
            log.info("Protocolo {}: Dados brutos salvos com sucesso na Camada Bronze", protocolId);
        } catch (Exception e) {
            log.error("Protocolo {}: Erro ao salvar na Camada Bronze - {}", protocolId, e.getMessage());
            protocolService.updateStatus(protocolId, ProtocolStatus.FAILED);
            throw new RuntimeException("Falha na ingestão", e);
        }
    }
}