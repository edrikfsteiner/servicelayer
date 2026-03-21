# Project Guidelines — servicelayer

Data migration service (TCC) that extracts records from a legacy **MySQL** database and loads them into a new **PostgreSQL** database via **RabbitMQ** async messaging. 

**CRITICAL ARCHITECTURE RULE:** Generative AI (OpenAI via Spring AI) is used **ONLY at Design Time** to analyze sample data and generate a JSON mapping contract. The actual data migration (Runtime ETL) must be purely deterministic Java code applying the JSON mapping, with NO calls to the AI model during the mass processing phase.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Framework | Spring Boot 4.0.3, Java 25 |
| AI | Spring AI 2.0.0-M2 (OpenAI) |
| Messaging | RabbitMQ (Spring AMQP) |
| Source DB | MySQL 8.0 (`legado_db`) |
| Target DB | PostgreSQL 16 (`novo_db`) |
| ORM | Spring Data JPA |
| Monitoring | Actuator + Micrometer/Prometheus |
| Docs | SpringDoc OpenAPI (Swagger UI at `/swagger-ui.html`) |
| Utilities | Lombok |

## Architecture & Project Context

Before implementing any feature, you MUST read the business rules and system requirements located at: `docs/requisitos.md`.

Layered ETL architecture within package `com.migration.servicelayer`:

1. **Design Time (AI):** Controller -> Service (Spring AI) -> Generates JSON Mapping.
2. **Runtime (Migration):** Controller (Receives Batch) -> RabbitMQ (Async) -> Worker Service (Reads JSON + applies deterministic transformation) -> Repository (PostgreSQL).

- Controllers handle REST endpoints and delegate to services.
- Repositories use Spring Data JPA interfaces — avoid raw SQL unless necessary.
- Use `@Service`, `@Repository`, `@RestController`, `@Data` (Lombok) consistently.

## Build & Test
(manter o restante que o Copilot gerou perfeitamente sobre Build, Test, Conventions e Key Files...)