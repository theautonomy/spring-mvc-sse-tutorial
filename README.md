# AI Nutrition Planner

A sample project demonstrating how to build AI agents with Spring AI.

**This is a copy of the Spring AI implementation from [SandraAhlgrimm/ai-nutrition-planner](https://github.com/SandraAhlgrimm/ai-nutrition-planner)**, a repository I created together with [Sandra Ahlgrimm](https://github.com/SandraAhlgrimm) for our talk comparing agentic Java frameworks. That repository implements the exact same nutrition planning use case three times — with Embabel, LangChain4j, and Spring AI — so the frameworks can be compared side by side.

[Slides: Building AI Agents with Spring AI](slides.pdf)

## Use Case

The agent creates a personalized weekly meal plan:

1. Fetches the user profile and seasonal ingredients in parallel
2. Generates recipes for the requested days and meals using seasonal produce
3. Validates the plan against the user profile (allergens, calorie limits, dietary restrictions)
4. Revises recipes based on feedback and re-validates — looping until the plan passes or a maximum number of iterations is reached

## Agentic AI Patterns Implemented

| Pattern | Implementation |
|---------|----------------|
| **Parallel Execution** — independent steps run concurrently | `Workflow.parallel()` |
| **Validation / Reflection Loop** — iterate until output passes quality checks | `ValidationRetryAdvisor` |
| **Tool Use** — agent calls external functions | `.tools()` on `ChatClient` |
| **Tool Search** — dynamic tool discovery at runtime | `ToolSearchToolCallAdvisor` — separate search step discovers tools via metadata before each call |
| **Human-in-the-Loop** — pause workflow for user input | `AskUserQuestionTool` |
| **Agent Skills** — invoke pre-packaged executable skills | `SkillsTool` |
| **Persona** — role-based system prompts per agent | custom implementation via `.system()` on `ChatClient` |
| **MCP Server** — expose agent as a Model Context Protocol tool | `@McpTool` |

## Prerequisites

- **Java 25**
- **Maven** (Maven wrapper included)
- **Docker Desktop** (for Grafana observability stack)
- An LLM provider: **Azure OpenAI**, or **OpenAI**

## Setup
### LLM Providers

**Azure OpenAI**:

```bash
export AZURE_OPENAI_ENDPOINT=https://your-resource.openai.azure.com/
export AZURE_OPENAI_API_KEY=your-api-key
export AZURE_OPENAI_DEPLOYMENT_NAME=gpt-4o
```

**OpenAI**:

```bash
export OPENAI_API_KEY=sk-...
```

## Run

Set the Spring profile related to an LLM provider and start the app:

```bash
export SPRING_PROFILES_ACTIVE=openai # or azure
./mvnw spring-boot:run
```

The app starts on port `8080`. Basic auth: `alice` / `123456`. UI at [http://localhost:8080](http://localhost:8080).

## Observability

A Grafana + OTLP stack (Loki, Tempo, Mimir) is included via Docker Compose.

- **Grafana**: [http://localhost:3000](http://localhost:3000) (admin/admin)
- **OTLP collector**: `localhost:4318` (HTTP) / `localhost:4317` (gRPC)

To start the docker compose container via Spring Boot and enable tracing and metrics export from any module, activate the `observability` profile
in addition to the profile for the LLM provider of choice:

```bash
SPRING_PROFILES_ACTIVE=openai,observability ./mvnw spring-boot:run
```
The dashboard shows agent invocation rates, execution durations (p95), active agents, HTTP endpoint latency, JVM metrics, and distributed traces.
