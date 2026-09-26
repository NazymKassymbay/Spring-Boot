# Spring Boot course project

`practice1-2/` — greeting REST service (Practice 1–3)

## Practice 3 — Dependency Injection and a conditional bean

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