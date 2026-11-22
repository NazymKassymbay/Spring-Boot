# Online Shop API — course project (SIS 1, Practice 4, SIS 2, Practice 5, Practice 6, SIS 3, Practice 7)

REST backend of a small online shop: categories, products with stock, and orders with a status life cycle.
Spring Boot 4.1 · Java 17 · Maven · PostgreSQL · Spring Data JPA · Flyway.

## Run

1. Start PostgreSQL (once; the data is kept in the container between restarts):

```bash
docker run -d --name shop-db -p 5432:5432 \
  -e POSTGRES_DB=online_shop -e POSTGRES_USER=shop -e POSTGRES_PASSWORD=shop \
  postgres:16-alpine
```

2. Start the application:

```bash
cd online-shop
./mvnw spring-boot:run                                   # dev profile: schema + demo catalogue
DB_URL=jdbc:postgresql://host:5432/db DB_USERNAME=... DB_PASSWORD=... \
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=prod # prod: schema only, settings from env
./mvnw test                                              # integration tests, need Docker running
```

The default connection is `jdbc:postgresql://localhost:5432/online_shop`, user `shop`, password `shop`;
`DB_URL`, `DB_USERNAME` and `DB_PASSWORD` override it. The prod profile has no defaults.
Tests start their own PostgreSQL in Docker with Testcontainers, so they never touch the dev database.

## Database

The schema is owned by Flyway; Hibernate only validates the entities against it (`ddl-auto=validate`).

| Migration | Location | Applied in |
|---|---|---|
| `V1__create_schema.sql` — tables, foreign keys, unique and check constraints, indexes | `db/migration` | all profiles |
| `V2__seed_demo_catalogue.sql` — 3 categories, 5 products | `db/seed` | dev only |
| `V3__processed_events.sql` — idempotency store of the Kafka consumer (SIS 3) | `db/migration` | all profiles |
| `V4__users_and_roles.sql` — `app_users`, `user_roles` (Practice 7) | `db/migration` | all profiles |
| `V5__seed_demo_users.sql` — demo users `customer`, `admin`, `blocked` with BCrypt hashes | `db/seed` | dev only |

Version numbers are shared by both locations, because in dev Flyway reads them together: two files with the
same version stop start-up (`Found more than one migration with version 2`). That is why the Kafka table is
V3, not V2. Test and prod simply skip versions 2 and 5, which Flyway allows.

From now on a schema change is always a new file (`V6__...`); an applied migration is never edited.
To start from an empty database: `docker rm -f shop-db` and run the `docker run` command again.

## Persistence (Practice 4)

| Topic | How it is done |
|---|---|
| Entities | `Category`, `Product`, `Order`, `OrderItem`; every association is `LAZY`; ids are `IDENTITY` columns |
| Relationships | `Product *→1 Category` (`@ManyToOne`), `Order 1→* OrderItem` (`@OneToMany(mappedBy, cascade = ALL)`) |
| Repositories | Spring Data `JpaRepository`; derived queries (`findByCategoryId`, `findBySkuIgnoreCase`, `findByStatus`) and JPQL `@Query` (`findByIdWithCategory`, `existsByStatusInAndProductId`) |
| Paging | `GET /products` and `GET /orders` take `Pageable` (`page`, `size`, `sort`); size is capped at 100 |
| Transactions | `@Transactional` on every service; `readOnly = true` for reads; a runtime exception rolls everything back |
| Concurrency | `@Version` on `Product` and `Order` (optimistic locking): a lost update becomes `409 Conflict` |
| Settings | `ddl-auto=validate`, `open-in-view=false`, password from the `DB_PASSWORD` env variable |

### Maven build profiles

The Maven profile is written into `application.properties` (`spring.profiles.active=@app.profile@`),
so the built jar starts with the matching Spring profile.

| Command | Spring profile | Behaviour |
|---|---|---|
| `./mvnw package` | `dev` (default) | demo data, DEBUG logs |
| `./mvnw package -Ptest` | `test` | no demo data, max 5 products per order |
| `./mvnw package -Pprod` | `prod` | no demo data, DB settings from env, WARN logs, jar named `online-shop.jar` |

## Layers

```
web (controllers, DTOs, GlobalExceptionHandler)
  -> service (business rules, transactions)
    -> repository (Spring Data JPA interfaces)
      -> domain (JPA entities: Category, Product, Order, OrderItem)
mapper: DTO <-> domain       config: ShopProperties, Clock
```

