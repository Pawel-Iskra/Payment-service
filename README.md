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
