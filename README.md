### Payment Service
A Java 25 and Spring Boot project demonstrating reliable payment processing and event-driven communication.<br>
An idempotent payment REST API with asynchronous Kafka publishing using the Transactional Outbox Pattern, and a workflow supporting limited ISO 20022 pain.001.001.12 and pain.002.001.14.

### Overview
1. The service exposes a REST API for creating and retrieving payments, with request validation and idempotent payment creation using the Idempotency-Key request header. Both JSON and ISO 20022 XML input formats are supported.
2. The service supports limited, project-specific XML profiles based on ISO 20022 `pain.001.001.12` and `pain.002.001.14`. Incoming payment messages are validated against the corresponding limited XSD profile, mapped to the application's payment model, and processed through the existing payment creation flow. Responses are returned as structured `pain.002` XML messages.
3. Payment data and corresponding events are persisted atomically within a single PostgreSQL transaction using the Transactional Outbox Pattern.
4. Pending outbox events are published asynchronously to Apache Kafka in configurable batches, with keyset scrolling, exponential backoff, and a configurable retry limit. Events are marked as published after broker acknowledgment.
5. A lightweight Kafka consumer demonstrates idempotent event processing and duplicate message handling.

The ISO 20022 implementation intentionally supports a limited subset of message structures and fields. The project-specific XSD profiles are based on the corresponding ISO 20022 schemas but do not implement the full set of ISO 20022 or SEPA business rules. This is an educational implementation, not a production-ready banking platform.

_**The project is part of my ongoing effort to strengthen and expand my backend development skills, with a particular focus on reliable data processing, transactional consistency, idempotency, and production-oriented design patterns.**_
<br>

<!-- another option for visualisation
| **Current Scope** | **Tech Stack** |
|---|---|
| 1. REST API for creating and retrieving payments | **Language:** Java 25 |
| 2. Request validation and error handling | **Framework:** Spring Boot 4.1.1 |
| 3. Idempotent payment creation using idempotency keys | **Database:** PostgreSQL |
| 4. PostgreSQL persistence with Liquibase database migrations | **Database Migrations:** Liquibase 5.0.4 |
| 5. Transactional Outbox Pattern for reliable event publishing | **Data Access:** Spring Data JPA, Spring Jdbc |
| 6. Kafka publisher with synchronous and asynchronous failure handling | **Messaging:** Apache Kafka, Spring for Apache Kafka |
| 7. Async event publishing with acknowledgement-based outbox updates |**Testing:** JUnit, Mockito,  WebMvcTest, SpringBootTest, Testcontainers|
| 8. Idempotent Kafka consumer  | **Build Tool:** Maven |
| 9. Configurable outbox batch processing with keyset scrolling, exponential backoff and retry limits| **Infrastructure:** Docker |
| 10. Unit and integration tests | 
-->

<br>
<table>
  <tr>
    <td width="55%" valign="top">

<h3 align="center">Current Scope</h3>

1. REST API for creating payments supporting both JSON and XML requests
2. Request validation and centralized error handling
3. Idempotent payment creation using idempotency keys
4. Limited ISO 20022 pain.001.001.12 XML input with XSD validation and payment data mapping.
5. Limited ISO 20022 pain.002.001.14 XML status reporting.
6. PostgreSQL persistence using Spring Data JPA and JDBC, with Liquibase database migrations
7. Transactional Outbox Pattern for reliable event publishing
8. Asynchronous Kafka event publishing with acknowledgement-based outbox updates
9. Kafka publisher with synchronous and asynchronous failure handling
10. Configurable outbox batch processing with keyset scrolling, exponential backoff and retry limits
11. Idempotent Kafka event consumer with concurrent duplicate protection
12. Unit and integration tests including concurrent event processing

</td>
<td width="45%" valign="top">

<h3 align="center">Tech Stack</h3>

- **Language:** Java 25
- **Framework:** Spring Boot 4.1.1
- **Database:** PostgreSQL
- **Database Migrations:** Liquibase 5.0.4
- **Data Access:** Spring Data JPA, Spring JDBC
- **Messaging:** Apache Kafka, Spring for Apache Kafka
- **Testing:** JUnit, Mockito, WebMvcTest, SpringBootTest, Testcontainers
- **Build Tool:** Maven
- **Infrastructure:** Docker

</td>
</tr>
</table>

<h3 align="center"> Known Limitations </h3>
This project is an educational implementation of a Payment Service, designed to demonstrate payment processing concepts, application architecture, persistence, idempotency, messaging, and ISO 20022 integration. It is not intended to be a production-ready banking platform.

The following aspects are intentionally simplified:

- **Idempotency and request processing:** For `pain.001` requests, XML parsing and mapping take place before the existing payment creation service checks the idempotency key. Repeated requests therefore still incur parsing and mapping overhead.
- **Idempotent response handling:** The original `pain.002` response is not persisted. Repeated requests may produce a newly generated response with a different message ID and creation timestamp.
- **Idempotency key and payload consistency:** The service does not verify whether a reused idempotency key belongs to the same original request payload. Reusing a key with different payment data is therefore not fully handled.
- **ISO 20022 scope:** The `limited-pain001.xsd` and `limited-pain002.xsd` schemas define restricted message profiles for the fields and structures supported by this project. They do not implement the full set of ISO 20022 or SEPA business rules.
- **Payment lifecycle:** A positive `pain.002` status such as `ACTC` indicates successful technical validation, not that a payment has been executed or settled by a bank.

