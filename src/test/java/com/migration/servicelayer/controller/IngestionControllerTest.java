package com.migration.servicelayer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.migration.servicelayer.dto.MigrationStartRequest;
import com.migration.servicelayer.dto.ProtocolResponse;
import com.migration.servicelayer.model.ProtocolStatus;
import com.migration.servicelayer.service.AIMappingService;
import com.migration.servicelayer.service.MappingStore;
import com.migration.servicelayer.service.IngestionService;
import com.migration.servicelayer.service.ProtocolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IngestionControllerTest {

    private MockMvc mockMvc;

        private ObjectMapper objectMapper;

    private IngestionService ingestionService;

    private AIMappingService aiMappingService;

    private MappingStore mappingStore;

    private ProtocolService protocolService;

        @BeforeEach
        void setUp() {
                objectMapper = new ObjectMapper();
                objectMapper.registerModule(new JavaTimeModule());

                ingestionService = mock(IngestionService.class);
                aiMappingService = mock(AIMappingService.class);
                mappingStore = mock(MappingStore.class);
                protocolService = mock(ProtocolService.class);

                IngestionController controller = new IngestionController(
                        ingestionService,
                                aiMappingService,
                                mappingStore,
                                objectMapper,
                                protocolService
                );

                mockMvc = MockMvcBuilders.standaloneSetup(controller)
                        .setMessageConverters(new JacksonJsonHttpMessageConverter())
                        .build();
        }

    @Test
    void start_shouldReturn202WithProtocolId() throws Exception {
        when(ingestionService.startMassMigration("clientes_legado", "clientes")).thenReturn("proto-abc");

        mockMvc.perform(post("/api/migration/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MigrationStartRequest("clientes_legado", "clientes"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.protocolId").value("proto-abc"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void status_shouldReturnProtocolResponse() throws Exception {
        var response = new ProtocolResponse("proto-abc", "origin", "target",
                10, 8, 2, ProtocolStatus.COMPLETED,
                LocalDateTime.of(2026, 3, 21, 10, 0),
                LocalDateTime.of(2026, 3, 21, 10, 1));
        when(protocolService.getStatus("proto-abc")).thenReturn(response);

        mockMvc.perform(get("/api/migration/status/proto-abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.protocolId").value("proto-abc"))
                .andExpect(jsonPath("$.totalRecords").value(10))
                .andExpect(jsonPath("$.processedRecords").value(8))
                .andExpect(jsonPath("$.failedRecords").value(2))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void getMapping_shouldReturnMappingWhenExists() throws Exception {
        when(mappingStore.getMapping("clientes")).thenReturn(Map.of("nome", "nome_completo"));

        mockMvc.perform(get("/api/migration/mapping/clientes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("nome_completo"));
    }

    @Test
    void getMapping_shouldReturn404WhenNotFound() throws Exception {
        when(mappingStore.getMapping("unknown")).thenReturn(null);

        mockMvc.perform(get("/api/migration/mapping/unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aiMap_shouldGenerateAndSaveMapping() throws Exception {
        when(aiMappingService.generateMapping(anyString(), anyString()))
                .thenReturn("{\"nome\": \"nome_completo\"}");

        mockMvc.perform(post("/api/migration/ai-map")
                        .param("targetTable", "clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originSchema\":\"origin(nome_completo VARCHAR)\",\"targetSchema\":\"target(nome VARCHAR)\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Mapeamento gerado")));

        verify(mappingStore).saveMapping(eq("clientes"), anyMap());
    }

    @Test
    void aiMap_shouldReturn500OnError() throws Exception {
        when(aiMappingService.generateMapping(anyString(), anyString()))
                .thenThrow(new RuntimeException("API offline"));

        mockMvc.perform(post("/api/migration/ai-map")
                        .param("targetTable", "clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originSchema\":\"origin\",\"targetSchema\":\"target\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Erro ao processar com a IA")));
    }
}