## Endpoints (`/api/v1`)

"Who" is the Practice 7 access rule: **anyone** needs no token, **signed in** needs any valid token,
**ADMIN** needs a token of a user with the ADMIN role. Without a token a protected endpoint answers `401`,
with a token but the wrong role `403`.

| Method | Path | Who | Success | Errors |
|---|---|---|---|---|
| POST | `/auth/login` | anyone | 200 + token | 400, 401 |
| GET | `/auth/me` | signed in | 200 | 401 |
| GET | `/categories` | anyone | 200 | |
| GET | `/categories/{id}` | anyone | 200 | 404 |
| POST | `/categories` | ADMIN | 201 + Location | 400, 409 duplicate name |
| PUT | `/categories/{id}` | ADMIN | 200 | 400, 404, 409 |
| DELETE | `/categories/{id}` | ADMIN | 204 | 404, 409 has products |
| GET | `/products?categoryId&page&size&sort=price,desc` | anyone | 200 | 400 unknown sort field |
| GET | `/products/{id}` | anyone | 200 | 404 |
| POST | `/products` | ADMIN | 201 + Location | 400, 409 duplicate SKU or unknown category |
| PUT | `/products/{id}` | ADMIN | 200 | 400, 404, 409 |
| PATCH | `/products/{id}/stock` | ADMIN | 200 | 400, 404, 409 stock below 0 |
| DELETE | `/products/{id}` | ADMIN | 204 | 404, 409 in an active order |
| GET | `/orders?status&page&size&sort` | ADMIN | 200 | 400 |
| GET | `/orders/{id}` | ADMIN | 200 | 404 |
| POST | `/orders` | signed in | 201 + Location | 400, 409 not enough stock / unknown product / too many lines |
| PATCH | `/orders/{id}/status` | ADMIN | 200 | 400, 404, 409 illegal transition |
| DELETE | `/orders/{id}` | ADMIN | 204 | 404, 409 order still active |
| GET | `/exchange-rates/{code}` | anyone | 200 | 400 |

### Business rules

- Creating an order reserves stock for all lines at once. If any line lacks stock, nothing is reserved.
- Order price and product name are copied at order time, so later price changes do not alter history.
- Status flow: `NEW → PAID → SHIPPED → DELIVERED`. `NEW` and `PAID` orders can become `CANCELLED`, which returns stock.
- Only `CANCELLED` or `DELIVERED` orders can be deleted. Products in `NEW`/`PAID` orders cannot be deleted.
- A category with products cannot be deleted. Category names and product SKUs are unique (case-insensitive).

## Error format

Every error, including validation, 401 and 403 from the security filter chain, 404 of unknown URLs, 405, 406
and 415, has the same body:

```json
{
  "timestamp": "2026-09-29T14:15:23.145Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Request body is invalid",
  "path": "/api/v1/orders",
  "violations": [
    { "field": "customerEmail", "rejectedValue": "bad", "message": "customerEmail must be a valid email" }
  ]
}
```

`violations` is present only for validation errors.

## HTTP client collection

`http/shop-api.http` covers every endpoint plus the error cases (47 requests).
Open it in IntelliJ IDEA, choose the `dev` environment from `http/http-client.env.json` and run the requests top to bottom.
Requests 0a/0b log in and save `adminToken` / `customerToken`; created ids are saved into variables too.
Run it against a fresh dev database: request 20 uses product `3` from the demo catalogue.
Requests 37-44 prove the Practice 7 rules (401, 403, 2xx). The Postman collection
(`http/online-shop.postman_collection.json`) has the same login folder and security folder.

## SIS 2: layered architecture and tests

The project is split **by feature**, with layers inside each feature (lecture weeks 6-7, slides 25-27).

