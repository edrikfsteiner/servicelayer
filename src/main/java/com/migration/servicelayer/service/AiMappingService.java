package com.migration.servicelayer.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class AiMappingService {

    private final ChatClient chatClient;

    public AiMappingService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public String gerarMapeamentoDePara(String schemaOrigem, String schemaDestino) {
        String mensagemSistema = """
            Você é um engenheiro de dados senior especialista em migração de sistemas legados.
            Sua função é analisar a estrutura de dados de ORIGEM (sistema legado) e a estrutura de DESTINO (sistema moderno),\s
            e criar um mapeamento 'de-para' exato.
           \s
            REGRAS OBRIGATÓRIAS:
            1. Devolve APENAS um objeto JSON válido.
            2. Não inclua texto explicativo antes ou depois do JSON.
            3. Não inclua blocos de formatação Markdown (como ```json).
            4. As chaves do JSON devem ser os campos de DESTINO e os valores devem ser os campos de ORIGEM correspondentes.
           \s""";

        String mensagemUtilizador = String.format("""
            Schema de ORIGEM:
            %s
            
            Schema de DESTINO:
            %s
            """, schemaOrigem, schemaDestino);

        return this.chatClient.prompt()
                .system(mensagemSistema)
                .user(mensagemUtilizador)
                .call()
                .content();
    }
}