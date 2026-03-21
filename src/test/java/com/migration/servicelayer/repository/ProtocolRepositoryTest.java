package com.migration.servicelayer.repository;

import com.migration.servicelayer.model.MigrationProtocol;
import com.migration.servicelayer.model.ProtocolStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProtocolRepositoryTest {

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private ProtocolRepository protocolRepository;

    @BeforeEach
    void setUp() {
        protocolRepository = new ProtocolRepository(jdbc);
    }

    @Test
    void initSchema_shouldExecuteCreateTable() {
        when(jdbc.getJdbcTemplate()).thenReturn(jdbcTemplate);
        doNothing().when(jdbcTemplate).execute(anyString());

        protocolRepository.initSchema();

        verify(jdbcTemplate).execute(anyString());
    }

    @Test
    void save_shouldPersistProtocol() {
        MigrationProtocol protocol = new MigrationProtocol(
                "proto-1",
                "clientes_legado",
                "clientes",
                10,
                3,
                1,
                ProtocolStatus.IN_PROGRESS,
                LocalDateTime.of(2026, 3, 21, 10, 0),
                LocalDateTime.of(2026, 3, 21, 10, 1)
        );

        protocolRepository.save(protocol);

        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(anyString(), captor.capture());
        MapSqlParameterSource params = captor.getValue();
        assertEquals("proto-1", params.getValue("id"));
        assertEquals("clientes_legado", params.getValue("originTable"));
        assertEquals("clientes", params.getValue("targetTable"));
        assertEquals(10L, params.getValue("totalRecords"));
        assertEquals("IN_PROGRESS", params.getValue("status"));
    }

    @Test
    void findById_shouldReturnMappedProtocolWhenFound() {
        MigrationProtocol expected = new MigrationProtocol(
                "proto-1",
                "clientes_legado",
                "clientes",
                10,
                8,
                2,
                ProtocolStatus.COMPLETED,
                LocalDateTime.of(2026, 3, 21, 10, 0),
                LocalDateTime.of(2026, 3, 21, 10, 1)
        );
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(expected));

        Optional<MigrationProtocol> result = protocolRepository.findById("proto-1");

        assertTrue(result.isPresent());
        assertEquals(expected, result.get());
    }

    @Test
    void findById_shouldExecuteRowMapperAgainstResultSet() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 3, 21, 10, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 3, 21, 10, 1);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<MigrationProtocol> rowMapper = invocation.getArgument(2, RowMapper.class);
                    ResultSet resultSet = org.mockito.Mockito.mock(ResultSet.class);
                    when(resultSet.getString("id")).thenReturn("proto-1");
                    when(resultSet.getString("origin_table")).thenReturn("clientes_legado");
                    when(resultSet.getString("target_table")).thenReturn("clientes");
                    when(resultSet.getLong("total_records")).thenReturn(10L);
                    when(resultSet.getLong("processed_records")).thenReturn(8L);
                    when(resultSet.getLong("failed_records")).thenReturn(2L);
                    when(resultSet.getString("status")).thenReturn("COMPLETED");
                    when(resultSet.getTimestamp("created_at")).thenReturn(Timestamp.valueOf(createdAt));
                    when(resultSet.getTimestamp("updated_at")).thenReturn(Timestamp.valueOf(updatedAt));
                    return List.of(rowMapper.mapRow(resultSet, 0));
                });

        Optional<MigrationProtocol> result = protocolRepository.findById("proto-1");

        assertTrue(result.isPresent());
        assertEquals("proto-1", result.get().getId());
        assertEquals("clientes_legado", result.get().getOriginTable());
        assertEquals("clientes", result.get().getTargetTable());
        assertEquals(10L, result.get().getTotalRecords());
        assertEquals(8L, result.get().getProcessedRecords());
        assertEquals(2L, result.get().getFailedRecords());
        assertEquals(ProtocolStatus.COMPLETED, result.get().getStatus());
        assertEquals(createdAt, result.get().getCreatedAt());
        assertEquals(updatedAt, result.get().getUpdatedAt());
    }

    @Test
    void findById_shouldReturnEmptyWhenNotFound() {
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        Optional<MigrationProtocol> result = protocolRepository.findById("missing");

        assertFalse(result.isPresent());
    }

    @Test
    void incrementProcessed_shouldExecuteUpdate() {
        protocolRepository.incrementProcessed("proto-1");

        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(anyString(), captor.capture());
        assertEquals("proto-1", captor.getValue().getValue("id"));
    }

    @Test
    void incrementFailed_shouldExecuteUpdate() {
        protocolRepository.incrementFailed("proto-1");

        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(anyString(), captor.capture());
        assertEquals("proto-1", captor.getValue().getValue("id"));
    }

    @Test
    void updateStatus_shouldExecuteUpdateWithStatus() {
        protocolRepository.updateStatus("proto-1", ProtocolStatus.COMPLETED);

        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(anyString(), captor.capture());
        assertEquals("proto-1", captor.getValue().getValue("id"));
        assertEquals("COMPLETED", captor.getValue().getValue("status"));
    }
}