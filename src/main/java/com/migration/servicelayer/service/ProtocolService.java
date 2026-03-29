package com.migration.servicelayer.service;

import com.migration.servicelayer.dto.ProtocolResponse;
import com.migration.servicelayer.model.IngestionProtocol;
import com.migration.servicelayer.model.ProtocolStatus;
import com.migration.servicelayer.repository.ProtocolRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
public class ProtocolService {

    private final ProtocolRepository protocolRepository;

    public ProtocolService(ProtocolRepository protocolRepository) {
        this.protocolRepository = protocolRepository;
    }

    public String createProtocol(String tenantId, String eventType) {
        String protocolId = UUID.randomUUID().toString();

        IngestionProtocol protocol = new IngestionProtocol();
        protocol.setId(protocolId);
        protocol.setTenantId(tenantId);
        protocol.setEventType(eventType);
        protocol.setStatus(ProtocolStatus.QUEUED);
        protocol.setCreatedAt(LocalDateTime.now());
        protocol.setUpdatedAt(LocalDateTime.now());

        protocolRepository.save(protocol);
        log.info("Protocolo de ingestão criado: {} (Tenant: {}, Evento: {})", protocolId, tenantId, eventType);
        return protocolId;
    }

    public ProtocolResponse getStatus(String protocolId) {
        IngestionProtocol protocol = protocolRepository.findById(protocolId)
                .orElseThrow(() -> new IllegalArgumentException("Protocolo não encontrado: " + protocolId));

        return new ProtocolResponse(
                protocol.getId(), protocol.getTenantId(), protocol.getEventType(),
                protocol.getStatus(), protocol.getCreatedAt(), protocol.getUpdatedAt()
        );
    }

    public void updateStatus(String protocolId, ProtocolStatus status) {
        protocolRepository.updateStatus(protocolId, status);
        log.info("Protocolo {} atualizado para o status: {}", protocolId, status);
    }
}
