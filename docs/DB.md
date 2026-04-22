# Database migration: H2 (dev) → MySQL (production on Railway)

This document describes how to move the `chat-api` service from the in-memory
H2 database (fine for local development and tests) to a managed MySQL instance
hosted on Railway for production, **without breaking the local workflow**.

The goal is:

- **Local / tests** → keep H2 (fast, zero setup, isolated per run).
- **Production (Railway)** → MySQL (persistent, survives redeploys and restarts).

No code is changed at this stage — this document is the plan.

---

## 1. Current state (as of today)

- JPA is already wired: `spring-boot-starter-data-jpa` is in `pom.xml`.
- Only the `ChatSession` entity (`anonymous_sessions` table) is persisted via
  `ChatSessionRepository`.
- `src/main/resources/application.properties` hard-codes H2:
  ```
  spring.datasource.url=jdbc:h2:mem:chatdb
  spring.datasource.driver-class-name=org.h2.Driver
  spring.datasource.username=sa
  spring.datasource.password=
  spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
  spring.jpa.hibernate.ddl-auto=create-drop
  ```
- `ddl-auto=create-drop` means the schema is recreated at every start and
  dropped at shutdown. This is acceptable for H2 in-memory but **must not** be
  used against MySQL in production (you would wipe real data on each deploy).

Consequence today: every Railway redeploy loses all sessions. Migrating to
MySQL fixes this and gives us a durable store.

---

## 2. High-level migration steps

1. Add the MySQL JDBC driver to `pom.xml`.
2. Split configuration by Spring profile (`dev` = H2, `prod` = MySQL).
3. Provision a MySQL database on Railway and link it to the `chat-api` service.
4. Map Railway's injected variables to Spring's datasource properties.
5. Change `ddl-auto` strategy for production (and ideally introduce Flyway).
6. Verify connection, schema creation, and application behavior in a staging
   deploy.
7. Cut over.

Each step is detailed below.

---

## 3. Step 1 — Add the MySQL driver

In `pom.xml`, next to the existing H2 dependency, add:

```xml
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
```

Notes:
- Spring Boot's BOM manages the version; do **not** pin it manually.
- Keep H2 with `<scope>runtime</scope>` (or move it to `<scope>test</scope>`
  if we want H2 only for tests — see §9).
- No application code change is needed: JPA/Hibernate pick the driver from the
  URL and dialect.

---

## 4. Step 2 — Split configuration by profile

Current single `application.properties` mixes app config and H2 datasource.
The cleanest approach is to split it:

- `application.properties` — common settings (app name, Spring AI, rate
  limits, CORS, API key, AWS, logging). No datasource settings.
- `application-dev.properties` — H2 datasource + `create-drop`. Activated by
  default locally.
- `application-prod.properties` — MySQL datasource, `validate` (or `none`) for
  `ddl-auto`, MySQL dialect. Activated on Railway.

Example `application-prod.properties` (content to add — **not applied yet**):

```properties
# Datasource — values come from Railway env vars (see §6)
spring.datasource.url=${SPRING_DATASOURCE_URL}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# JPA / Hibernate
spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.properties.hibernate.format_sql=true

# Connection pool (HikariCP defaults are sensible; tune if needed)
spring.datasource.hikari.maximum-pool-size=5
spring.datasource.hikari.minimum-idle=1
spring.datasource.hikari.connection-timeout=10000
```

