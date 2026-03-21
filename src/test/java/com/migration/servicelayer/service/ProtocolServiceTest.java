package com.migration.servicelayer.service;

import com.migration.servicelayer.dto.ProtocolResponse;
import com.migration.servicelayer.model.MigrationProtocol;
import com.migration.servicelayer.model.ProtocolStatus;
import com.migration.servicelayer.repository.ProtocolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProtocolServiceTest {

    @Mock
    private ProtocolRepository protocolRepository;

    @InjectMocks
    private ProtocolService protocolService;

    private MigrationProtocol sampleProtocol;

    @BeforeEach
    void setUp() {
        sampleProtocol = new MigrationProtocol();
        sampleProtocol.setId("proto-123");
        sampleProtocol.setOriginTable("clientes_legado");
        sampleProtocol.setTargetTable("clientes");
        sampleProtocol.setTotalRecords(5);
        sampleProtocol.setProcessedRecords(0);
        sampleProtocol.setFailedRecords(0);
        sampleProtocol.setStatus(ProtocolStatus.IN_PROGRESS);
        sampleProtocol.setCreatedAt(LocalDateTime.now());
        sampleProtocol.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    void createProtocol_shouldSaveAndReturnId() {
        String protocolId = protocolService.createProtocol("origin_tbl", "target_tbl", 10);

        assertNotNull(protocolId);
        assertFalse(protocolId.isBlank());

        ArgumentCaptor<MigrationProtocol> captor = ArgumentCaptor.forClass(MigrationProtocol.class);
        verify(protocolRepository).save(captor.capture());

        MigrationProtocol saved = captor.getValue();
        assertEquals("origin_tbl", saved.getOriginTable());
        assertEquals("target_tbl", saved.getTargetTable());
        assertEquals(10, saved.getTotalRecords());
        assertEquals(ProtocolStatus.IN_PROGRESS, saved.getStatus());
    }

    @Test
    void createProtocol_withZeroRecords_shouldSetCompleted() {
        protocolService.createProtocol("origin_tbl", "target_tbl", 0);

        ArgumentCaptor<MigrationProtocol> captor = ArgumentCaptor.forClass(MigrationProtocol.class);
        verify(protocolRepository).save(captor.capture());

        assertEquals(ProtocolStatus.COMPLETED, captor.getValue().getStatus());
    }

    @Test
    void incrementProcessed_shouldCallRepoAndCheckCompletion() {
        sampleProtocol.setProcessedRecords(4);
        when(protocolRepository.findById("proto-123")).thenReturn(Optional.of(sampleProtocol));

        protocolService.incrementProcessed("proto-123");

        verify(protocolRepository).incrementProcessed("proto-123");
        verify(protocolRepository).findById("proto-123");
    }

    @Test
    void incrementProcessed_shouldMarkCompleteWhenAllDone() {
        sampleProtocol.setTotalRecords(3);
        sampleProtocol.setProcessedRecords(3);
        sampleProtocol.setFailedRecords(0);
        when(protocolRepository.findById("proto-123")).thenReturn(Optional.of(sampleProtocol));

        protocolService.incrementProcessed("proto-123");

        verify(protocolRepository).updateStatus("proto-123", ProtocolStatus.COMPLETED);
    }

    @Test
    void incrementFailed_shouldCallRepoAndCheckCompletion() {
        sampleProtocol.setFailedRecords(4);
        when(protocolRepository.findById("proto-123")).thenReturn(Optional.of(sampleProtocol));

        protocolService.incrementFailed("proto-123");

        verify(protocolRepository).incrementFailed("proto-123");
        verify(protocolRepository).findById("proto-123");
    }

    @Test
    void getStatus_shouldReturnProtocolResponse() {
        when(protocolRepository.findById("proto-123")).thenReturn(Optional.of(sampleProtocol));

        ProtocolResponse response = protocolService.getStatus("proto-123");

        assertEquals("proto-123", response.protocolId());
        assertEquals("clientes_legado", response.originTable());
        assertEquals("clientes", response.targetTable());
        assertEquals(5, response.totalRecords());
        assertEquals(ProtocolStatus.IN_PROGRESS, response.status());
    }

    @Test
    void getStatus_shouldThrowWhenNotFound() {
        when(protocolRepository.findById("unknown")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> protocolService.getStatus("unknown"));
    }
}
