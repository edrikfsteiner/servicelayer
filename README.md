# Service Layer — Migração Massiva de Dados

Serviço Spring Boot que migra dados de um banco MySQL (origem) para PostgreSQL (destino) de forma assíncrona via RabbitMQ, com mapeamento de esquema gerado por IA.

---

## Pré-requisitos

| Ferramenta | Versão mínima |
|:---|:---|
| Java (JDK) | 25 |
| Docker + Docker Compose | 20+ / v2 |
| Maven | 3.9+ (ou use o wrapper `./mvnw`) |
| Chave de API OpenAI | Necessária para o endpoint `/ai-map` |

---

## 1. Subir a infraestrutura

```bash
docker compose up -d
```

Isso inicia:

| Container | Porta | Credenciais |
|:---|:---|:---|
| MySQL 8.0 (origem) | 3306 | root / root |
| PostgreSQL 16 (destino) | 5432 | postgres / password |
| RabbitMQ 3 + Management | 5672 / 15672 | admin / admin123 |

Verifique se estão rodando:

```bash
docker compose ps
```

---

## 2. Configurar banco de origem (MySQL)

Conecte no MySQL e crie a tabela de origem com os dados que deseja migrar.

```bash
docker exec -it origin-db mysql -uroot -proot origin_db
```

Exemplo — criar e popular uma tabela `clientes`:

```sql
CREATE TABLE clientes (
    id        INT AUTO_INCREMENT PRIMARY KEY,
    nome      VARCHAR(100),
    email     VARCHAR(100),
    documento VARCHAR(20),
    cidade    VARCHAR(50)
);

INSERT INTO clientes (nome, email, documento, cidade) VALUES
('Ana Silva',    'ana@email.com',    '111.222.333-44', 'São Paulo'),
('Bruno Costa',  'bruno@email.com',  '555.666.777-88', 'Rio de Janeiro'),
('Carla Souza',  'carla@email.com',  '999.000.111-22', 'Curitiba');
```

---

## 3. Configurar banco de destino (PostgreSQL)

Conecte no PostgreSQL e crie a tabela de destino com o esquema alvo.

```bash
docker exec -it target-db psql -U postgres -d target_db
```

Exemplo — tabela `customers`:

```sql
CREATE TABLE customers (
    id         SERIAL PRIMARY KEY,
    full_name  VARCHAR(100),
    email      VARCHAR(100),
    cpf        VARCHAR(20),
    city       VARCHAR(50)
);
```

---

## 4. Configurar a chave da IA

Exporte a variável de ambiente antes de iniciar a aplicação:

```bash
# Linux / macOS
export OPENAI_API_KEY=sk-...

# Windows PowerShell
$env:OPENAI_API_KEY="sk-..."
```

---

## 5. Executar a aplicação

```bash
./mvnw spring-boot:run
```

No Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

A aplicação sobe na porta **8080**.

---

## 6. Fluxo de uso (endpoints)

### 6.1 Gerar mapeamento com IA

Envia os esquemas de origem e destino para o LLM gerar o contrato JSON.

```bash
curl -X POST "http://localhost:8080/api/migration/ai-map?targetTable=customers" \
  -H "Content-Type: application/json" \
  -d '{
    "originSchema": "clientes(id INT, nome VARCHAR, email VARCHAR, documento VARCHAR, cidade VARCHAR)",
    "targetSchema": "customers(id SERIAL, full_name VARCHAR, email VARCHAR, cpf VARCHAR, city VARCHAR)"
  }'
```

### 6.2 Consultar mapeamento salvo

```bash
curl http://localhost:8080/api/migration/mapping/customers
```

Resposta esperada:

```json
{
  "nome": "full_name",
  "email": "email",
  "documento": "cpf",
  "cidade": "city"
}
```

### 6.3 Iniciar a migração

```bash
curl -X POST http://localhost:8080/api/migration/start \
  -H "Content-Type: application/json" \
  -d '{
    "originTable": "clientes",
    "targetTable": "customers"
  }'
```

Resposta:

```json
{
  "protocolId": "a1b2c3d4-...",
  "message": "Migração iniciada"
}
```

### 6.4 Consultar status por protocolo

```bash
curl http://localhost:8080/api/migration/status/{protocolId}
```

Resposta:

```json
{
  "protocolId": "a1b2c3d4-...",
  "originTable": "clientes",
  "targetTable": "customers",
  "totalRecords": 3,
  "processedRecords": 3,
  "failedRecords": 0,
  "status": "COMPLETED",
  "createdAt": "2026-03-21T10:00:00",
  "updatedAt": "2026-03-21T10:00:05"
}
```

### 6.5 DLQ — consultar falhas

```bash
curl http://localhost:8080/api/dlq/count
```

### 6.6 DLQ — reprocessar mensagens

```bash
curl -X POST http://localhost:8080/api/dlq/reprocess
```

---

## 7. Executar os testes

```bash
./mvnw test
```

70 testes unitários cobrindo controllers, services, consumer, repository e utilitários.

---

## 8. Acessos úteis

| Recurso | URL |
|:---|:---|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| RabbitMQ Management | http://localhost:15672 |
| Actuator | http://localhost:8080/actuator |

---

## Stack

- Java 25, Spring Boot 4.0.3, Spring AI 2.0.0-M2
- RabbitMQ (mensageria + DLQ)
- MySQL 8.0 (origem) / PostgreSQL 16 (destino)
- Virtual Threads habilitadas
- Lombok, Jackson, Springdoc OpenAPI
