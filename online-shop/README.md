# Online Shop API — course project (SIS 1, Practice 4, SIS 2, Practice 5, Practice 6)

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

From now on a schema change is always a new file (`V3__...`); an applied migration is never edited.
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

| Method | Path | Success | Errors |
|---|---|---|---|
| GET | `/categories` | 200 | |
| GET | `/categories/{id}` | 200 | 404 |
| POST | `/categories` | 201 + Location | 400, 409 duplicate name |
| PUT | `/categories/{id}` | 200 | 400, 404, 409 |
| DELETE | `/categories/{id}` | 204 | 404, 409 has products |
| GET | `/products?categoryId&page&size&sort=price,desc` | 200 | 400 unknown sort field |
| GET | `/products/{id}` | 200 | 404 |
| POST | `/products` | 201 + Location | 400, 409 duplicate SKU or unknown category |
| PUT | `/products/{id}` | 200 | 400, 404, 409 |
| PATCH | `/products/{id}/stock` | 200 | 400, 404, 409 stock below 0 |
| DELETE | `/products/{id}` | 204 | 404, 409 in an active order |
| GET | `/orders?status&page&size&sort` | 200 | 400 |
| GET | `/orders/{id}` | 200 | 404 |
| POST | `/orders` | 201 + Location | 400, 409 not enough stock / unknown product / too many lines |
| PATCH | `/orders/{id}/status` | 200 | 400, 404, 409 illegal transition |
| DELETE | `/orders/{id}` | 204 | 404, 409 order still active |

### Business rules

- Creating an order reserves stock for all lines at once. If any line lacks stock, nothing is reserved.
- Order price and product name are copied at order time, so later price changes do not alter history.
- Status flow: `NEW → PAID → SHIPPED → DELIVERED`. `NEW` and `PAID` orders can become `CANCELLED`, which returns stock.
- Only `CANCELLED` or `DELIVERED` orders can be deleted. Products in `NEW`/`PAID` orders cannot be deleted.
- A category with products cannot be deleted. Category names and product SKUs are unique (case-insensitive).

## Error format

Every error, including validation, 404 of unknown URLs, 405, 406 and 415, has the same body:

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

`http/shop-api.http` covers every endpoint plus the error cases (37 requests).
Open it in IntelliJ IDEA, choose the `dev` environment from `http/http-client.env.json` and run the requests top to bottom.
Created ids are saved into variables automatically. Run it against a fresh dev database:
request 20 uses product `3` from the demo catalogue.

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
annotations document the endpoints and their error body (`ErrorResponse`) — see `ExchangeRateController`
and `ProductController.findById`.

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
| Resilience | `exchange/app/ExchangeRateService` | on any `RestClientException` it returns a configured fallback rate, marked `source: "FALLBACK"` |
| Settings | `application.properties` (`app.exchange.*`) | base URL, timeouts and the last-known fallback rates |

So the shop keeps answering even when the external service is slow or down: the response just carries
`"source": "FALLBACK"` instead of `"LIVE"`. Proven by `HttpExchangeRateProviderTest` (the HTTP adapter
against a `MockRestServiceServer`: a good response is parsed, a server error becomes an exception),
`ExchangeRateServiceTest` (live vs fallback) and `ExchangeRateControllerTest` (mapping and validation) —
none of them needs the network.