Example `application-dev.properties` (keeps today's behavior):

```properties
spring.datasource.url=jdbc:h2:mem:chatdb
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=create-drop
```

Profile activation:
- Local: set `spring.profiles.active=dev` (or leave it as the default in
  `application.properties`).
- Railway: set env var `SPRING_PROFILES_ACTIVE=prod`.

---

## 5. Step 3 — Provision MySQL on Railway

In the Railway dashboard, on the same project as `chat-api`:

1. **New → Database → Add MySQL**. Railway creates a MySQL instance in the
   project.
2. Open the MySQL service → **Variables** tab. Railway auto-generates:
   - `MYSQLHOST`
   - `MYSQLPORT`
   - `MYSQLDATABASE`
   - `MYSQLUSER`
   - `MYSQLPASSWORD`
   - `MYSQL_URL` (a full JDBC-like URL, often `mysql://user:pass@host:port/db`)
   - `MYSQL_PUBLIC_URL` (for access from outside Railway — **do not use** from
     the app)
3. Note: variables named above belong to the MySQL service. To read them from
   `chat-api`, we use Railway's **reference variables** syntax (see §6).

Networking:
- Use the **private** networking between services on Railway — it is free,
  faster, and not exposed to the Internet. The MySQL service exposes a
  hostname like `mysql.railway.internal`. The `MYSQLHOST` variable already
  points to it inside the project.

---

## 6. Step 4 — Wire Railway variables into `chat-api`

On the `chat-api` service in Railway → **Variables** tab, add:

| Variable name                  | Value (reference syntax)                          |
|--------------------------------|---------------------------------------------------|
| `SPRING_PROFILES_ACTIVE`       | `prod`                                            |
| `SPRING_DATASOURCE_URL`        | `jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?useSSL=false&serverTimezone=UTC&characterEncoding=UTF-8` |
| `SPRING_DATASOURCE_USERNAME`   | `${{MySQL.MYSQLUSER}}`                            |
| `SPRING_DATASOURCE_PASSWORD`   | `${{MySQL.MYSQLPASSWORD}}`                        |

Notes:
- `${{MySQL.XXX}}` is Railway's reference-variable syntax. Replace `MySQL`
  with the actual name of the database service if it is different.
- `jdbc:mysql://…` is the **JDBC** URL format. Do not reuse the raw
  `MYSQL_URL` value directly — that one starts with `mysql://` and is not a
  JDBC URL.
- Spring Boot automatically binds env vars like `SPRING_DATASOURCE_URL` to the
  property `spring.datasource.url` — no code change required.

---

## 7. Step 5 — Schema management strategy

Two realistic options. I recommend **Option B** for production from day one.

### Option A — Let Hibernate create the schema once, then switch to `validate`

1. First deploy with `spring.jpa.hibernate.ddl-auto=update` → Hibernate
   creates `anonymous_sessions`.
2. Immediately after a successful startup, change the value to `validate`.

Pros: zero extra tooling.
Cons: manual, error-prone, no history of schema changes, no rollback.

### Option B — Introduce Flyway (recommended)

1. Add to `pom.xml`:
   ```xml
   <dependency>
       <groupId>org.flywaydb</groupId>
       <artifactId>flyway-core</artifactId>
   </dependency>
   <dependency>
       <groupId>org.flywaydb</groupId>
       <artifactId>flyway-mysql</artifactId>
   </dependency>
   ```
2. Create `src/main/resources/db/migration/V1__init.sql` with the DDL for
   `anonymous_sessions` (derived from the `ChatSession` entity — see §8).
3. Keep `spring.jpa.hibernate.ddl-auto=validate` in prod (Hibernate only
   checks the schema matches the entities; Flyway applies migrations).
4. Any future entity change → new `V2__xxx.sql`, `V3__xxx.sql`, etc.

Pros: versioned, auditable, idempotent, works the same on any developer's
machine and in CI.
Cons: a bit more discipline needed.

---

## 8. Expected initial schema (`V1__init.sql` if we go Flyway)

Derived from `ChatSession`:

```sql
CREATE TABLE anonymous_sessions (
    token           VARCHAR(64)  NOT NULL,
    fingerprint     VARCHAR(255),
    ip_address      VARCHAR(64),
    messages_used   INT          NOT NULL DEFAULT 0,
    messages_limit  INT          NOT NULL DEFAULT 0,
    tokens_used     INT          NOT NULL DEFAULT 0,
    tokens_limit    INT          NOT NULL DEFAULT 0,
    created_at      DATETIME(6),
    expires_at      DATETIME(6),
    last_used_at    DATETIME(6),
    revoked         BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (token)
);

CREATE INDEX idx_sessions_ip_expires ON anonymous_sessions (ip_address, expires_at);
CREATE INDEX idx_sessions_expires     ON anonymous_sessions (expires_at);
```

The two indexes directly support the existing repository queries:
- `countActiveSessionsByIp(ip, now)` → `idx_sessions_ip_expires`.
- `deleteExpiredBefore(cutoff)` → `idx_sessions_expires`.

Without these, cleanup and IP-quota checks will do full table scans as the
table grows.

---

## 9. Tests

- Unit and slice tests should keep using H2 (in-memory) for speed and
  isolation. Either:
  - add an `application-test.properties` that mirrors the dev datasource, and
    annotate tests with `@ActiveProfiles("test")`, **or**
  - use `@AutoConfigureTestDatabase` with an embedded H2.
- If we rely on MySQL-specific SQL later (JSON columns, `ON DUPLICATE KEY`,
  etc.), consider Testcontainers MySQL to get production-fidelity tests.
  That's out of scope today — H2 is enough given the current queries are
  standard JPQL.

---

## 10. Deployment checklist

Before merging:

- [ ] `mysql-connector-j` added to `pom.xml`.
- [ ] `application.properties` stripped of H2-specific settings.
- [ ] `application-dev.properties` created (H2 config moved here).
- [ ] `application-prod.properties` created (MySQL config, `ddl-auto=validate`).
- [ ] Flyway dependencies + `V1__init.sql` in place (if Option B).
- [ ] Tests still green locally (`./mvnw test`).

On Railway:

- [ ] MySQL service provisioned in the same project.
- [ ] `chat-api` service has `SPRING_PROFILES_ACTIVE=prod` and the three
      `SPRING_DATASOURCE_*` reference variables.
- [ ] First deploy logs show: HikariCP starting, Flyway applying `V1`,
      Hibernate reporting `Schema validation: success`.
- [ ] Smoke test: start a session via the API, confirm the row is present in
      MySQL, redeploy, confirm the row is still there.

---

## 11. Risks and rollback

- **Wrong JDBC URL format** → app fails to start. Fix the URL in the Railway
  env vars; no data loss.
- **`ddl-auto=update` left enabled in prod** → Hibernate can silently alter
  columns. Keep it on `validate` once `V1` is applied.
- **MySQL service stopped / scaled to zero** → `chat-api` health checks will
  fail. Railway's private networking is reliable but the DB must stay on.
- **Cost** → Railway MySQL is metered. Sessions expire quickly
  (`chat.session.ttl-minute=3`) and the cleanup task already deletes them,
  so the table stays small.
- **Rollback plan**: revert the PR, redeploy. As long as only `anonymous_sessions`
  lives in MySQL and sessions are short-lived, losing the table is acceptable
  in an emergency.

---

## 12. Summary — what changes where

| Area                              | Change                                              |
|-----------------------------------|-----------------------------------------------------|
| `pom.xml`                         | add `mysql-connector-j` (+ Flyway deps if Option B) |
| `application.properties`          | remove H2/JPA-specific lines, keep app config       |
| `application-dev.properties` (new)| H2 config (today's behavior)                        |
| `application-prod.properties` (new)| MySQL config, `ddl-auto=validate`                  |
| `db/migration/V1__init.sql` (new) | initial schema for `anonymous_sessions`             |
| Railway — MySQL service           | provision new MySQL database                        |
| Railway — `chat-api` variables    | `SPRING_PROFILES_ACTIVE=prod` + datasource refs     |
| Java code                         | **no change**                                       |
