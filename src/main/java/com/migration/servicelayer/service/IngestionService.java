package com.migration.servicelayer.service;

import com.migration.servicelayer.dto.IngestionBatchMessage;
import com.migration.servicelayer.dto.IngestionMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Slf4j
@Service
public class IngestionService {

    @Value("${app.messaging.exchange}")
    private String exchange;

    @Value("${app.messaging.routing-key-main}")
    private String mainRoutingKey;

    private final RabbitTemplate rabbitTemplate;
    private final ProtocolService protocolService;

    public String publishToQueue(String tenantId, String eventType, Map<String, Object> payload) {
        String protocolId = protocolService.createProtocol(tenantId, eventType);
        IngestionMessage message = new IngestionMessage(protocolId, tenantId, eventType, payload);
        rabbitTemplate.convertAndSend(exchange, mainRoutingKey, message);

        log.info("Ingestão recebida e enfileirada: Protocolo {}, Tenant {}", protocolId, tenantId);
        return protocolId;
    }

    public String publishBatchToQueue(String tenantId, String eventType, List<Map<String, Object>> payloads) {
        String protocolId = protocolService.createProtocol(tenantId, eventType);
        IngestionBatchMessage message = new IngestionBatchMessage(protocolId, tenantId, eventType, payloads);
        rabbitTemplate.convertAndSend(exchange, mainRoutingKey, message);

        log.info(
                "Lote recebido e enfileirado: Protocolo {}, {} registros para Tenant {}, EventType {}",
                protocolId,
                payloads.size(),
                tenantId,
                eventType
        );
        return protocolId;
    }
}
