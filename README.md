# Task Tracker

A small bilingual (German/English) web application for task tracking with a
per-task time tracker.

- **Backend:** Spring Boot 4.1 / Java 21, Spring MVC + Thymeleaf, Spring Data JPA,
  Flyway, H2 file database
- **Frontend:** Tailwind CSS (Play CDN) + vanilla JS, Toastify for notifications
- **i18n:** all UI text externalized in `messages*.properties`, switchable in the
  top-right menu (EN / DE)

## Features

- Create, rename, status-change (To do / In progress / Done), delete tasks
- **Time tracking:** start/stop a timer per task — one task can be tracked at a time
- Live elapsed time (ticks in the browser), plus the accumulated **tracked** time
- Total tracked time across all tasks in the header
- All user-facing text is externalized and available in **English** (default) and
  **German**

## Run

```bash
mvn spring-boot:run
```

Then open <http://localhost:8080>. Data is stored in `./data/tasktracker.mv.db`
(H2 file). Delete that file for a clean slate.

### Switch language

Use the **EN / DE** toggle in the top-right menu (sets the session locale via
`?lang=de`). All UI strings, validation messages and error responses follow the
selected language.

## API

| Method | Path | Description |
|---|---|---|
| GET | `/` | Task list page (HTML, i18n) |
| POST | `/tasks` | Create a task (`title` form field), PRG |
| PATCH | `/tasks/{id}` | Update `title` and/or `status`, PRG |
| POST | `/tasks/{id}/delete` | Delete a task, PRG |
| POST | `/api/tasks/{id}/start` | Start tracking (JSON `TaskResponse`) |
| POST | `/api/tasks/{id}/stop` | Stop tracking (JSON `TaskResponse`) |

Error responses are RFC 7807 `ProblemDetail` (`404`, `409`, `400`), with
i18n-resolved `detail` messages.

## Time-tracking model

- `tracked_seconds` — accumulated tracked time (persisted; survives restarts)
- `started_at` — epoch `Instant` when the current session started (nullable)
- Elapsed time is always `now - started_at` (server clock), so it is stable
  across restarts and timezone changes; no wall-clock accumulation in the DB.
- Stopping adds `now - started_at` to `tracked_seconds` and clears `started_at`.
- At most **one** task may be running at a time.

## Testing

```bash
mvn test
```

- `TaskServiceTest` — Mockito unit tests (create, ordering, start/stop state
  machine, not-found, update/delete) using a **fixed Clock** for deterministic
  elapsed-time assertions
- `TaskControllerTest` — standalone MockMvc tests (routing, PRG redirects, JSON
  payloads, 404/409/400 status codes)
- `TaskRepositoryTest` — `@DataJpaTest` against real H2 (derived queries, unique
  index, auditing)
- `I18nSmokeTest` — full `@SpringBootTest` context: Flyway + schema validate,
  Thymeleaf rendering, EN and German (umlaut) output, create→start→stop round trip

## Project structure

```
src/main/java/com/example/tasktracker/
├── config/        # WebConfig (i18n), JpaAuditingConfig, AppBeans (Clock)
├── domain/        # Task entity + Status enum
├── dto/           # TaskResponse (API boundary record)
├── exception/     # TaskNotFound, InvalidTaskState, GlobalExceptionHandler
├── repository/    # TaskRepository (Spring Data JPA)
├── service/       # TaskService (business logic)
└── web/           # TaskController (Thymeleaf + JSON)
src/main/resources/
├── db/migration/  # V1__create_tasks_table.sql (Flyway, idempotent)
├── templates/     # layout/base.html + tasks/list.html
├── messages.properties      # English (default)
├── messages_de.properties   # German
└── application.yml
```

## Architecture decisions

See [`docs/adr/`](adr/):

- [ADR-0001](adr/0001-h2-file-database.md) — H2 file DB as default persistence
- [ADR-0002](adr/0002-server-rendered-with-tailwind.md) — Server-rendered Thymeleaf + Tailwind CDN
- [ADR-0003](adr/0003-i18n-session-locale.md) — i18n via SessionLocaleResolver + `?lang=`
