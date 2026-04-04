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

    public String generateMapping(String payloadJsonSample, String lakehouseSchema) {
        String systemMessage = """
            Você é um engenheiro de dados senior especialista em arquitetura Data Lakehouse e ingestão de dados.
            Sua função é analisar um JSON de exemplo enviado por um sistema terceiro (payload) e
            a estrutura de uma tabela de destino no Lakehouse (schema), e criar um mapeamento 'de-para' exato.
            
            REGRAS OBRIGATÓRIAS:
            1. Devolve APENAS um objeto JSON válido.
            2. Não inclua texto explicativo antes ou depois do JSON.
            3. Não inclua blocos de formatação Markdown (como ```json).
            4. As chaves do JSON devem ser as colunas do LAKEHOUSE (destino) e os valores devem ser as chaves do
            JSON DE ENTRADA correspondentes (usando notação de ponto se houver aninhamento, ex: 'cliente.endereco.rua').
            """;

        String userMessage = String.format("""
            Exemplo de JSON Recebido (Payload):
            %s
            
            Schema do Lakehouse (Destino):
            %s
            """, payloadJsonSample, lakehouseSchema
        );

        return this.chatClient.prompt()
                .system(systemMessage)
                .user(userMessage)
                .call()
                .content();
    }
}