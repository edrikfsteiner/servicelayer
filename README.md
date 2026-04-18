# servicelayer — Histórico de Alterações

---

## Commit `616a775` · Rate Limiting por Tenant

> Add Bucket4j and Caffeine Cache to implement per-tenant Rate Limiting on the `/api/ingest` endpoint.

### Dependências adicionadas

| Artefato | Versão |
|---|---|
| `com.bucket4j:bucket4j-core` | 8.10.1 |
| `com.github.ben-manes.caffeine:caffeine` | 3.2.0 |

### Configuração (`application.yaml`)

```yaml
rate-limit:
  capacity: 1000   # requisições por janela por tenant
  minutes: 1       # tamanho da janela em minutos
```

### Arquivos criados

| Arquivo | O que faz |
|---|---|
| `config/RateLimitConfig.java` | Cria `LoadingCache<String, Bucket>` (Caffeine, expira em 1 h de inatividade). Desabilita auto-registro do filtro como servlet raw via `FilterRegistrationBean.setEnabled(false)`. |
| `service/RateLimitingService.java` | Encapsula `cache.get(tenantId)` — cria bucket atomicamente para novos tenants. Zero acesso a DB. |
| `security/filter/RateLimitFilter.java` | `OncePerRequestFilter` para `/api/ingest/**`. Lê `tenantId` do `JwtAuthenticationToken` no `SecurityContext`. Retorna HTTP `429` + header `X-Rate-Limit-Retry-After-Seconds` se bucket esgotado. |

### Arquivos modificados

**`config/SecurityConfig.java`**
- Injetou `RateLimitFilter` no `filterChain()`
- Adicionou `.addFilterAfter(rateLimitFilter, BearerTokenAuthenticationFilter.class)` — filtro roda dentro da cadeia de segurança, após autenticação JWT

**`AGENTS.md`** — atualizado package map e convenções de rate limiting

### Testes

| Arquivo | Casos cobertos |
|---|---|
| `service/RateLimitingServiceTest.java` | Permite exatamente 1000 · bloqueia o 1001º · isolamento entre tenants · mesmo bucket no cache · `getNanosToWaitForRefill` positivo ao esgotar |

---

## Commit `b84e57e` · Camada Silver (Processamento e Higienização)

> Camada Silver com arquitetura de metadados dinâmica: AI Profiler + Transformation Scheduler.

### Dependências adicionadas

| Artefato | Versão |
|---|---|
| `org.springframework.ai:spring-ai-openai-spring-boot-starter` | 1.0.0 |

### Configuração (`application.yaml`)

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o-mini

silver:
  scheduler:
    fixed-delay-ms: 300000  # 5 minutos entre execuções
```

### `Application.java`
- Adicionou `@EnableScheduling`

### Arquivos criados

| Arquivo | O que faz |
|---|---|
| `dto/SilverFieldRule.java` | Record imutável: `fieldName`, `targetType` (`STRING\|INTEGER\|DOUBLE\|DATE`), `trim`, `nullable`. |
| `model/SilverMappingRules.java` | Documento MongoDB (`silver_rules`). Índice composto único em `(tenantId, eventType)` previne duplicatas. |
| `repository/SilverMappingRulesRepository.java` | `MongoRepository` com `findByTenantIdAndEventType` para upsert. |
| `service/SilverProfilerService.java` | **Cérebro.** Lê 50 amostras da `bronze_{tenantId}` → envia à LLM via `ChatClient` → parseia JSON retornado → upsert em `silver_rules`. |
| `service/SilverTransformationScheduler.java` | **Músculo.** `@Scheduled(fixedDelay)` → itera rule sets → batches de 10 000 via Virtual Threads → aplica `coerce()` por tipo → insere em `silver_{tenantId}` → marca bronze com `_processed: true`. |

### Arquivos modificados

**`worker/IngestionWorker.java`**
- Insere na coleção `bronze_{tenantId}` (isolamento por tenant)
- Adiciona campo `_processed: false` em cada documento ao ingeri-lo

### Testes

| Arquivo | Casos cobertos |
|---|---|
| `service/SilverTransformationSchedulerTest.java` | Fluxo completo · skip sem registros · isolamento de falha entre tenants · campo nullable · default para não-nullable · fallback em erro de coerção · matrix de tipos · trim antes de conversão · sanitização de nome de coleção com hífen |

---

## Passo a passo — Teste End-to-End com dataset real

### 1. Dataset sugerido

**Tema:** E-commerce Orders (Amazon Sales Dataset)  
**Kaggle:** [`mkechinov/ecommerce-behavior-data-from-multi-category-store`](https://www.kaggle.com/datasets/mkechinov/ecommerce-behavior-data-from-multi-category-store)  
**Formato:** CSV — converta para JSON antes de ingerir (ou use qualquer dataset `.json` do Kaggle)

Campos típicos do payload após conversão:
```json
{
  "event_time": "2019-10-01 00:00:00 UTC",
  "event_type": "view",
  "product_id": 44600062,
  "category_id": 2103807459595387724,
  "category_code": "electronics.smartphone",
  "brand": "samsung",
  "price": 489.07,
  "user_id": 541312140,
  "user_session": "72d76fde-8bb3-4e00-8c23-a032dfed738c"
}
```

---

### 2. Serviços que precisam estar no ar

```bash
# RabbitMQ
docker-compose up -d