```
com.example.shop
├── category        CRUD only, so simply layered: the JPA entity is the model
│                   Category, CategoryRepository, CategoryService, CategoryController, CategoryMapper
├── product         real rules (stock, price), so ports and adapters
│   ├── domain      Product (aggregate), CategoryRef (value object), ProductRepository (port). No Spring, no JPA
│   ├── app         ProductService (use cases), ProductQueries, CategoryCatalog, ProductUsage (ports)
│   ├── infra       ProductEntity, SpringDataProducts, JpaProductRepository, ProductEntityMapper, adapters
│   └── web         ProductController, request/response records, ProductApiMapper
├── order           real rules (status life cycle, stock reservation), so ports and adapters
│   ├── domain      Order (aggregate), OrderLine (value object), OrderStatus, OrderRepository (port)
│   ├── app         OrderService (use cases), Inventory, OrderQueries (ports)
│   ├── infra       OrderEntity, OrderItemEntity, SpringDataOrders, JpaOrderRepository, OrderEntityMapper,
│   │               ProductInventory (talks to the product context)
│   └── web         OrderController, request/response records, OrderApiMapper
└── common          config (Clock, ShopProperties), exceptions, error handler, PageResponse
```

Dependencies between features point one way: `order -> product -> category`.
A feature that needs something from another one defines a small port and the other feature implements it
(`CategoryUsage`, `ProductUsage`, `CategoryCatalog`, `Inventory`).

### Three models, two mappers

| Model | Product | Order |
|---|---|---|
| API (DTO) | `ProductRequest`, `ProductResponse` | `CreateOrderRequest`, `OrderResponse` |
| Domain | `Product` | `Order`, `OrderLine` |
| Persistence | `ProductEntity` | `OrderEntity`, `OrderItemEntity` |
| DTO <-> domain | `ProductApiMapper` | `OrderApiMapper` |
| Domain <-> entity | `ProductEntityMapper` | `OrderEntityMapper` |

Controllers call only application services, and no `@Entity` ever appears in a response.
The API, the URLs and the JSON are exactly the same as in SIS 1 and Practice 4.

### Tests

| Test | Kind | Needs |
|---|---|---|
| `ProductTest`, `OrderTest` | domain rules, plain JUnit | nothing |
| `ProductServiceTest` | service unit test with an in-memory repository | nothing |
| `OrderServiceTest` | service unit test with Mockito mocks | nothing |
| `ProductControllerTest`, `OrderControllerTest` | `@WebMvcTest` + MockMvc slice, service mocked | nothing |
| `RepositoryIntegrationTest` | repositories and mappers against real PostgreSQL (Testcontainers) | Docker |
| `ShopApiIntegrationTest` | the whole API end to end (Practice 4) | Docker |
| `CategoryCachingTest` | the Practice 5 cache: cached read, evict on write | nothing |

```bash
./mvnw test
```

## Cross-cutting concerns (Practice 5)

Behaviour that every service needs but no service should own is written once as an aspect, and the
category list is cached. Both are built on the same Spring proxies that already give `@Transactional`
its transactions (lecture weeks 8-9).

| Concern | Where | What it does |
|---|---|---|
| Execution time + logging | `common/aop/ServiceTimingAspect` | `@Around` on every public method of any `@Service`; logs the signature and elapsed ms (never the arguments) |
| Caching | `common/config/CacheConfig` + `CategoryService.findAll` | `@Cacheable("categories")` on the read, `@CacheEvict(allEntries = true)` on every write |

- The cached value is an immutable `CategoryResponse` record, never a JPA entity.
- Caffeine is the backend, bounded by `maximumSize` and `expireAfterWrite` in `application.properties`
  (`spring.cache.caffeine.spec`), so the cache never grows forever.
- `CategoryResponse` carries a `productCount`, which a product create or delete changes, so
  `ProductService.create` and `ProductService.delete` evict the cache too (the product context already
  depends on the category context, never the other way).
- The timing log line (`...took N ms`) only appears for calls that arrive through the proxy —
  a service method called from inside the same bean is not advised.

## API docs and an external call (Practice 6)

Two things from lecture week 10: the API is published as OpenAPI / Swagger UI, and the shop calls one
external service with a timeout and a fallback.

### Swagger UI

springdoc generates the spec from the existing controllers and DTOs; `common/config/OpenApiConfig`
adds the title, description and one `GroupedOpenApi` per version (`v1`). `@Operation` and `@ApiResponse`
annotations document **every** endpoint (17 operations in `Categories`, `Products`, `Orders` and
`Exchange rates` tags): a summary, the success status and each error status that really occurs, with the
`ErrorResponse` body. Paged lists use `@ParameterObject`, so `page`, `size` and `sort` appear as three query
parameters. `ShopApiIntegrationTest.everyEndpointIsDocumentedWithItsErrors` reads `/v3/api-docs/v1` and fails
if an endpoint is added without this documentation.

