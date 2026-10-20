# Payment Service

A hands-on Java and Spring Boot project focused on building a payment processing service.

The project is part of my ongoing effort to refresh, strengthen, and expand my backend development skills, with a particular focus on reliable data processing, and production-oriented design patterns.

| **Current Scope** | **Tech Stack** |
|---|---|
| 1. REST API for creating and retrieving payments | **Language:** Java 25 |
| 2. Request validation and error handling | **Framework:** Spring Boot 4.1.1 |
| 3. Idempotent payment creation using idempotency keys | **Database:** PostgreSQL |
| 4. PostgreSQL persistence with Liquibase database migrations | **Database Migrations:** Liquibase 5.0.4 |
| 5. Transactional Outbox Pattern for reliable event publishing | **Data Access:** Spring Data JPA |
| 6. Kafka publisher with synchronous and asynchronous failure handling | **Messaging:** Apache Kafka, Spring for Apache Kafka |
| 7. Async event publishing with acknowledgement-based outbox updates | **Serialization:** Jackson 3 |
| 8. Basic Kafka consumer | **Testing:** JUnit, Mockito,  WebMvcTest, SpringBootTest, Testcontainers |
| 9. Unit and integration tests | **Build Tool:** Maven |
|  | **Infrastructure:** Docker |

## Project Roadmap
| Task | Description |
|:---|---|
| **✅ Task 01 — Basic Payment REST API** | Implemented a REST API for creating payments with DTOs, service logic, and unit tests. |
| **✅ Task 02 — Payment Retrieval** | Added payment retrieval by ID and handling of missing payments. |
| **✅ Task 03 — MVC Tests** | Added controller tests covering successful requests, validation, and error responses. |
| **✅ Task 04 — PostgreSQL Persistence** | Integrated PostgreSQL, JDBC, Liquibase, and Testcontainers. |
| **✅ Task 05 — Idempotent Payment Creation** | Implemented idempotent payment creation using `Idempotency-Key` and database constraints. |
| **✅ Task 06 — Kafka Integration** | Added asynchronous Kafka event publishing and consumption. |
| **✅ Task 07 — Transactional Outbox Pattern** | Ensured payment data and outbox events are saved atomically and events are marked as published after Kafka acknowledgement. |
| **✅ Task 08 — Spring Data JPA Migration** | Migrate payment persistence to Spring Data JPA |
| **Task 09 — Idempotent Kafka Consumer** | Prevent duplicate event processing through event identification and deduplication. |
| **Task 10 — Outbox Reliability & Retry Policy** | Improve event publishing with configurable batch size, retries, backoff, and failure tracking. |
| **Task 11 — OpenAPI & Swagger UI** | Document the REST API and provide an interactive interface for exploring its endpoints. |
| **Task 12 — ISO 20022 Payment Messages** | Add support for processing selected ISO 20022 payment initiation and status messages. |
| **Task 13 — OpenAPI Generator** | Generate API interfaces, DTOs, or clients from the OpenAPI specification. |
| **Task 14 — Observability & Test Coverage** | Improve code coverage, HTTP logging, sensitive-data masking, and service health monitoring. |


## Completed Tasks

#### Task 01 — Basic Payment REST API
**Goal:** Build the initial payment creation endpoint and establish a layered application structure.

- REST endpoint for payment creation (`POST /api/payments`) with request and response DTOs.
- Separation of Controller, Service, and Storage layers.
- Payment domain model, payment status, and unit tests.

#### Task 02 — Payment Retrieval and Error Handling
**Goal:** Retrieve payments by ID and handle missing resources consistently.

- Payment retrieval with `GET /api/payments/{paymentId}` and `Optional`.
- Custom exception handling for missing payments (`404 Not Found`).
- Centralized exception handling with `@ControllerAdvice`.

#### Task 03 — REST API Integration Testing
**Goal:** Verify HTTP endpoints and request validation at the web layer.

- MVC tests using `@WebMvcTest` and `MockMvc`.
- Tests for successful payment creation and retrieval.
- Tests for invalid requests and missing payments.

#### Task 04 — PostgreSQL Persistence
**Goal:** Replace in-memory storage with persistent database storage.

- PostgreSQL integration using Spring JDBC and `JdbcTemplate`.
- Database schema management with Liquibase changesets.
- PostgreSQL integration tests using Testcontainers.

#### Task 05 — Idempotent Payment Creation
**Goal:** Prevent duplicate payments when clients retry requests.

- Idempotency-Key handling and database-level uniqueness constraints.
- Duplicate request handling and consistent HTTP responses.
- Tests for repeated requests and concurrent inserts.

#### Task 06 — Kafka Event-Driven Communication
**Goal:** Publish payment creation events and consume them asynchronously.

- Kafka producer and consumer using Spring Kafka.
- Asynchronous message publishing with acknowledgement and error handling.
- Tests for publishing outcomes and prevention of duplicate events on idempotent replays.

#### Task 07 — Transactional Outbox Pattern
**Goal:** Ensure atomic persistence of payments and outbox events, with reliable asynchronous publication to Kafka.

- Separate payment and outbox storage.
- Single database transaction for payment and outbox event persistence.
- Publish events asynchronously to Kafka and collect IDs of successfully acknowledged events.
- Handle synchronous and asynchronous publishing failures without interrupting the processing of other events.

#### Task 08 — Spring Daja JPA migration
**Goal:**  Migrate payment and outbox persistence to Spring Data JPA while preserving atomic idempotent payment creation.

- Introduced PaymentEntity and OutboxEventEntity with Spring Data JPA repositories.
- Replaced PaymentStorageJdbc and OutboxStorageJdbc with JPA-based persistence.
- JDBC retained as a custom repository implementation for atomic INSERT ... ON CONFLICT DO NOTHING RETURNING payment creation.