# Verificar: http://localhost:15672  (admin / admin123)
```
---

### 3. Variáveis de ambiente obrigatórias

```bash
export JWT_SECRET="minimo-32-chars"
export OPENAI_API_KEY="sk-..."
```
---

### 4. Subir a aplicação

```bash
./mvnw spring-boot:run
```

---

### 5. Autenticar e obter o JWT

```bash
curl -s -X POST http://localhost:8080/api/auth \
  -H "Content-Type: application/json" \
  -d '{"clientId":"cliente-teste","clientSecret":"senha123"}'
```

Guarde o `accessToken` retornado:
```bash
TOKEN="<cole_o_accessToken_aqui>"
```

---

### 6. Ingerir registros do dataset

> Envie alguns registros individualmente ou em loop. O `tenantId` vem do JWT —

```bash
curl -s -X POST http://localhost:8080/api/ingest \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "X-Event-Type: ecommerce_event" \
  -d '{
    "event_time": "2019-10-01 00:00:00 UTC",
    "event_type": "view",
    "product_id": 44600062,
    "category_code": "electronics.smartphone",
    "brand": "samsung",
    "price": 489.07,
    "user_id": 541312140,
    "user_session": "72d76fde-8bb3-4e00-8c23-a032dfed738c"
  }'
```

Para enviar vários registros em loop (bash):
```bash
for i in $(seq 1 20); do
  curl -s -X POST http://localhost:8080/api/ingest \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -H "X-Event-Type: ecommerce_event" \
    -d "{\"product_id\": $i, \"brand\": \"brand_$i\", \"price\": $(echo "$i * 9.99" | bc)}" \
    > /dev/null
done
echo "20 registros ingeridos."
```

---

### 7. Disparar o AI Profiler manualmente

O profiler ainda não tem endpoint REST — acione-o pelo Swagger UI em `http://localhost:8080/swagger-ui.html`,  
ou adicione uma chamada temporária a `SilverProfilerService.profileBronzeCollection()` via um `CommandLineRunner` de teste.

Alternativamente, aguarde o scheduler rodar (máximo 5 min) —verificar as regras em `silver_rules`, ele processa automaticamente.

---

### 8. Verificar resultados no MongoDB

No Atlas (ou Compass), verifique as coleções:

| Coleção | O que esperar |
|---|---|
| `bronze_empresa_teste` | Documentos com `_processed: false` (antes) e `true` (após scheduler) |
| `silver_rules` | Um documento com os campos inferidos pela LLM para `ecommerce_event` |
| `silver_empresa_teste` | Documentos transformados com tipos corretos e campo `_bronzeId` |

---

### 9. Conferir limite de rate (429)

```bash
# Dispara 1001 requisições em paralelo e conta as respostas 429
for i in $(seq 1 1001); do
  curl -s -o /dev/null -w "%{http_code}\n" \
    -X POST http://localhost:8080/api/ingest \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -H "X-Event-Type: ecommerce_event" \
    -d '{"product_id": 1}' &
done | sort | uniq -c
```

Espera-se: 1000 respostas `202` e pelo menos 1 resposta `429`.
