## ADR-0001: H2 file database as the default persistence

**Status:** Accepted

**Context**
The app needs persistence for tasks and tracked time, but it is a small
single-user local tool. Introducing a PostgreSQL instance is heavier than the
app warrants and would require a running database for `mvn test` and
`spring-boot:run`.

**Options Considered**
1. **H2 file database** — embedded, zero-setup, file-backed (data survives
   restarts).
   - ✅ Pros: no external service; works out of the box; full SQL; Flyway
     migrations run against it.
   - ❌ Cons: single-process (no concurrent multi-machine access); H2-specific
     SQL (but we keep it plain ANSI SQL).
2. **PostgreSQL** — robust, multi-client, production-grade.
   - ✅ Pros: scalable; widely used.
   - ❌ Cons: requires a running server; overkill for a local single-user tool;
     complicates tests and first run.

**Decision**
We choose **H2 file database** (`jdbc:h2:file:./data/tasktracker`) as the
default. The datasource is externalized in `application.yml`, so switching to
PostgreSQL (or any JDBC DB) is a config change plus the matching Flyway dialect.

**Consequences**
- `mvn test` and `mvn spring-boot:run` work with zero external setup.
- Data lives in `./data/`; deleting it resets the app.
- If multi-user/multi-host access is ever required, migrate the datasource and
  add the `flyway-database-postgresql` dependency (SQL is already ANSI-compatible).
