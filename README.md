# Payment Service

A hands-on Java and Spring Boot project focused on building a payment processing service and exploring real-world backend engineering challenges.

The project is part of my ongoing effort to refresh, strengthen, and expand my backend development skills, with a particular focus on reliable data processing, distributed systems, and production-oriented design patterns.

**Current scope:**
- REST API for creating and retrieving payments
- PostgreSQL persistence using Spring JDBC and Liquibase
- Idempotent payment creation
- Apache Kafka integration for event-driven communication
- Unit and integration testing with JUnit, Mockito, and Testcontainers

**Work in progress:** 
Currently implementing the Transactional Outbox pattern to improve the reliability of event publishing.

The project is developed incrementally, with an emphasis on clean code, automated testing, and understanding architectural trade-offs.

**Current Tech Stack**
- Language: Java 25
- Framework: Spring Boot 4.1.1
- Database: PostgreSQL
- Database migrations: Liquibase 5.0.4
- Data access: Spring JDBC
- Messaging: Apache Kafka, Spring for Apache Kafka
- Testing: JUnit, Mockito, Spring Boot Test, Testcontainers
- Build tool: Maven
- Infrastructure: Docker

=======
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
**Goal:** Ensure payment persistence and event recording are atomic, with reliable asynchronous publication to Kafka.

- Separate payment and outbox storage.
- Single database transaction for payment and outbox event persistence.
- Outbox polling, Kafka acknowledgement handling, retries, and integration tests.  