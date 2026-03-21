# Guia de Validação — Passo a Passo

Este guia descreve como subir o ambiente e validar cada funcionalidade do sistema de migração.

---

## 1. Subir a Infraestrutura

```bash
docker-compose up -d
```

Aguarde todos os containers ficarem saudáveis:

```bash
docker ps
```

Você deve ver 3 containers rodando: `origin-db`, `target-db`, `rabbitmq`.

> **RabbitMQ Management:** acesse http://localhost:15672 (login: `admin` / `admin123`)

---

## 2. Preparar os Bancos de Dados

### 2.1 Criar tabela de ORIGEM (MySQL)

```bash
docker exec -i origin-db mysql -uroot -proot origin_db
```

```sql
CREATE TABLE clientes_legado (
    id INT PRIMARY KEY AUTO_INCREMENT,
    nome_completo VARCHAR(255),
    email_contato VARCHAR(255),
    telefone VARCHAR(50),
    data_cadastro DATE
);

INSERT INTO clientes_legado (nome_completo, email_contato, telefone, data_cadastro) VALUES
('João Silva', 'joao@email.com', '11999990001', '2020-01-15'),
('Maria Souza', 'maria@email.com', '11999990002', '2021-06-20'),
('Carlos Lima', 'carlos@email.com', '11999990003', '2022-03-10');
```

### 2.2 Criar tabela de DESTINO (PostgreSQL)

```bash
docker exec -i target-db psql -U postgres -d target_db
```

```sql
CREATE TABLE clientes (
    id SERIAL PRIMARY KEY,
    nome VARCHAR(255),
    email VARCHAR(255),
    telefone VARCHAR(50),
    criado_em DATE
);
```

---

## 3. Iniciar a Aplicação

Defina a variável de ambiente com sua chave da API OpenAI e rode a aplicação:

```bash
export OPENAI_API_KEY=sua-chave-aqui
./mvnw spring-boot:run
```

A aplicação sobe em http://localhost:8080.  
O Swagger UI fica disponível em http://localhost:8080/swagger-ui.html.

---

## 4. Design Time — Gerar Contrato de Mapeamento com IA (RF04 + RF05)

Envie os schemas de origem e destino para a IA gerar o mapeamento:

```bash
curl -X POST "http://localhost:8080/api/migration/ai-map?targetTable=clientes" \
  -H "Content-Type: application/json" \
  -d '{
    "originSchema": "clientes_legado(id INT, nome_completo VARCHAR, email_contato VARCHAR, telefone VARCHAR, data_cadastro DATE)",
    "targetSchema": "clientes(id SERIAL, nome VARCHAR, email VARCHAR, telefone VARCHAR, criado_em DATE)"
  }'
```

**Resposta esperada:** JSON com o mapeamento de-para, por exemplo:
```json
{
  "id": "id",
  "nome": "nome_completo",
  "email": "email_contato",
  "telefone": "telefone",
  "criado_em": "data_cadastro"
}
```

---

## 5. Validar o Contrato Antes de Migrar (RN02)

Inspecione o contrato gerado:

```bash
curl http://localhost:8080/api/migration/mapping/clientes
```

**Validação:** confira se cada campo de destino aponta para o campo de origem correto. Se o mapeamento estiver errado, chame o `/ai-map` novamente — o contrato será sobrescrito.

---

## 6. Runtime — Iniciar a Migração em Massa (RF01 + RF02 + RF03)

```bash
curl -X POST http://localhost:8080/api/migration/start \
  -H "Content-Type: application/json" \
  -d '{
    "originTable": "clientes_legado",
    "targetTable": "clientes"
  }'
```

**Resposta esperada (202 Accepted):**
```json
{
  "protocolId": "a1b2c3d4-...",
  "message": "Migração iniciada de clientes_legado para clientes"
}
```

> **Guarde o `protocolId`** — ele será usado nos próximos passos.

---

## 7. Consultar Status da Migração (RF11 + RN03)

```bash
curl http://localhost:8080/api/migration/status/{protocolId}
```

**Resposta esperada:**
```json
{
  "protocolId": "a1b2c3d4-...",
  "originTable": "clientes_legado",
  "targetTable": "clientes",
  "totalRecords": 3,
  "processedRecords": 3,
  "failedRecords": 0,
  "status": "COMPLETED",
  "createdAt": "2026-03-21T10:30:00",
  "updatedAt": "2026-03-21T10:30:01"
}
```

