package com.migration.servicelayer.controller;

import com.migration.servicelayer.service.DLQService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DLQControllerTest {

    private MockMvc mockMvc;

    private DLQService dlqService;

    @BeforeEach
    void setUp() {
        dlqService = mock(DLQService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DLQController(dlqService)).build();
    }

    @Test
    void count_shouldReturnMessageCount() throws Exception {
        when(dlqService.count()).thenReturn(7L);

        mockMvc.perform(get("/api/dlq/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messagesInDlq").value(7));
    }

    @Test
    void reprocess_shouldReturnReprocessedCount() throws Exception {
        when(dlqService.reprocessAll()).thenReturn(3);

        mockMvc.perform(post("/api/dlq/reprocess"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reprocessed").value(3));
    }

    @Test
    void count_shouldReturnZeroWhenEmpty() throws Exception {
        when(dlqService.count()).thenReturn(0L);

        mockMvc.perform(get("/api/dlq/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messagesInDlq").value(0));
    }
}
