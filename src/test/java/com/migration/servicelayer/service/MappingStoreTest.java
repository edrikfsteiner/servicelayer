package com.migration.servicelayer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MappingStoreTest {

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private MappingStore mappingStore;

    @BeforeEach
    void setUp() {
        mappingStore = new MappingStore(jdbc, objectMapper);
    }

    @Test
    void init_shouldCreateSchemaAndLoadMappingsIntoCache() throws Exception {
        when(jdbc.getJdbcTemplate()).thenReturn(jdbcTemplate);
        doNothing().when(jdbcTemplate).execute(anyString());
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(Map.of("nome", "nome_completo"));
        when(jdbc.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RowMapper<Object> rowMapper = invocation.getArgument(1, RowMapper.class);
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.getString("target_table")).thenReturn("clientes");
            when(resultSet.getString("mapping_json")).thenReturn("{\"nome\":\"nome_completo\"}");
            rowMapper.mapRow(resultSet, 0);
            return List.of();
        });

        mappingStore.init();

        verify(jdbcTemplate).execute(anyString());
        assertEquals(Map.of("nome", "nome_completo"), mappingStore.getMapping("clientes"));
    }

    @Test
    void saveMapping_shouldPersistAndUpdateCache() throws Exception {
        Map<String, String> mapping = Map.of("nome", "nome_completo");
        when(objectMapper.writeValueAsString(mapping)).thenReturn("{\"nome\":\"nome_completo\"}");

        mappingStore.saveMapping("clientes", mapping);

        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(anyString(), captor.capture());
        MapSqlParameterSource params = captor.getValue();
        assertEquals("clientes", params.getValue("targetTable"));
        assertEquals("{\"nome\":\"nome_completo\"}", params.getValue("json"));
        assertSame(mapping, mappingStore.getMapping("clientes"));
    }

    @Test
    void saveMapping_shouldWrapSerializationErrors() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("boom"));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> mappingStore.saveMapping("clientes", Map.of("nome", "nome_completo")));

        assertNotNull(exception.getCause());
        assertEquals("Erro ao salvar contrato: boom", exception.getMessage());
    }

    @Test
    void init_shouldSkipInvalidMappingsFromDatabase() throws Exception {
        when(jdbc.getJdbcTemplate()).thenReturn(jdbcTemplate);
        doNothing().when(jdbcTemplate).execute(anyString());
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenThrow(new RuntimeException("json inválido"));
        when(jdbc.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RowMapper<Object> rowMapper = invocation.getArgument(1, RowMapper.class);
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.getString("target_table")).thenReturn("clientes");
            when(resultSet.getString("mapping_json")).thenReturn("not-json");
            rowMapper.mapRow(resultSet, 0);
            return List.of();
        });

        mappingStore.init();

        assertEquals(null, mappingStore.getMapping("clientes"));
    }
}