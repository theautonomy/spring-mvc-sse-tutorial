# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

A step-by-step tutorial app for Server-Sent Events with Spring MVC. Each step is a page at `/stepN`. `TUTORIAL.md` explains every step; keep it in sync with the code.

Branches: `sse-tutorial` (htmx), `sse-tutorial-vanilla` (plain `EventSource` + `fetch`), `sse-tutorial-vanilla-security` (adds Spring Security and steps 7–8 with H2).

## Stack

- Java 25, Spring Boot 4.1, Maven (wrapper included)
- Spring MVC with `SseEmitter`, virtual threads (`spring.threads.virtual.enabled`)
- Thymeleaf for pages and for the HTML sent as event data
- Browser: plain `EventSource` and `fetch` in inline scripts; Tailwind from a CDN for styling only
- Spring Security with in-memory users; Spring JDBC (`JdbcClient`) with in-memory H2 for steps 7–8
- Tests: JUnit 5, MockMvc, Spring Security Test

## Commands

```bash
./mvnw spring-boot:run                 # http://localhost:8080, log in as alice/password (admin/password for steps 4 and 6)
./mvnw test
./mvnw test -Dtest=JobRunnerTests      # one class; add #methodName for one test
```

## Architecture

- One package per step (`com.example.sse.stepN`): page, SSE endpoint, POSTs. Pages are `templates/stepN.html`.
- Event data is HTML: controllers render `templates/fragments/stepN.html` with `FragmentRenderer`, and the page's inline script inserts it. Use `th:text` for anything user-supplied.
- Jobs (steps 3, 6, 7, 8) always end with a `done` event, and the page calls `es.close()` on it. Otherwise the browser reconnects forever.
- Step 8 keeps job state in the database; its stream polls that state instead of receiving events from the workers.
- Security: `/step4/**` and `/step6/**` need ADMIN. `fetch` POSTs must send `headers: csrfHeaders()` (defined in `layout.html`).
