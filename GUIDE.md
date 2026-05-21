# Chorus Observe Server — Developer Guide

> **Goal:** Get from zero to production traces in 5 minutes.  
> **Phase:** 1 — ChorusWire Protocol + Persistence Foundation

---

## Table of Contents

1. [What You Get](#what-you-get)
2. [Prerequisites](#prerequisites)
3. [Quick Start (Standalone)](#quick-start-standalone)
4. [Quick Start (Embedded Library)](#quick-start-embedded-library)
5. [Sending Traces](#sending-traces)
   - [Chorus Engine (Java)](#chorus-engine-java)
   - [LangChain (Python)](#langchain-python)
   - [OpenAI SDK (Python)](#openai-sdk-python)
   - [Raw HTTP/JSON](#raw-httpjson)
6. [Configuration Reference](#configuration-reference)
7. [Database & Migrations](#database--migrations)
8. [REST API Cookbook](#rest-api-cookbook)
9. [Docker Deployment](#docker-deployment)
10. [Troubleshooting](#troubleshooting)
11. [Architecture for Contributors](#architecture-for-contributors)

---

## What You Get

Chorus Observe Server is a **standalone observability backend** for LLM agents. Think of it as LangSmith, but:

- **Framework-agnostic** — any OTel-compatible agent can send traces
- **Java-native** — first-class Java SDK, not a wrapper
- **Apache 2.0** — fully open source, free self-host
- **Zero-config for Chorus Engine** — one YAML line enables full tracing

**What ships in Phase 1:**

| Feature | Status |
|---|---|
| OTLP gRPC ingestion (port 4317) | ✅ |
| OTLP HTTP/JSON ingestion (`/v1/traces`) | ✅ |
| PostgreSQL persistence | ✅ |
| Flyway schema migrations | ✅ |
| REST API v1 (runs, spans, metrics, feedback) | ✅ |
| GenAI semantic convention spans | ✅ |
| Spring Boot auto-configuration | ✅ |
| Docker / Docker Compose | ✅ |

---

## Prerequisites

- **Java 25** with `--enable-preview`
- **Gradle 9.1+**
- **PostgreSQL 15+** (or use Docker Compose)
- Optional: **TimescaleDB** extension for time-series metrics

---

## Quick Start (Standalone)

Run Chorus Observe as its own Spring Boot application.

### 1. Clone & Build

```bash
git clone https://github.com/MuhibNayem/chorus-engine4j.git
cd chorus-engine4j
./gradlew :chorus-observe-server:bootJar
```

### 2. Start PostgreSQL

```bash
docker run -d \
  --name chorus-observe-db \
  -e POSTGRES_DB=chorus_observe \
  -e POSTGRES_USER=chorus \
  -e POSTGRES_PASSWORD=chorus \
  -p 5432:5432 \
  timescale/timescaledb-ha:pg16-latest
```

### 3. Run the Server

```bash
java \
  --enable-preview \
  --add-modules jdk.incubator.vector \
  -jar chorus-observe-server/build/libs/chorus-observe-server-*.jar \
  --chorus.observe.database.url=jdbc:postgresql://localhost:5432/chorus_observe \
  --chorus.observe.database.username=chorus \
  --chorus.observe.database.password=chorus
```

**Verify it's up:**
```bash
curl http://localhost:8080/actuator/health
# → {"status":"UP"}
```

---

## Quick Start (Embedded Library)

Add Chorus Observe to your existing Spring Boot application.

### 1. Add Dependency

**Gradle (Kotlin DSL):**
```kotlin
dependencies {
    implementation("io.github.muhibnayem:chorus-observe-server:0.1.0")
    // You already have a DataSource or configure one below
}
```

**Maven:**
```xml
<dependency>
    <groupId>io.github.muhibnayem</groupId>
    <artifactId>chorus-observe-server</artifactId>
    <version>0.1.0</version>
</dependency>
```

### 2. Configure `application.yml`

```yaml
chorus:
  observe:
    enabled: true
    database:
      # Option A: Dedicated database (recommended)
      url: jdbc:postgresql://localhost:5432/chorus_observe
      username: chorus
      password: chorus
      max-pool-size: 20
      migrate-on-startup: true

      # Option B: Leave url blank to reuse your app's DataSource
      # url: ""
    grpc:
      enabled: true
      port: 4317
```

### 3. That's It

Start your application. The server auto-wires:
- OTLP gRPC on port 4317
- REST API on your app's HTTP port
- Flyway migrations on first boot

---

## Sending Traces

### Chorus Engine (Java)

Add this to your `application.yml` or `chorus.yml`:

```yaml
chorus:
  observe:
    enabled: true
    endpoint: "http://localhost:4317"
    export-provenance: true
    sample-rate: 1.0
```

Zero code changes. The Spring Boot starter auto-wires `ChorusOtlpExporter`, which subscribes to the `EventBus` and exports every `ChorusEvent` as OTLP spans with full GenAI semantic conventions.

**What gets exported automatically:**
- `AgentStartEvent` → `gen_ai.agent.run` span
- `LlmCallEvent` → `gen_ai.chat` span with `gen_ai.usage.input_tokens`, `gen_ai.request.model`, etc.
- `ToolCallEvent` → `gen_ai.tool.use` span
- `GuardrailEvent` → `chorus.guardrail` span with tier info
- `CheckpointEvent` → `chorus.checkpoint` span (if `export-provenance: true`)

### LangChain (Python)

```python
import os
from opentelemetry import trace
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from opentelemetry.instrumentation.langchain import LangChainInstrumentor

# Point to Chorus Observe
os.environ["OTEL_EXPORTER_OTLP_ENDPOINT"] = "http://localhost:4317"

provider = TracerProvider()
processor = BatchSpanProcessor(OTLPSpanExporter())
provider.add_span_processor(processor)
trace.set_tracer_provider(provider)

LangChainInstrumentor().instrument()

# Your existing LangChain code — zero other changes
from langchain_openai import ChatOpenAI
llm = ChatOpenAI()
llm.invoke("Hello, world!")
```

Traces appear in Chorus Observe with `gen_ai.*` attributes and `framework=langchain`.

### OpenAI SDK (Python)

```python
from opentelemetry.instrumentation.openai import OpenAIInstrumentor
OpenAIInstrumentor().instrument()

import openai
client = openai.OpenAI()
client.chat.completions.create(model="gpt-4o", messages=[{"role": "user", "content": "Hi"}])
```

### Raw HTTP/JSON

POST to `http://localhost:4318/v1/traces`:

```bash
curl -X POST http://localhost:4318/v1/traces \
  -H "Content-Type: application/json" \
  -d '{
    "resourceSpans": [{
      "scopeSpans": [{
        "spans": [{
          "traceId": "abc123",
          "spanId": "span-1",
          "name": "my-agent-run",
          "kind": 1,
          "startTimeUnixNano": "1700000000000000000",
          "endTimeUnixNano": "1700000001000000000",
          "attributes": [
            {"key": "gen_ai.system", "value": {"stringValue": "openai"}},
            {"key": "gen_ai.request.model", "value": {"stringValue": "gpt-4o"}},
            {"key": "gen_ai.usage.input_tokens", "value": {"intValue": 100}},
            {"key": "gen_ai.usage.output_tokens", "value": {"intValue": 50}}
          ],
          "events": [],
          "status": {"code": 1}
        }]
      }]
    }]
  }'
```

---

## Configuration Reference

All properties under `chorus.observe.*`.

```yaml
chorus:
  observe:
    # Master switch
    enabled: true

    server:
      # HTTP port for REST API (only used in standalone mode)
      port: 8080

    database:
      # REQUIRED: JDBC URL, or leave blank to reuse app's DataSource
      url: ""
      username: ""
      password: ""
      max-pool-size: 20
      migrate-on-startup: true

    grpc:
      enabled: true
      port: 4317
```

**Spring Boot standard properties also work:**

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/chorus_observe
    username: chorus
    password: chorus
```

If you set `spring.datasource.*` and leave `chorus.observe.database.url` blank, Chorus Observe will reuse your existing connection pool.

---

## Database & Migrations

### Schema Overview

| Table | Purpose |
|---|---|
| `runs` | Agent execution records |
| `spans` | Operations within a run |
| `llm_calls` | LLM invocations with tokens, cost, latency |
| `tool_calls` | Tool invocations with args, results, errors |
| `feedback` | Human / automated scores and comments |
| `metric_snapshots` | Time-series metrics (TimescaleDB hypertable) |

### Manual Migration (if not using Flyway)

```bash
psql -h localhost -U chorus -d chorus_observe \
  -f chorus-observe-server/src/main/resources/db/migration/V1__init_schema.sql
```

### Connecting to Existing PostgreSQL

```yaml
chorus:
  observe:
    database:
      url: jdbc:postgresql://prod-db.internal:5432/chorus_observe
      username: ${DB_USER}
      password: ${DB_PASSWORD}
      migrate-on-startup: false  # You manage migrations
```

---

## REST API Cookbook

### List Runs

```bash
curl "http://localhost:8080/api/v1/runs?framework=chorus&status=SUCCESS&limit=10"
```

**Query params:** `framework`, `agentId`, `status`, `from`, `to`, `tagKey`, `tagValue`, `sortBy`, `sortOrder`, `limit`, `offset`

### Get Run Detail

```bash
curl http://localhost:8080/api/v1/runs/run-abc123
```

### Get Span Waterfall

```bash
curl http://localhost:8080/api/v1/runs/run-abc123/spans
```

### Get LLM Calls

```bash
curl http://localhost:8080/api/v1/runs/run-abc123/llm-calls
```

### Submit Feedback

```bash
curl -X POST http://localhost:8080/api/v1/runs/run-abc123/feedback \
  -H "Content-Type: application/json" \
  -d '{"score": 4.5, "label": "good", "comment": "Accurate response", "source": "human"}'
```

### Cost Metrics

```bash
curl "http://localhost:8080/api/v1/metrics/cost?from=2026-05-01T00:00:00Z&to=2026-05-21T00:00:00Z"
```

---

## Docker Deployment

### Docker Compose (Full Stack)

```bash
docker compose -f docker-compose.observe.yml up -d
```

This starts PostgreSQL + TimescaleDB and Chorus Observe Server.

### Standalone Container

```bash
./gradlew :chorus-observe-server:bootJar
docker build -t chorus-observe:latest -f chorus-observe-server/Dockerfile .
docker run -p 8080:8080 -p 4317:4317 \
  -e CHORUS_OBSERVE_DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/chorus_observe \
  -e CHORUS_OBSERVE_DATABASE_USERNAME=chorus \
  -e CHORUS_OBSERVE_DATABASE_PASSWORD=chorus \
  chorus-observe:latest
```

---

## Troubleshooting

### "Chorus Observe requires a DataSource"

You didn't configure a database URL and no primary `DataSource` bean exists.

**Fix:**
```yaml
chorus:
  observe:
    database:
      url: jdbc:postgresql://localhost:5432/chorus_observe
      username: chorus
      password: chorus
```

### "Failed to ingest traces" (HTTP 400)

Your OTLP JSON payload doesn't match the expected shape.

**Fix:** Ensure your JSON follows the [OTLP HTTP JSON spec](https://opentelemetry.io/docs/specs/otlp/):
- `traceId` and `spanId` as hex strings
- `startTimeUnixNano` and `endTimeUnixNano` as strings or longs
- `attributes` as array of `{key, value}` objects

### "Permission denied" on database

The database user needs CREATE TABLE privileges for Flyway migrations.

**Fix:**
```sql
GRANT ALL PRIVILEGES ON DATABASE chorus_observe TO chorus;
```

### gRPC port already in use

```yaml
chorus:
  observe:
    grpc:
      port: 14317  # Pick another port
```

### I don't see any traces

1. Check that the exporter endpoint points to the right host:
   ```yaml
   chorus:
     observe:
       endpoint: "http://localhost:4317"
   ```
2. Check `sample-rate`: `1.0` = 100%, `0.1` = 10%
3. Verify the OTLP payload has `gen_ai.system` or `chorus.run_id` attributes (otherwise spans are stored but may not show LLM-specific enrichment)

---

## Architecture for Contributors

```
chorus-observe-server/
├── api/              ← REST controllers + OTLP intake
│   ├── RunController.java
│   ├── OtlpHttpController.java
│   ├── OtlpGrpcService.java
│   └── dto/          ← Type-safe OTLP DTOs (zero unchecked casts)
├── config/           ← Spring Boot auto-configuration
│   ├── ChorusObserveProperties.java
│   └── ChorusObserveAutoConfiguration.java
├── model/            ← Domain records (Run, Span, LlmCall, ...)
├── persistence/      ← JDBC repositories (no JPA)
│   ├── RunRepository.java
│   ├── SpanRepository.java
│   └── ...
├── service/          ← Business logic
│   ├── OtlpIngestionService.java
│   ├── RunService.java
│   └── ...
└── resources/
    └── db/migration/ ← Flyway SQL scripts
```

### Design Decisions

| Decision | Rationale |
|---|---|
| **Raw JDBC, not JPA** | Avoids CGLIB proxies → GraalVM native-image compatible |
| **No hardcoded DB URL** | Library must not assume infrastructure |
| **DTOs for OTLP JSON** | Eliminates `@SuppressWarnings("unchecked")` anti-patterns |
| `@ConditionalOnMissingBean` | Every bean is overridable by the user |
| **Hand-written fakes in tests** | Zero Mockito, per project convention |

---

*Need help? Open an issue at https://github.com/MuhibNayem/chorus-engine4j/issues*
