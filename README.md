# Universal Data Ingestion Hub (NoSQL Data Lakehouse PaaS)

> **Projeto de Trabalho de Conclusão de Curso (TCC) - Engenharia de Software**
>
> Uma Plataforma como Serviço (PaaS) M2M para ingestão massiva, tratamento e disponibilização de dados utilizando a **Arquitetura Medallion**, 100% orientada a documentos e impulsionada pelas *Virtual Threads* do Java 25.

---

## Visão Geral

O **Universal Data Ingestion Hub** foi desenhado para resolver o problema de ingestão de dados heterogêneos de múltiplos clientes (Multi-tenant) em alta velocidade. O sistema atua como um *Gateway* assíncrono que recebe os dados via Push API, protege a infraestrutura contra sobrecargas utilizando mensageria e processa a informação através de um *NoSQL Data Lakehouse* estruturado em três camadas contínuas de refinamento (Bronze, Prata e Ouro).

### Principais Características

* **Zero-DB Ingestion:** A rota de alta volumetria (`/api/ingest`) não realiza consultas a banco de dados. A autenticação M2M é *Stateless* (via validação de assinatura JWT) e o controle de *Rate Limit* e quotas é resolvido localmente na memória RAM (*Caffeine Cache*).
* **Alta Concorrência com Project Loom:** Utilização massiva de *Virtual Threads* (Java 25) para suportar milhares de conexões simultâneas sem bloqueio de Carrier Threads do sistema operacional.
* **Schema Evolution Dinâmico:** Adoção exclusiva do MongoDB em todas as camadas, permitindo que a estrutura de dados evolua organicamente sem a necessidade de migrações estruturais (DDL) ou paradas no sistema.
* **Isolamento de Tenants:** Identidade do cliente atrelada criptograficamente ao *Token* de acesso, eliminando riscos de *Spoofing* nas requisições.

---

## Arquitetura e Fluxo de Dados

A arquitetura do projeto segue o padrão **Medallion**, mas adaptada para o ecossistema NoSQL (Orientado a Documentos):

1. **Fase de Segurança (Handshake M2M):**

    * O cliente requisita acesso via `POST /auth` informando `clientId` e `clientSecret`.
    * O Hub valida as credenciais contra a base de segurança (Hashes protegidos via **Argon2id**).
    * Emite um JWT temporário e realiza o "aquecimento" do cache de quotas do cliente.

2. **Fase de Ingestão (API Gateway & Mensageria):**

    * O cliente envia o payload (JSON heterogêneo) para `POST /api/ingest`.
    * O `TenantSecurityFilter` valida a assinatura do JWT e as regras de negócio em cache (*Fail-fast*).
    * O dado é anexado ao `tenantId` e despachado para o **RabbitMQ**, liberando a conexão HTTP instantaneamente (HTTP 202 Accepted).

3. **Fase de Processamento (NoSQL Lakehouse):**

    * **Camada Bronze (Raw Data):** O `BronzeLayerWorker` consome a fila e salva o dado bruto na coleção `bronze_raw_data`. Garante rastreabilidade total sem perda de dados por formatação.
    * **Camada Prata (Cleansed Data):** Rotinas assíncronas higienizam, padronizam valores e desnormalizam os documentos (embutindo chaves estrangeiras), salvando na coleção `silver_cleansed_data`.
    * **Camada Ouro (Aggregated Data):** Utilização das *Aggregation Pipelines* do MongoDB para criar visões otimizadas e relatórios rápidos a partir dos dados desnormalizados da camada Prata, ideais para ferramentas de BI e geração de contexto para Inteligências Artificiais (LLMs).

---

## Stack Tecnológica

| Componente | Tecnologia | Propósito |
| :--- | :--- | :--- |
| **Core** | Java 25 & Spring Boot 3.x | Lógica de negócio, API RESTful e Workers assíncronos. |
| **Concorrência** | Virtual Threads & ReentrantLocks | Alta performance de I/O, evitando *Thread Pinning*. |
| **Segurança M2M** | Spring Security, JJWT, Argon2id | Proteção de credenciais robusta e validação de tokens Stateless. |
| **Mensageria** | RabbitMQ | *Message Broker* para absorção de picos de carga (Buffer/Backpressure). |
| **Database** | MongoDB | Persistência orientada a documentos (*Schemaless*) suportando o Lakehouse. |
| **Cache Local** | Caffeine Cache | Rate Limiting e validação de *Quotas* em memória ultrarrápida. |

---

## Como Executar o Projeto Localmente

### Pré-requisitos

* **Java 25** (JDK) configurado no ambiente.
* **Docker** e **Docker Compose** instalados.
* Uma IDE compatível (IntelliJ IDEA, VS Code).