# Online Shop API — course project (SIS 1, Practice 4)

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

A schema change is always a new file (`V3__...`); an applied migration is never edited.
To start from an empty database: `docker rm -f shop-db` and run the `docker run` command again.

- Product lists are filtered, sorted and paged by PostgreSQL (`Specification` + `Pageable`), and the
  category is fetched in the same query (`@EntityGraph`), so there is no N+1.
- Every service method runs in a transaction. Stock changes lock the product rows
  (`SELECT ... FOR UPDATE`) in ascending id order, so parallel orders never oversell and never deadlock.

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
      -> domain (JPA entities: Category, Product, Order; OrderItem is an embedded value)
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
| GET | `/products?categoryId&q&minPrice&maxPrice&inStock&page&size&sort=price,desc` | 200 | 400 |
| GET | `/products/{id}` | 200 | 404 |
| POST | `/products` | 201 + Location | 400, 409 duplicate SKU or unknown category |
| PUT | `/products/{id}` | 200 | 400, 404, 409 |
| PATCH | `/products/{id}/stock` | 200 | 400, 404, 409 stock below 0 |
| DELETE | `/products/{id}` | 204 | 404, 409 in an active order |
| GET | `/orders?status&customerEmail&page&size` | 200 | 400 |
| GET | `/orders/{id}` | 200 | 404 |
| POST | `/orders` | 201 + Location | 400, 409 not enough stock / unknown product / too many products |
| PATCH | `/orders/{id}/status` | 200 | 400, 404, 409 illegal transition |
| DELETE | `/orders/{id}` | 204 | 404, 409 order still active |

### Business rules

- Creating an order reserves stock for all lines at once. If any line lacks stock, nothing is reserved.
- The same product listed twice in one order is merged into one line.
- Order price and product name are copied at order time, so later price changes do not alter history.
- Status flow: `NEW → PAID → SHIPPED → DELIVERED`. `NEW` and `PAID` orders can become `CANCELLED`, which returns stock.
- Only `CANCELLED` or `DELIVERED` orders can be deleted. Products in `NEW`/`PAID` orders cannot be deleted.
- A category with products cannot be deleted. Category names and product SKUs are unique (case-insensitive).

## Error format

Every error, including validation, 404 of unknown URLs, 405 and 415, has the same body:

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

`http/shop-api.http` covers every endpoint plus the error cases (36 requests).
Open it in IntelliJ IDEA, choose the `dev` environment from `http/http-client.env.json` and run the requests top to bottom.
Created ids are saved into variables automatically. Run it against a fresh dev database:
request 20 uses product `3` from the demo catalogue.
