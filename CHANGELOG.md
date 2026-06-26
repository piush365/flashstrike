# Changelog

All notable changes to this project are documented here.

## [Unreleased]

### Added
- Clean Architecture package layout (`api/`, `application/`, `domain/`, `infrastructure/`)
- Resilience4j circuit breaker protecting Kafka publish path with automatic stock rollback
- `FlashSaleProperties` type-safe configuration with `@ConfigurationProperties`
- `FlashSaleHealthIndicator` custom Actuator health check reporting live stock level
- `CorrelationIdFilter` — UUID per request injected into MDC, response header, and response body
- `GlobalExceptionHandler` with RFC 7807 `ProblemDetail` responses
- Flyway schema migrations replacing `ddl-auto: update`
- Idempotency key (`UUID`) on every `OrderEvent` with database unique constraint
- Dead Letter Topic (`flash-sale-orders.DLT`) with exponential back-off retry
- Kafka producer configured with `acks=all`, `enable.idempotence=true`
- Spring Security stateless API-key authentication via `X-API-Key` header
- OpenAPI / Swagger UI with `SecurityRequirement` annotations
- Grafana dashboard with 8 panels auto-provisioned on startup
- k6 load test script ramping to 2,000 virtual users
- GitHub Actions CI (build → unit tests → integration tests → Docker scan)
- Testcontainers integration tests (PostgreSQL + EmbeddedKafka)
- `OversellConcurrencyTest` — 500 threads, zero overselling verified

### Fixed
- Rate limiter used `setRate()` (resets window every call) — replaced with `trySetRate()`
- Inventory used `SET` (overwrites on restart) — replaced with `SETNX` (`setIfAbsent`)
- Kafka send return value was discarded — now awaited with timeout and rolled back on failure
- Inventory decrement was two Redis commands (check + DECR) — replaced with single Lua script
- `OrderResult` enum replaces fragile string comparisons between service and controller

### Removed
- Lombok (incompatible with the build environment; explicit Java used throughout)

## [0.1.0] — Initial Commit

- Basic Spring Boot project with Redis, Kafka, and PostgreSQL