| Page | URL |
|---|---|
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |

### External exchange-rate call with timeout and fallback

`GET /api/v1/exchange-rates/{code}` returns the shop currency (KZT) in another currency, fetched from an
external service.

| Piece | Where | Role |
|---|---|---|
| Timed client | `common/config/RestClientConfig` | a `RestClient` with connect and read timeouts, so a slow service fails fast |
| Port / adapter | `exchange/app/ExchangeRateProvider` → `exchange/infra/HttpExchangeRateProvider` | the only class that knows the external HTTP API |
| Retry (SIS 3) | `@Retry("exchangeRate")` on `ExchangeRateService.rateFor` | up to 3 attempts, backoff 200 ms → 400 ms; only transient faults (timeout / I/O error, 5xx), never a 4xx |
| Circuit breaker (SIS 3) | `@CircuitBreaker("exchangeRate")` on the same method | opens when ≥ 50 % of the last 10 calls (min. 5) failed or took > 1.5 s; stays open 10 s, then 3 trial calls |
| Fallback | `ExchangeRateService.fallbackRate` | after all attempts fail, or at once while the breaker is open: a configured last-known rate, marked `source: "FALLBACK"` |
| Settings | `application.properties` (`app.exchange.*`, `resilience4j.*.instances.exchangeRate.*`) | base URL, timeouts, retry and breaker rules, fallback rates |

The three questions of slide 29, answered: **how long do I wait?** 2 s connect + 2 s read; **how often do I
retry?** at most 3 attempts, only for transient faults; **what do I return when it fails?** the last-known rate,
honestly marked `FALLBACK`.

Resilience4j (`resilience4j-spring-boot4`) applies `@Retry` and `@CircuitBreaker` as aspects, so they need
`spring-boot-starter-aspectj` and work only when the method is called through the Spring proxy (from the
controller). The default order is Retry(CircuitBreaker(call)), so the breaker counts every attempt. The
fallback is declared on `@Retry`, the **outer** aspect: on the inner `@CircuitBreaker` it would turn the first
failure into a successful result and Retry would never retry.

Proven without the network by `HttpExchangeRateProviderTest` (the HTTP adapter against a
`MockRestServiceServer`), `ExchangeRateControllerTest` (mapping and validation) and `ExchangeRateServiceTest`,
which starts a small context with the Resilience4j aspects and a mocked provider: live rate; a transient fault
retried and then answered live; fallback after 3 failed attempts; a 4xx not retried; an open breaker answering
from the fallback without calling the service at all.

## Event-driven communication with Kafka (SIS 3)

From lecture week 11: placing an order publishes a domain event, and a separate consumer reacts to it on
its own schedule. The order is not held up waiting for that reaction.

### Start Kafka locally

A single-node Kafka (KRaft mode, no ZooKeeper) is enough for development:

```
docker run -d --name shop-kafka -p 9092:9092 apache/kafka:3.8.0
```

The app reads `spring.kafka.bootstrap-servers` (default `localhost:9092`, override with
`KAFKA_BOOTSTRAP_SERVERS`). If Kafka is **not** running the application still starts normally — the
producer and consumer simply stay idle, so the rest of the API keeps working.

### The flow

| Piece | Where | Role |
|---|---|---|
| Event | `order/events/OrderPlaced` | the fact: an order has been placed; carries `eventId` (dedup) and `orderId` (the Kafka key) |
| Publish after commit | `order/events/OrderEventsRelay` | `@TransactionalEventListener(AFTER_COMMIT)` — Kafka only sees committed orders |
| Producer | `order/events/OrderEventsProducer` | sends to `orders.placed`, key = order id, so one order's events stay ordered |
| Consumer | `order/events/OrderPlacedListener` | own group `order-notifications`, `concurrency = 3`, idempotent |
| Idempotency | `order/events/ProcessedEvents` + `processed_events` table | each `eventId` handled once, even if delivered twice |
| Dead-letter topic | `common/config/KafkaConfig` | retry twice, then park poison records on `orders.placed.DLT` |

Delivery is **at-least-once + idempotent consumer** (slide 23): a duplicate delivery is recognised by its
`eventId` and skipped.

### Demo

