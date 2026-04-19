package com.migration.servicelayer.service;

import com.migration.servicelayer.dto.ProtocolResponse;
import com.migration.servicelayer.model.IngestionProtocol;
import com.migration.servicelayer.model.ProtocolStatus;
import com.migration.servicelayer.repository.ProtocolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@RequiredArgsConstructor
@Slf4j
@Service
public class ProtocolService {

    private final ProtocolRepository protocolRepository;

    public String createProtocol(String tenantId, String eventType) {
        String protocolId = UUID.randomUUID().toString();

        IngestionProtocol protocol = IngestionProtocol.builder()
                .id(protocolId)
                .tenantId(tenantId)
                .eventType(eventType)
                .status(ProtocolStatus.QUEUED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        protocolRepository.save(protocol);
        log.info("Protocolo de ingestão criado: {} (Tenant: {}, Evento: {})", protocolId, tenantId, eventType);
        return protocolId;
    }

    public ProtocolResponse getStatus(String protocolId, String tenantId) {
        IngestionProtocol protocol = protocolRepository.findByIdAndTenantId(protocolId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Protocolo não encontrado"));

        return new ProtocolResponse(
                protocol.getId(),
                protocol.getTenantId(),
                protocol.getEventType(),
                protocol.getStatus(),
                protocol.getCreatedAt(),
                protocol.getUpdatedAt()
        );
    }

    public void updateStatus(String protocolId, ProtocolStatus status) {
        protocolRepository.updateStatus(protocolId, status, LocalDateTime.now());
        log.info("Protocolo {} atualizado para o status: {}", protocolId, status);
    }
}
