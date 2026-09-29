# Online Shop API — course project (SIS 1)

REST backend of a small online shop: categories, products with stock, and orders with a status life cycle.
Spring Boot 4.1 · Java 17 · Maven. Storage is in-memory for now; PostgreSQL + JPA replace it in Practice 4.

## Run

```bash
cd online-shop
./mvnw spring-boot:run                                   # dev profile: demo catalogue is loaded
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod   # empty store, quiet logs
./mvnw test                                              # integration tests (test profile)
```

### Maven build profiles

The Maven profile is written into `application.properties` (`spring.profiles.active=@app.profile@`),
so the built jar starts with the matching Spring profile.

| Command | Spring profile | Behaviour |
|---|---|---|
| `./mvnw package` | `dev` (default) | demo data, DEBUG logs |
| `./mvnw package -Ptest` | `test` | empty store, max 5 products per order |
| `./mvnw package -Pprod` | `prod` | empty store, WARN logs, jar named `online-shop.jar` |

## Layers

```
web (controllers, DTOs, GlobalExceptionHandler)
  -> service (business rules)
    -> repository (interfaces; in-memory implementations)
      -> domain (Category, Product, Order, OrderItem, OrderStatus)
mapper: DTO <-> domain       config: ShopProperties, Clock, DemoDataLoader
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
| POST | `/products` | 201 + Location | 400, 409 duplicate SKU, 422 unknown category |
| PUT | `/products/{id}` | 200 | 400, 404, 409, 422 |
| PATCH | `/products/{id}/stock` | 200 | 400, 404, 422 stock below 0 |
| DELETE | `/products/{id}` | 204 | 404, 409 in an active order |
| GET | `/orders?status&customerEmail&page&size` | 200 | 400 |
| GET | `/orders/{id}` | 200 | 404 |
| POST | `/orders` | 201 + Location | 400, 422 not enough stock / unknown product / too many products |
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
Created ids are saved into variables automatically.
