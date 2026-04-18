package com.migration.servicelayer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.migration.servicelayer.dto.IngestionMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class IngestionService {

    @Value("${app.messaging.exchange}")
    private String exchange;

    @Value("${app.messaging.routing-key-main}")
    private String mainRoutingKey;

    private final RabbitTemplate rabbitTemplate;
    private final ProtocolService protocolService;

    public IngestionService(RabbitTemplate rabbitTemplate, ProtocolService protocolService) {
        this.rabbitTemplate = rabbitTemplate;
        this.protocolService = protocolService;
    }

    public String publishToQueue(String tenantId, String eventType, JsonNode payload) {
        String protocolId = protocolService.createProtocol(tenantId, eventType);
        IngestionMessage message = new IngestionMessage(protocolId, tenantId, eventType, payload);
        rabbitTemplate.convertAndSend(exchange, mainRoutingKey, message);

        log.info("Ingestão recebida e enfileirada: Protocolo {}, Tenant {}", protocolId, tenantId);
        return protocolId;
    }
}