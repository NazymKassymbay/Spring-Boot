# Spring Boot course project

INFT3132 · Backend Framework. Spring · KBTU, Fall 2026

| Folder | What it is | Assignments |
|---|---|---|
| [`practice1-2/`](practice1-2) | Greeting REST service | Practice 1–3 |
| [`online-shop/`](online-shop) | Online shop REST API — the course project | SIS 1–3, Practice 4–7 |

## online-shop — the course project

REST backend of a small online shop: categories, products with stock, and orders with a status life cycle.
Spring Boot 4.1 · Java 17 · Maven · PostgreSQL · Spring Data JPA · Flyway · Kafka · Spring Security (JWT).
Full documentation: [online-shop/README.md](online-shop/README.md).

| Assignment | Topic | What was done |
|---|---|---|
| SIS 1 | REST API | CRUD endpoints with DTOs and Bean Validation, one error format, correct status codes, Maven profiles, HTTP-client collection |
| Practice 4 | Persistence | PostgreSQL, Spring Data JPA repositories, paged queries, Flyway migrations and seed data |
| SIS 2 | Layers and tests | Layers by feature, DTO ↔ domain ↔ entity mappers, Mockito unit tests, MockMvc slices, Testcontainers repository tests |
| Practice 5 | AOP and caching | Execution-time logging aspect for the service layer, bounded Caffeine cache with eviction |
| Practice 6 | API docs and an external call | Swagger UI, outbound REST client with timeouts and a fallback |
| SIS 3 | Documented API and events | OpenAPI for every endpoint incl. errors; retry + circuit breaker; Kafka producer and idempotent consumer with a dead-letter topic |
| Practice 7 | Security | User store with BCrypt, JWT login, role-based access (`DELETE /api/v1/products/{id}` → ADMIN), tests for 401 / 403 / 2xx |

### Run it

```bash
# PostgreSQL (once)
docker run -d --name shop-db -p 5432:5432 \
  -e POSTGRES_DB=online_shop -e POSTGRES_USER=shop -e POSTGRES_PASSWORD=shop postgres:16-alpine

# Kafka (optional: the API also works without it)
docker run -d --name shop-kafka -p 9092:9092 apache/kafka:3.8.0

# The application, dev profile: schema, demo catalogue and demo users
cd online-shop
./mvnw spring-boot:run

# All tests (need Docker running)
./mvnw test
```

| Page | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |

Demo users (dev profile only): `customer` / `customer123` (CUSTOMER) and `admin` / `admin123` (ADMIN).
Log in with `POST /api/v1/auth/login` and send the token as `Authorization: Bearer <token>`.

## practice1-2 — Practice 3: Dependency Injection and a conditional bean

- All services use constructor injection with `private final` fields (no field `@Autowired`).
- `Clock` is registered as a bean in `AppConfig` and injected into `GreetingService`.
- `GreetingAuditLogger` is a conditional bean (`@ConditionalOnProperty`),
  created only when `app.greeting.audit.enabled=true`.

### How to toggle the conditional bean

| Mode | Command | Audit |
|------|---------|-------|
| Default | `./mvnw spring-boot:run` | OFF |
| Dev profile | `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` | ON |

Run the commands inside the `practice1-2` folder.

Endpoint: `GET http://localhost:8080/greet/{name}`
