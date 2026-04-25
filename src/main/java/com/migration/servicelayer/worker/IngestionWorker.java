package com.migration.servicelayer.worker;

import com.migration.servicelayer.dto.IngestionMessage;
import com.migration.servicelayer.model.BronzeDocument;
import com.migration.servicelayer.model.ProtocolStatus;
import com.migration.servicelayer.service.ProtocolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@RequiredArgsConstructor
@Slf4j
@Service
public class IngestionWorker {

    private final MongoTemplate mongoTemplate;
    private final ProtocolService protocolService;

    @RabbitListener(queues = "${app.messaging.queue-main}")
    public void consume(IngestionMessage message, @Header(value = "reprocessed", required = false) boolean reprocessed) {
        String protocolId = message.protocolId();

        try {
            if (!reprocessed) {
                protocolService.updateStatus(protocolId, ProtocolStatus.IN_PROGRESS);
            }

            BronzeDocument document = BronzeDocument.builder()
                    .protocolId(protocolId)
                    .tenantId(message.tenantId())
                    .eventType(message.eventType())
                    .createdAt(LocalDateTime.now())
                    .payload(message.payload())
                    .processed(false)
                    .build();

            mongoTemplate.save(document);
            protocolService.updateStatus(protocolId, ProtocolStatus.COMPLETED);
            log.info("Protocolo {}: Dados brutos salvos com sucesso na Camada Bronze", protocolId);
        } catch (Exception e) {
            log.error("Protocolo {}: Erro ao salvar na Camada Bronze - {}", protocolId, e.getMessage());
            protocolService.updateStatus(protocolId, ProtocolStatus.FAILED);
            throw new RuntimeException("Falha na ingestão", e);
        }
    }
}