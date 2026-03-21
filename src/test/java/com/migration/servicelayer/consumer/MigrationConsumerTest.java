package com.migration.servicelayer.consumer;

import com.migration.servicelayer.service.MappingStore;
import com.migration.servicelayer.service.ProtocolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MigrationConsumerTest {

    @Mock
    private NamedParameterJdbcTemplate targetJdbcTemplate;

    @Mock
    private MappingStore mappingStore;

    @Mock
    private ProtocolService protocolService;

    @InjectMocks
    private MigrationConsumer consumer;

    private Map<String, Object> originPayload;
    private Map<String, String> mapping;

    @BeforeEach
    void setUp() {
        originPayload = new HashMap<>(Map.of(
                "id", 1,
                "nome_completo", "João Silva",
                "email_contato", "joao@email.com"
        ));

        mapping = Map.of(
                "nome", "nome_completo",
                "email", "email_contato"
        );
    }

    @Test
    void processarMensagem_shouldTransformAndInsert() {
        when(mappingStore.getMapping("clientes")).thenReturn(mapping);
        when(targetJdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        consumer.processarMensagem(originPayload, "clientes", "proto-1", null);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(targetJdbcTemplate).update(sqlCaptor.capture(), paramsCaptor.capture());

        String sql = sqlCaptor.getValue();
        assertTrue(sql.startsWith("INSERT INTO clientes"));
        assertTrue(sql.contains("nome"));
        assertTrue(sql.contains("email"));

        verify(protocolService).incrementProcessed("proto-1");
        verify(protocolService, never()).incrementFailed(anyString());
    }

    @Test
    void processarMensagem_shouldIncrementFailedOnError() {
        when(mappingStore.getMapping("clientes")).thenReturn(mapping);
        when(targetJdbcTemplate.update(anyString(), any(MapSqlParameterSource.class)))
                .thenThrow(new RuntimeException("DB error"));

        assertThrows(RuntimeException.class,
                () -> consumer.processarMensagem(originPayload, "clientes", "proto-1", null));

        verify(protocolService).incrementFailed("proto-1");
        verify(protocolService, never()).incrementProcessed(anyString());
    }

    @Test
    void processarMensagem_shouldThrowWhenMappingNotFound() {
        when(mappingStore.getMapping("unknown")).thenReturn(null);

        assertThrows(RuntimeException.class,
                () -> consumer.processarMensagem(originPayload, "unknown", "proto-1", null));

        verify(protocolService).incrementFailed("proto-1");
    }

    @Test
    void processarMensagem_shouldRejectInvalidTableName() {
        assertThrows(IllegalArgumentException.class,
                () -> consumer.processarMensagem(originPayload, "DROP TABLE;", "proto-1", null));
    }

    @Test
    void processarMensagem_reprocessed_shouldNotUpdateProtocol() {
        when(mappingStore.getMapping("clientes")).thenReturn(mapping);
        when(targetJdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        consumer.processarMensagem(originPayload, "clientes", "proto-1", true);

        verify(protocolService, never()).incrementProcessed(anyString());
        verify(protocolService, never()).incrementFailed(anyString());
    }

    @Test
    void processarMensagem_reprocessedFailure_shouldNotUpdateProtocol() {
        when(mappingStore.getMapping("clientes")).thenReturn(mapping);
        when(targetJdbcTemplate.update(anyString(), any(MapSqlParameterSource.class)))
                .thenThrow(new RuntimeException("DB error"));

        assertThrows(RuntimeException.class,
                () -> consumer.processarMensagem(originPayload, "clientes", "proto-1", true));

        verify(protocolService, never()).incrementFailed(anyString());
        verify(protocolService, never()).incrementProcessed(anyString());
    }

    @Test
    void processarMensagem_shouldIgnoreMissingOriginColumns() {
        Map<String, String> partialMapping = Map.of(
                "nome", "nome_completo",
                "documento", "cpf"
        );
        when(mappingStore.getMapping("clientes")).thenReturn(partialMapping);
        when(targetJdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        consumer.processarMensagem(originPayload, "clientes", "proto-1", null);

        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(targetJdbcTemplate).update(anyString(), paramsCaptor.capture());
        assertEquals("João Silva", paramsCaptor.getValue().getValue("nome"));
        assertThrows(IllegalArgumentException.class, () -> paramsCaptor.getValue().getValue("documento"));
        verify(protocolService).incrementProcessed("proto-1");
    }
}