1. Start PostgreSQL and Kafka (commands above), then `./mvnw spring-boot:run`.
2. Place an order (`POST /api/v1/orders`, or run the HTTP collection). In the log you will see
   `Published OrderPlaced ...` from the producer and `Order ... confirmed ...` from the consumer.
3. Poison message → dead-letter topic. Send a broken record to the topic and watch it land on the DLT:

   ```
   docker exec -it shop-kafka /opt/kafka/bin/kafka-console-producer.sh \
     --bootstrap-server localhost:9092 --topic orders.placed
   > {"eventId":"00000000-0000-0000-0000-000000000001","orderId":-1,"customerName":"x","customerEmail":"x@x","total":1,"occurredAt":"2026-01-01T00:00:00Z"}
   ```

   Then read the dead-letter topic:

   ```
   docker exec -it shop-kafka /opt/kafka/bin/kafka-console-consumer.sh \
     --bootstrap-server localhost:9092 --topic orders.placed.DLT --from-beginning
   ```

   The broken record (orderId -1) appears on `orders.placed.DLT`; the consumer did **not** act on it.

### Test

`OrderEventsIntegrationTest` runs an in-JVM broker (`@EmbeddedKafka`) with a real PostgreSQL
(Testcontainers) and proves all three behaviours: the event is produced and consumed, a duplicate
delivery is processed only once, and a poison record is parked on `orders.placed.DLT`.

### SIS 3 checklist

| Deliverable | Where |
|---|---|
| OpenAPI docs for every endpoint, incl. errors | `OpenApiConfig`, `@Tag`/`@Operation`/`@ApiResponse` on all 17 operations; checked by `everyEndpointIsDocumentedWithItsErrors` |
| Outbound client: timeouts, retry, breaker, fallback | `RestClientConfig` (timeouts), `ExchangeRateService` (`@Retry`, `@CircuitBreaker`, `fallbackRate`) |
| Logging aspect + a bounded cache | Practice 5 — `ServiceTimingAspect`, Caffeine `categories` cache |
| Domain event, key = aggregate id | `OrderPlaced`, produced with key = order id |
| Consumer in its own group, idempotent | `OrderPlacedListener` + `ProcessedEvents` |
| Dead-letter topic + poison-message demo | `KafkaConfig` → `orders.placed.DLT`, demo above |
| README: how to start Kafka locally | this section |

## Security: users, login and roles (Practice 7)

Lecture week 12. The API is no longer open to anyone who can reach port 8080: every request passes the Spring
Security filter chain, which decides who the caller is (authentication, `401` if unknown) and what they may do
(authorization, `403` if not allowed). Rules deny by default and open public endpoints by name.

### Try it

```bash
# 1. Log in as a demo user (dev profile only) -> a JWT valid for 15 minutes
curl -s -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}'
# {"accessToken":"eyJraWQiOi...","tokenType":"Bearer","expiresIn":900}

# 2. Send it on every protected request
curl -i -X DELETE localhost:8080/api/v1/products/5 -H "Authorization: Bearer $TOKEN"
```

| Demo user (dev only) | Password | Roles | May |
|---|---|---|---|
| `customer` | `customer123` | CUSTOMER | read the catalogue, place orders |
| `admin` | `admin123` | ADMIN | everything: catalogue writes, all orders |
| `blocked` | `disabled123` | CUSTOMER, account disabled | nothing: login gives 401 |

In Swagger UI: run `POST /api/v1/auth/login`, press **Authorize**, paste the `accessToken`.

### Definition of done (slide 4 / 16)

| Request | anonymous | CUSTOMER | ADMIN |
|---|---|---|---|
| `GET /api/v1/products` | 200 | 200 | 200 |
| `POST /api/v1/orders` | 401 | 201 | 201 |
| `GET /api/v1/orders` | 401 | 403 | 200 |
| `DELETE /api/v1/products/{id}` (the role-protected endpoint) | 401 | 403 | 204 |

### How it works

