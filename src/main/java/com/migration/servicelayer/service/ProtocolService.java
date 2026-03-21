package com.migration.servicelayer.service;

import com.migration.servicelayer.dto.ProtocolResponse;
import com.migration.servicelayer.model.MigrationProtocol;
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

    public String createProtocol(String originTable, String targetTable, long totalRecords) {
        String protocolId = UUID.randomUUID().toString();

        MigrationProtocol protocol = new MigrationProtocol();
        protocol.setId(protocolId);
        protocol.setOriginTable(originTable);
        protocol.setTargetTable(targetTable);
        protocol.setTotalRecords(totalRecords);
        protocol.setStatus(totalRecords == 0 ? ProtocolStatus.COMPLETED : ProtocolStatus.IN_PROGRESS);
        protocol.setCreatedAt(LocalDateTime.now());
        protocol.setUpdatedAt(LocalDateTime.now());

        protocolRepository.save(protocol);
        log.info("Protocolo criado: {} ({} registros)", protocolId, totalRecords);
        return protocolId;
    }

    public void incrementProcessed(String protocolId) {
        protocolRepository.incrementProcessed(protocolId);
        checkCompletion(protocolId);
    }

    public void incrementFailed(String protocolId) {
        protocolRepository.incrementFailed(protocolId);
        checkCompletion(protocolId);
    }

    public ProtocolResponse getStatus(String protocolId) {
        MigrationProtocol p = protocolRepository.findById(protocolId)
                .orElseThrow(() -> new IllegalArgumentException("Protocolo não encontrado: " + protocolId));
        return new ProtocolResponse(
                p.getId(), p.getOriginTable(), p.getTargetTable(),
                p.getTotalRecords(), p.getProcessedRecords(), p.getFailedRecords(),
                p.getStatus(), p.getCreatedAt(), p.getUpdatedAt());
    }

    private void checkCompletion(String protocolId) {
        protocolRepository.findById(protocolId).ifPresent(p -> {
            if (p.getProcessedRecords() + p.getFailedRecords() >= p.getTotalRecords()) {
                protocolRepository.updateStatus(protocolId, ProtocolStatus.COMPLETED);
                log.info("Protocolo {} concluído: {} processados, {} falhas",
                        protocolId, p.getProcessedRecords(), p.getFailedRecords());
            }
        });
    }
}
