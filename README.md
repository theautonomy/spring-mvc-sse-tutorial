# SSE Tutorial (branch `sse-tutorial-vanilla`)

A step-by-step tutorial for **Server-Sent Events with Spring MVC (`SseEmitter`) and plain JavaScript (`EventSource`, `fetch`)**.

This branch takes the SSE part of the AI Nutrition Planner (on `main`) and teaches it on its own. All AI code and config is removed. What's left is a small Spring Boot app with one page per step, from hello world to a job that pauses to ask the user a question.

The browser side uses no framework. The `sse-tutorial` branch teaches the same steps with htmx and its SSE extension.

➡️ **Start with [TUTORIAL.md](TUTORIAL.md).**

| Step | Page | Topic |
| --- | --- | --- |
| 1 | `/step1` | Hello world: wire format, `EventSource`, the reconnect gotcha |
| 2 | `/step2` | A ticking clock: long-lived streams, named events, timeouts, cleanup |
| 3 | `/step3` | Progress of a long task: POST starts a job, HTML fragments as events |
| 4 | `/step4` | A live dashboard: one stream, many named events (admin) |
| 5 | `/step5` | A chat room: broadcast, heartbeats, `Last-Event-ID` catch-up |
| 6 | `/step6` | Ask the user: two-way flow over SSE + POST (admin) |

## Prerequisites

- **Java 25**
- **Maven** (wrapper included)

## Run

```bash
./mvnw spring-boot:run
```

Open [http://localhost:8080](http://localhost:8080) and log in as **alice**, **bob** or **admin** (password `password` for all). Spring Security protects every page and SSE stream; steps 4 and 6 are for **admin** only. If port 8080 is taken:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

## Test

```bash
./mvnw test
```