**Validação da RN03:** `processedRecords + failedRecords == totalRecords` e status = `COMPLETED`.

---

## 8. Verificar Dados no Destino (RF07 + RF08)

```bash
docker exec -i target-db psql -U postgres -d target_db -c "SELECT * FROM clientes;"
```

**Resultado esperado:**

| id | nome | email | telefone | criado_em |
|----|------|-------|----------|-----------|
| 1 | João Silva | joao@email.com | 11999990001 | 2020-01-15 |
| 2 | Maria Souza | maria@email.com | 11999990002 | 2021-06-20 |
| 3 | Carlos Lima | carlos@email.com | 11999990003 | 2022-03-10 |

---

## 9. Testar a DLQ — Simular Falha (RF09 + RN01)

Para forçar um erro, inicie uma migração **sem** ter salvado o contrato de mapeamento:

```bash
curl -X POST http://localhost:8080/api/migration/start \
  -H "Content-Type: application/json" \
  -d '{
    "originTable": "clientes_legado",
    "targetTable": "tabela_inexistente"
  }'
```

Os registros vão falhar no consumer (contrato não encontrado) e serão enviados para a DLQ.

### 9.1 Verificar quantidade na DLQ

```bash
curl http://localhost:8080/api/dlq/count
```

```json
{ "messagesInDlq": 3 }
```

### 9.2 Consultar o protocolo (deve ter falhas)

```bash
curl http://localhost:8080/api/migration/status/{protocolId}
```

```json
{
  "totalRecords": 3,
  "processedRecords": 0,
  "failedRecords": 3,
  "status": "COMPLETED"
}
```

---

## 10. Reprocessar a DLQ (RF10)

Primeiro salve o contrato correto para a tabela, depois reprocesse:

```bash
curl -X POST "http://localhost:8080/api/migration/ai-map?targetTable=tabela_inexistente" \
  -H "Content-Type: application/json" \
  -d '{
    "originSchema": "...",
    "targetSchema": "..."
  }'
```

```bash
curl -X POST http://localhost:8080/api/dlq/reprocess
```

```json
{ "reprocessed": 3 }
```

---

## 11. Verificar Logs no Terminal (RF12)

Enquanto a migração roda, observe os logs no terminal da aplicação:

```
INFO  c.m.s.service.ProtocolService  - Protocolo a1b2c3d4-...: 3 registros
INFO  c.m.s.consumer.MigrationConsumer - Protocolo a1b2c3d4-...: registro inserido em 'clientes'
INFO  c.m.s.service.ProtocolService  - Protocolo a1b2c3d4-... concluído: 3 processados, 0 falhas
```

---

## 12. Checklist Final de Requisitos

| Requisito | Como validar | Passo |
|---|---|---|
| **RF01** | POST `/start` aceita lote | 6 |
| **RF02** | Resposta retorna `protocolId` | 6 |
| **RF03** | Registros aparecem na fila do RabbitMQ (http://localhost:15672) | 6 |
| **RF04** | POST `/ai-map` envia schema para IA | 4 |
| **RF05** | Resposta contém JSON de mapeamento | 4 |
| **RF06** | Consumer processa automaticamente | 7 |
| **RF07** | Dados transformados (nomes de coluna trocados) | 8 |
| **RF08** | Dados chegam no PostgreSQL | 8 |
| **RF09** | Mensagens com erro vão para a DLQ | 9 |
| **RF10** | POST `/dlq/reprocess` funciona | 10 |
| **RF11** | GET `/status/{id}` retorna progresso | 7 |
| **RF12** | Logs no terminal com SLF4J | 11 |
| **RNF04** | Virtual Threads ativas (log do Spring Boot no startup) | 3 |
| **RNF05** | IA chamada só no passo 4, nunca nos passos 6-8 | 4-8 |
| **RN01** | Erro em 1 registro não trava os outros | 9 |
| **RN02** | GET `/mapping/{table}` permite inspecionar JSON | 5 |
| **RN03** | `processed + failed == total` → COMPLETED | 7 |
| **RN04** | `protocolId` no header de cada mensagem | 6-7 |
