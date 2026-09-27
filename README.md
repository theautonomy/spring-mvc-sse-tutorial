# SSE Tutorial (branch `sse-tutorial-jte`)

A step-by-step tutorial for **Server-Sent Events with Spring MVC (`SseEmitter`), htmx and [jte](https://jte.gg) templates**. The `sse-tutorial` branch has the same tutorial with Thymeleaf.

The purpose of this repo is to demo SSE with Spring MVC. It is a small Spring Boot app with one page per step, from hello world to a job that pauses to ask the user a question.

The browser side uses HTMX.

➡️ **Start with [TUTORIAL.md](TUTORIAL.md).**

| Step | Page | Topic |
| --- | --- | --- |
| 1 | `/step1` | Hello world: wire format, `EventSource`, the reconnect gotcha |
| 2 | `/step2` | A ticking clock with htmx: long-lived streams, timeouts, cleanup |
| 3 | `/step3` | Progress of a long task: POST starts a job, HTML fragments as events |
| 4 | `/step4` | A live dashboard: one stream, many named events |
| 5 | `/step5` | A chat room: broadcast, heartbeats, `Last-Event-ID` catch-up |
| 6 | `/step6` | Ask the user: two-way flow over SSE + POST |

## Prerequisites

- **Java 25**
- **Maven** (wrapper included)

## Run

```bash
./mvnw spring-boot:run
```

Open [http://localhost:8080](http://localhost:8080). If port 8080 is taken:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

To edit the jte templates in `src/main/jte` and see changes without restarting, use the `dev` profile:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## Test

```bash
./mvnw test
```
