package com.migration.servicelayer.worker;

import com.migration.servicelayer.dto.IngestionBatchMessage;
import com.migration.servicelayer.dto.IngestionMessage;
import com.migration.servicelayer.model.BronzeDocument;
import com.migration.servicelayer.model.ProtocolStatus;
import com.migration.servicelayer.service.ProtocolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
@Slf4j
@Service
@RabbitListener(queues = "${app.messaging.queue-main}")
public class IngestionWorker {

    private final MongoTemplate mongoTemplate;
    private final ProtocolService protocolService;

    @RabbitHandler
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
                    .queued(false)
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

    @RabbitHandler
    public void consumeBatch(
            IngestionBatchMessage message,
            @Header(value = "reprocessed", required = false) boolean reprocessed
    ) {
        String protocolId = message.protocolId();

        try {
            if (!reprocessed) {
                protocolService.updateStatus(protocolId, ProtocolStatus.IN_PROGRESS);
            }

            LocalDateTime now = LocalDateTime.now();
            List<BronzeDocument> documents = message.payloads().stream()
                    .map(payload -> BronzeDocument.builder()
                            .protocolId(protocolId)
                            .tenantId(message.tenantId())
                            .eventType(message.eventType())
                            .createdAt(now)
                            .payload(payload)
                            .queued(false)
                            .processed(false)
                            .build())
                    .toList();

            mongoTemplate.insert(documents, BronzeDocument.class);
            protocolService.updateStatus(protocolId, ProtocolStatus.COMPLETED);
            log.info(
                    "Protocolo {}: Lote com {} registros salvo com sucesso na Camada Bronze",
                    protocolId,
                    documents.size()
            );
        } catch (Exception e) {
            log.error("Protocolo {}: Erro ao salvar lote na Camada Bronze - {}", protocolId, e.getMessage());
            protocolService.updateStatus(protocolId, ProtocolStatus.FAILED);
            throw new RuntimeException("Falha na ingestao em lote", e);
        }
    }
}
