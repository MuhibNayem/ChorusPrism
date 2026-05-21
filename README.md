# Chorus Observe Server

Standalone observability and evaluation platform for LLM agents. Spring Boot 4 + Java 25.

## Quick Start

```bash
# Build
./gradlew bootJar

# Run with Docker Compose (PostgreSQL + ClickHouse + Server)
docker compose up -d

# API will be available at http://localhost:8080
```

## API Endpoints

| Endpoint | Description |
|---|---|
| `POST /v1/traces` | OTLP HTTP/JSON trace ingestion |
| `GET /api/v1/runs` | List runs with filtering/pagination |
| `GET /api/v1/runs/{runId}` | Get single run |
| `GET /api/v1/runs/{runId}/spans` | List spans for run |
| `GET /api/v1/runs/{runId}/llm-calls` | List LLM calls |
| `GET /api/v1/runs/{runId}/tool-calls` | List tool calls |
| `GET /api/v1/runs/{runId}/provenance` | Provenance DAG entries |
| `GET /api/v1/runs/{runId}/stream` | SSE real-time span stream |
| `GET /api/v1/metrics/dashboard` | Dashboard metrics |
| `POST /api/v1/runs/{runId}/feedback` | Submit feedback |

## Configuration

```yaml
chorus:
  observe:
    database:
      url: jdbc:postgresql://localhost:5432/chorus_observe
      username: chorus
      password: chorus
    clickhouse:
      url: jdbc:clickhouse://localhost:8123/chorus_observe
      username: chorus
      password: chorus
    storage:
      span-store: postgresql  # postgresql | clickhouse | dual
    grpc:
      enabled: true
      port: 4317
```

## Architecture

```
Any OTLP Source → /v1/traces → OtlpIngestionService → SpanStore → PostgreSQL/ClickHouse
                                        ↓
                                   RunRepository → PostgreSQL (relational)
                                        ↓
                                    REST API ← Chorus Studio UI
```

## Storage Backends

- **postgresql** (default) — ACID spans via JDBC repositories
- **clickhouse** — Columnar span storage for high throughput
- **dual** — Writes to both simultaneously

## License

Apache 2.0
