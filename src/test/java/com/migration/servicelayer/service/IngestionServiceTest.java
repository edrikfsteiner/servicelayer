package com.migration.servicelayer.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    @Mock
    private JdbcTemplate originJdbcTemplate;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private ProtocolService protocolService;

    @InjectMocks
    private IngestionService ingestionService;

    @Test
    void startMassMigration_shouldQueryOriginAndPublishMessages() {
        List<Map<String, Object>> rows = List.of(
                Map.of("id", 1, "nome", "João"),
                Map.of("id", 2, "nome", "Maria")
        );
        when(originJdbcTemplate.queryForList("SELECT * FROM clientes_legado")).thenReturn(rows);
        when(protocolService.createProtocol("clientes_legado", "clientes", 2)).thenReturn("proto-abc");

        String protocolId = ingestionService.startMassMigration("clientes_legado", "clientes");

        assertEquals("proto-abc", protocolId);
        verify(originJdbcTemplate).queryForList("SELECT * FROM clientes_legado");
        verify(protocolService).createProtocol("clientes_legado", "clientes", 2);
        verify(rabbitTemplate, times(2)).convertAndSend(
            eq("migration.exchange"), eq("migration.routing.key"), any(Map.class), any(MessagePostProcessor.class));
    }

    @Test
    void startMassMigration_shouldPopulateMessageHeaders() {
        List<Map<String, Object>> rows = List.of(Map.of("id", 1, "nome", "João"));
        when(originJdbcTemplate.queryForList("SELECT * FROM clientes_legado")).thenReturn(rows);
        when(protocolService.createProtocol("clientes_legado", "clientes", 1)).thenReturn("proto-abc");

        ingestionService.startMassMigration("clientes_legado", "clientes");

        ArgumentCaptor<MessagePostProcessor> captor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(
                eq("migration.exchange"), eq("migration.routing.key"), any(Map.class), captor.capture());

        Message message = new Message(new byte[0], new MessageProperties());
        Message processed = captor.getValue().postProcessMessage(message);
        assertEquals("clientes", processed.getMessageProperties().getHeaders().get("targetTable"));
        assertEquals("proto-abc", processed.getMessageProperties().getHeaders().get("protocolId"));
    }

    @Test
    void startMassMigration_shouldRejectInvalidTableName() {
        assertThrows(IllegalArgumentException.class,
                () -> ingestionService.startMassMigration("DROP TABLE x;--", "clientes"));
    }

    @Test
    void startMassMigration_withEmptyTable_shouldCreateProtocolWithZero() {
        when(originJdbcTemplate.queryForList("SELECT * FROM empty_table")).thenReturn(List.of());
        when(protocolService.createProtocol("empty_table", "clientes", 0)).thenReturn("proto-empty");

        String protocolId = ingestionService.startMassMigration("empty_table", "clientes");

        assertEquals("proto-empty", protocolId);
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Map.class), any(MessagePostProcessor.class));
    }
}