These limitations reflect the project's intended scope and timeframe. A production-grade implementation would require additional business rules, request fingerprinting, persisted response replay, more comprehensive payment lifecycle handling, operational controls, and further security and compliance measures.

<h3 align="center"> Project Roadmap</h3>

| Task | Description |
|:---|---|
| **✅ Task 01 — Basic Payment REST API** | Implemented a REST API for creating payments with DTOs, service logic, and unit tests. |
| **✅ Task 02 — Payment Retrieval** | Added payment retrieval by ID and handling of missing payments. |
| **✅ Task 03 — MVC Tests** | Added controller tests covering successful requests, validation, and error responses. |
| **✅ Task 04 — PostgreSQL Persistence** | Integrated PostgreSQL, JDBC, Liquibase, and Testcontainers. |
| **✅ Task 05 — Idempotent Payment Creation** | Implemented idempotent payment creation using `Idempotency-Key` and database constraints. |
| **✅ Task 06 — Kafka Integration** | Added asynchronous Kafka event publishing and consumption. |
| **✅ Task 07 — Transactional Outbox Pattern** | Ensured payment data and outbox events are saved atomically and events are marked as published after Kafka acknowledgement. |
| **✅ Task 08 — Spring Data JPA Migration** | Migrated payment and outbox persistence to Spring Data JPA while preserving atomic idempotent payment creation. |
| **✅ Task 09 — Idempotent Kafka Consumer** | Prevent duplicate consumed event processing through event identification and deduplication. |
| **✅ Task 10 — Outbox Reliability & Retry Policy** | Improve event publishing with configurable batch size, retries, backoff, and failure tracking. |
| **✅ Task 11 — ISO 20022 Payment Messages** | Add selected ISO 20022 pain.001 and pain.002 message structures with a limited set of fields and validation. |
| **Task 12 — OpenAPI Contract** | Define the REST API contract in openapi.yaml, covering endpoints, schemas, validation rules, error responses, and examples. |
| **Task 13 — Test Coverage & End-to-End Testing** | Add JaCoCo with an 85% line coverage target and end-to-end tests using Kafka Testcontainers. |
| **Task 14 — Observability & Logging** | Add basic application metrics, health checks, and Logback logging with sensitive payment data masking. |
| **Task 15 — DevOps & CI/CD** | Containerize the application stack with Docker Compose and automate build, test, and Docker image publishing using GitHub Actions. |<br>



### Possible Future Extensions 
<p align="left"><em>Ideas under consideration for future development.</em></p>

- **Concurrent Outbox Publishing** — introduce multiple publishing workers while maintaining consistency and preventing duplicate processing.
- **Extended Consumer Business Logic** — implement more realistic payment event processing and downstream business workflows.
- **Payment Lifecycle Management** — add payment cancellation, reversal, and additional state-transition endpoints.
- **Angular Frontend** — build a web interface for creating payments, retrieving their status, and exploring payment events.
- **API Security & Authorization** — introduce JWT-based authentication and authorization to protect payment resources and restrict access.
- **Payment Audit Trail** — maintain a traceable history of payment state transitions and business operations.
- **Failed Event Recovery & Dead-Letter Handling** — implement controlled recovery for failed outbox events and route unprocessable Kafka messages to a dead-letter topic for investigation and reprocessing.
  <br>

### Completed Tasks

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

#### Task 09 — Idempotent Kafka Consumer
**Goal:** Prevent duplicate consumed event processing through event identification and deduplication.

- Implemented a lightweight Kafka consumer to demonstrate idempotent event processing (while keeping the primary focus on the REST API and reliable event publishing).
- Implemented atomic duplicate detection using INSERT ... ON CONFLICT DO NOTHING RETURNING, ensuring safe handling of concurrent duplicate deliveries.
- Added unit and integration tests covering successful processing, duplicate events, invalid payloads, and concurrent event delivery.

#### Task 10 – Outbox Reliability & Retry Policy
**Goal:** Improve event publishing with configurable batch size, retries, backoff, and failure tracking.

- Implemented configurable batch publishing using Spring Data JPA keyset scrolling, with configurable batch size and scheduler interval.
- Implemented exponential backoff, retry tracking, maximum attempt limits and failed-event handling.
- Added unit tests and PostgreSQL Testcontainers integration tests covering batch processing, retry scheduling, database filtering and outbox state updates.

#### Task 11 — ISO 20022 Payment Messages
**Goal:** Enable XML-based payment initiation and status reporting using validated, limited ISO 20022 pain.001 and pain.002 message profiles.

- Added XML payment initiation using limited ISO 20022 pain.001.001.12 with XSD validation and support for a single payment transaction.
- Implemented JAXB-based XML parsing and mapping of selected payment fields to the application model.
- Added limited ISO pain.002.001.14 status report generation with original payment references and a limited response profile supporting ACTC and RJCT.
- Added tests covering XML validation, parsing, mapping, and response generation against official and limited XSD schemas.