| Piece | Where | Role |
|---|---|---|
| User store | `V4__users_and_roles.sql`, `auth/AppUser`, `AppUserRepository` | users and their roles in PostgreSQL; only BCrypt hashes, never passwords |
| Load users | `auth/JpaUserDetailsService` | plugs the table into Spring Security; the entity never implements `UserDetails`, so a hash cannot leak into JSON |
| Password encoding | `SecurityConfig.passwordEncoder` | `DelegatingPasswordEncoder`: stored as `{bcrypt}$2a$10$...`, salted and slow; the prefix allows a later move to Argon2 |
| Login | `auth/AuthController` `POST /api/v1/auth/login` | `AuthenticationManager` → `DaoAuthenticationProvider` → user + hash check, then `TokenService` signs a JWT |
| Token | `auth/TokenService`, `auth/JwtConfig` | RS256 JWT: `iss`, `sub` = username, `iat`, `exp` (15 min), `roles` = `["ROLE_ADMIN"]` |
| Check every request | `SecurityConfig` → `oauth2ResourceServer().jwt()` | verifies the signature with the public key, then `exp` and `iss`; no database query per request |
| Roles from the token | `JwtConfig.jwtAuthenticationConverter` | reads the `roles` claim; without it Spring reads `scope` and every admin gets 403 |
| URL rules | `SecurityConfig.FilterChain.apiSecurity` | public catalogue reads and login; ADMIN for catalogue writes and order management; any user for placing an order; everything else needs a token |
| Method rule | `@PreAuthorize("hasRole('ADMIN')")` on `ProductService.delete` | defence in depth: holds even if a future caller skips the URL rules |
| One error body | `SecurityConfig` entry point / denied handler → `GlobalExceptionHandler` | 401 and 403 come back as the usual `ErrorResponse`, with `WWW-Authenticate: Bearer` on 401 |

Choices worth defending:

- **Stateless JWT, CSRF off.** The token travels in the `Authorization` header, which a browser never adds on its
  own, so a hostile page cannot forge a request; there is no session cookie to protect (slides 18, 26).
- **One message for every failed login.** Wrong password, unknown user and disabled account all answer
  `401 Invalid username or password`, so nobody can find out which usernames exist (slide 10).
- **RS256, not HS256.** Only the shop holds the private key; a service given the public key can check tokens but
  never mint them (slide 22). The cost of stateless tokens: one cannot be revoked before it expires, hence 15 minutes.
- **The identity comes from the token, never from the body** (`/auth/me` reads the `Jwt`, slide 11).
- **No secret in Git.** Dev and test generate a fresh key pair at start-up (tokens die on restart). Production
  needs both keys from outside: `JWT_PRIVATE_KEY` and `JWT_PUBLIC_KEY` (e.g. `file:/run/secrets/jwt-private.pem`);
  without them the prod profile refuses to start. Demo users exist only in the dev seed.

Generate a production key pair (never commit it; `*.pem` is git-ignored):

```bash
openssl genpkey -algorithm RSA -out jwt-private.pem -pkeyopt rsa_keygen_bits:2048
openssl rsa -pubout -in jwt-private.pem -out jwt-public.pem
```

### Tests

| Test | Proves |
|---|---|
| `SecurityIntegrationTest` (Docker) | real login, real signed tokens, real filter chain on PostgreSQL: BCrypt storage, the 401/403/2xx matrix above, one generic 401 for every failed login, a tampered token rejected, case-insensitive usernames, the `@PreAuthorize` rule without the web layer |
| `ShopApiIntegrationTest` | the business flow, sent as an ADMIN with spring-security-test's `jwt()`; the OpenAPI test also checks that 401/403 are documented |
| `ProductControllerTest`, `OrderControllerTest`, `ExchangeRateControllerTest` | mapping and validation only, so these slices run with `@AutoConfigureMockMvc(addFilters = false)` |

### Practice 7 checklist

| Deliverable | Where |
|---|---|
| Flyway migration: users and roles, one customer and one admin seeded with BCrypt hashes | `V4__users_and_roles.sql`, dev seed `V5__seed_demo_users.sql` |
| `UserDetailsService` on JPA and a `PasswordEncoder` bean | `JpaUserDetailsService`, `SecurityConfig.passwordEncoder` |
| `POST /api/v1/auth/login` returns a token (JWT) | `AuthController`, `TokenService`, `JwtConfig` |
| One endpoint restricted to a role | `DELETE /api/v1/products/{id}` → ADMIN (plus all catalogue writes and order management) |
| Proof: tests or HTTP-client requests showing 401, 403 and 2xx | `SecurityIntegrationTest`; `shop-api.http` requests 37-44 |
