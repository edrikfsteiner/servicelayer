package com.migration.servicelayer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AIMappingService {

    private final ChatClient chatClient;

    public AIMappingService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public String generateMapping(String originSchema, String targetSchema) {
        String systemMessage = """
            Você é um engenheiro de dados senior especialista em migração de sistemas legados.
            Sua função é analisar a estrutura de dados de ORIGEM (sistema legado) e a estrutura de DESTINO (sistema moderno),\s
            e criar um mapeamento 'de-para' exato.
            \s
            REGRAS OBRIGATÓRIAS:
            1. Devolve APENAS um objeto JSON válido.
            2. Não inclua texto explicativo antes ou depois do JSON.
            3. Não inclua blocos de formatação Markdown (como ```json).
            4. As chaves do JSON devem ser as colunas de DESTINO (sem mencionar a tabela) e os valores devem ser as colunas de ORIGEM correspondentes (sem mencionar a tabela).
            \s""";

        String userMessage = String.format("""
            Schema de ORIGEM:
            %s
            
            Schema de DESTINO:
            %s
            """, originSchema, targetSchema
        );

        return this.chatClient.prompt()
                .system(systemMessage)
                .user(userMessage)
                .call()
                .content();
    }
}