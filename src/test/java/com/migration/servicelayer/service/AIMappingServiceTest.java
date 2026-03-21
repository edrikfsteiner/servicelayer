package com.migration.servicelayer.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AIMappingServiceTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    private AIMappingService aiMappingService;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        aiMappingService = new AIMappingService(chatClientBuilder);
    }

    @Test
    void generateMapping_shouldCallChatClientAndReturnContent() {
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("{\"nome\": \"nome_completo\"}");

        String result = aiMappingService.generateMapping("origin(nome_completo VARCHAR)", "target(nome VARCHAR)");

        assertEquals("{\"nome\": \"nome_completo\"}", result);
        verify(chatClientBuilder).build();
    }
}
