package com.migration.servicelayer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.migration.servicelayer.dto.SilverFieldRule;
import com.migration.servicelayer.model.SilverMappingRules;
import com.migration.servicelayer.repository.SilverMappingRulesRepository;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI Profiler — the "brain" of the Silver Layer.
 *
 * <p>Reads a sample of raw records from {@code bronze_{tenantId}}, sends them to
 * an LLM via Spring AI, and persists the inferred field transformation rules in
 * the {@code silver_rules} collection. The rules are an upsert: re-running the
 * profiler for the same (tenantId, eventType) pair refreshes the rules rather
 * than duplicating them.
 *
 * <p>Zero-DB policy on the hot path: this service is called on-demand (e.g. by a
 * REST endpoint or a one-time setup job) — not inside the transformation loop.
 */
@Slf4j
@Service
public class SilverProfilerService {

    private static final int SAMPLE_SIZE = 50;

    /**
     * System prompt instructs the LLM to return ONLY a bare JSON array — no prose,
     * no markdown fences. The outer try/catch in {@link #callAiForRules} also strips
     * any fences that certain models insert anyway.
     */
    private static final String SYSTEM_PROMPT = """
            Você é um Engenheiro de Dados sênior especializado em NoSQL e Data Lakehouses.

            OBJETIVO: Analisar a amostra de JSON fornecida e gerar um contrato de transformação
            para a Camada Prata (Silver Layer).

            REGRAS:
            1. Identifique todos os campos recorrentes na amostra.
            2. Infira o tipo primitivo correto: STRING, INTEGER, DOUBLE, DATE.
            3. Identifique necessidades de limpeza: campos de texto que se beneficiam de trim,
               remoção de espaços extras ou valores visivelmente sujos.
            4. Marque como nullable=true campos que aparecem ausentes ou nulos em algum registro.
            5. Ignore campos internos de infraestrutura: _id, _processed, protocolId, tenantId,
               eventType, createdAt, _processedAt.

            SAÍDA: Responda APENAS com um array JSON puro, sem explicações, sem markdown,
            sem blocos de código. Cada elemento deve ter exatamente estes campos:
            - "fieldName"  : nome exato do campo no payload
            - "targetType" : STRING | INTEGER | DOUBLE | DATE
            - "trim"       : true se o valor se beneficia de strip de espaços
            - "nullable"   : true se o campo pode estar ausente ou nulo

            Exemplo de saída esperada:
            [
              {"fieldName":"customer_name","targetType":"STRING","trim":true,"nullable":false},
              {"fieldName":"age","targetType":"INTEGER","trim":false,"nullable":false},
              {"fieldName":"price","targetType":"DOUBLE","trim":false,"nullable":true},
              {"fieldName":"order_date","targetType":"DATE","trim":false,"nullable":false}
            ]
            """;

    private final MongoTemplate mongoTemplate;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final SilverMappingRulesRepository silverMappingRulesRepository;

    public SilverProfilerService(MongoTemplate mongoTemplate,
                                  ChatClient.Builder chatClientBuilder,
                                  ObjectMapper objectMapper,
                                  SilverMappingRulesRepository silverMappingRulesRepository) {
        this.mongoTemplate = mongoTemplate;
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.silverMappingRulesRepository = silverMappingRulesRepository;
    }

    /**
     * Profiles a bronze collection for the given tenant and event type.
     *
     * @param tenantId  tenant identifier extracted from the JWT claim
     * @param eventType the {@code X-Event-Type} header value used during ingestion
     * @return the persisted (or updated) {@link SilverMappingRules} document
     * @throws IllegalStateException if no bronze records exist or the AI call fails
     */
    public SilverMappingRules profileBronzeCollection(String tenantId, String eventType) {
        String bronzeCollection = bronzeCollectionName(tenantId);
        log.info("AI profiling started — tenant='{}', eventType='{}', source='{}'",
                tenantId, eventType, bronzeCollection);

        List<Document> samples = mongoTemplate.find(
                Query.query(Criteria.where("eventType").is(eventType)).limit(SAMPLE_SIZE),
                Document.class,
                bronzeCollection
        );

        if (samples.isEmpty()) {
            throw new IllegalStateException(
                    "No bronze records found for tenant='%s', eventType='%s'".formatted(tenantId, eventType));
        }

        List<SilverFieldRule> rules = callAiForRules(samples, tenantId, eventType);

        // Upsert: refresh existing rules rather than creating duplicates.
        LocalDateTime now = LocalDateTime.now();
        SilverMappingRules mappingRules = silverMappingRulesRepository
                .findByTenantIdAndEventType(tenantId, eventType)
                .orElseGet(SilverMappingRules::new);

        mappingRules.setTenantId(tenantId);
        mappingRules.setEventType(eventType);
        mappingRules.setFields(rules);
        mappingRules.setUpdatedAt(now);
        if (mappingRules.getCreatedAt() == null) {
            mappingRules.setCreatedAt(now);
        }

        SilverMappingRules saved = silverMappingRulesRepository.save(mappingRules);
        log.info("AI profiling complete — tenant='{}', {} field rule(s) persisted.", tenantId, rules.size());
        return saved;
    }

    // -------------------------------------------------------------------------
    // private helpers
    // -------------------------------------------------------------------------

    private List<SilverFieldRule> callAiForRules(List<Document> samples,
                                                  String tenantId,
                                                  String eventType) {
        try {
            String sampleJson = objectMapper.writeValueAsString(samples);
            String userPrompt = """
                    Tenant: %s | EventType: %s
                    Sample records (up to %d documents):
                    %s
                    """.formatted(tenantId, eventType, SAMPLE_SIZE, sampleJson);

            String rawResponse = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .content();

            String json = stripMarkdownFences(rawResponse);
            List<SilverFieldRule> rules = objectMapper.readValue(json, new TypeReference<>() {});
            if (rules.isEmpty()) {
                throw new IllegalStateException("AI returned an empty rule set.");
            }
            return rules;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "AI profiling failed for tenant='%s': %s".formatted(tenantId, e.getMessage()), e);
        }
    }

    /**
     * Some models wrap the JSON in {@code ```json ... ```} fences even when asked
     * not to. This method strips the fences so Jackson can parse the response.
     */
    private String stripMarkdownFences(String raw) {
        String trimmed = raw.strip();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            int endFence = trimmed.lastIndexOf("```");
            if (endFence >= 0) {
                trimmed = trimmed.substring(0, endFence).strip();
            }
        }
        return trimmed;
    }

    /**
     * Converts tenantId to a safe MongoDB collection name suffix.
     * Any character that is not alphanumeric or underscore is replaced with {@code _}.
     * This is defensive: tenantId comes from the JWT but its format is unconstrained.
     */
    static String bronzeCollectionName(String tenantId) {
        return "bronze_" + tenantId.replaceAll("[^a-zA-Z0-9_]", "_");
    }
}
