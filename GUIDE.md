# Chorus Observe Server — Developer Guide

> **From zero to production traces in 5 minutes — and deep understanding of every layer.**

---

## Table of Contents

1. [What You Get](#1-what-you-get)
2. [Architecture Overview](#2-architecture-overview)
3. [Project Structure](#3-project-structure)
4. [Database Schema](#4-database-schema)
5. [Authentication & Authorization](#5-authentication--authorization)
6. [Trace Ingestion Pipeline](#6-trace-ingestion-pipeline)
7. [Span Storage Backends](#7-span-storage-backends)
8. [Evaluation Framework](#8-evaluation-framework)
9. [Red Teaming](#9-red-teaming)
10. [Time-Travel Debugging](#10-time-travel-debugging)
11. [Alerting System](#11-alerting-system)
12. [Budget Enforcement](#12-budget-enforcement)
13. [Prompt A/B Testing](#13-prompt-ab-testing)
14. [Trace Clustering](#14-trace-clustering)
15. [Multi-Turn Testing](#15-multi-turn-testing)
16. [Data Retention & Export](#16-data-retention--export)
17. [Notifications](#17-notifications)
18. [Custom Dashboards](#18-custom-dashboards)
19. [SQL Query Defense](#19-sql-query-defense)
20. [Distributed Locking](#20-distributed-locking)
21. [Testing Strategy](#21-testing-strategy)
22. [Configuration](#22-configuration)
23. [Docker Deployment](#23-docker-deployment)
24. [Troubleshooting](#24-troubleshooting)

---

## 1. What You Get

Chorus Observe Server is a **standalone observability backend** for LLM agents. Think of it as LangSmith, but:

- **Framework-agnostic** — any OTel-compatible agent can send traces
- **Java-native** — first-class Java SDK, not a wrapper
- **Apache 2.0** — fully open source, free self-host
- **Zero-config for Chorus Engine** — one YAML line enables full tracing
- **Enterprise-grade** — RBAC, audit logging, budget enforcement, distributed locking, defense-in-depth SQL

### Enterprise Feature Matrix

| Feature | Status |
|---|---|
| OTLP gRPC/HTTP ingestion | ✅ |
| PostgreSQL persistence + Flyway | ✅ |
| REST API v1 (runs, spans, metrics, feedback) | ✅ |
| GenAI semantic convention spans | ✅ |
| **JWT Authentication** | ✅ |
| **RBAC (users, roles, permissions)** | ✅ |
| **Database-backed API keys** | ✅ |
| **Audit logging** | ✅ |
| **Rate limiting** | ✅ |
| **Budget enforcement** | ✅ |
| **Distributed locking** | ✅ |
| **Sampling (random/head/tail)** | ✅ |
| **Data retention scheduler** | ✅ |
| **Export (JSON/CSV/Parquet)** | ✅ |
| **Alerting (SQL rules + webhooks)** | ✅ |
| **Multi-channel notifications** | ✅ |
| **Custom dashboards** | ✅ |
| **Eval framework (exact_match/contains/llm_judge)** | ✅ |
| **Red teaming with guardrail integration** | ✅ |
| **Prompt A/B testing** | ✅ |
| **Trace clustering (DBSCAN)** | ✅ |
| **Multi-turn testing** | ✅ |
| **ClickHouse dual-write** | ✅ |
| **Time-travel debugging** | ✅ |
| **SQL query interface** | ✅ |

---

## 2. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Client Applications                                   │
│     (Your Agent System, Chorus Engine, OpenTelemetry SDKs)                   │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    ▼               ▼               ▼
            ┌──────────┐    ┌──────────┐    ┌──────────┐
            │ OTLP/gRPC│    │ OTLP/HTTP│    │ REST API │
            │  :4317   │    │ /v1/traces│   │ /api/v1/*│
            └────┬─────┘    └────┬─────┘    └────┬─────┘
                 │               │               │
                 └───────────────┼───────────────┘
                                 ▼
                    ┌────────────────────────┐
                    │  OtlpIngestionService  │
                    │  (Sampler → Accumulator)│
                    └───────────┬────────────┘
                                │
            ┌───────────────────┼───────────────────┐
            ▼                   ▼                   ▼
    ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
    │  PostgreSQL  │   │  ClickHouse  │   │   RunRepo    │
    │  (primary)   │   │  (analytics) │   │  (relational)│
    └──────────────┘   └──────────────┘   └──────────────┘
            │                   │                   │
            └───────────────────┼───────────────────┘
                                ▼
                    ┌────────────────────────┐
                    │    REST API Layer      │
                    │  (Controllers + Auth)  │
                    └────────────────────────┘
```

### Key Design Decisions

| Decision | Rationale |
|---|---|
| **Raw JDBC, not JPA** | Avoids CGLIB proxies → GraalVM native-image compatible |
| **No hardcoded DB URL** | Library must not assume infrastructure |
| **DTOs for OTLP JSON** | Eliminates `@SuppressWarnings("unchecked")` anti-patterns |
| `@ConditionalOnMissingBean` | Every bean is overridable by the user |
| **Hand-written fakes in tests** | Zero Mockito, per project convention |
| **Records for domain models** | Immutable data carriers, no Lombok |
| **Sealed interfaces for sum types** | `Result<T, E>`, `AgentEvent`, `StreamEvent` |
| **Virtual threads for I/O** | `CompletableFuture.runAsync(virtualThreadExecutor)` for evals, red-team |

---

## 3. Project Structure

```
chorus-observe-server/
├── api/
│   ├── controller/          ← REST controllers
│   │   ├── RunController.java
│   │   ├── EvalController.java
│   │   ├── RedTeamController.java
│   │   ├── AlertController.java
│   │   ├── DashboardController.java
│   │   ├── SqlQueryController.java
│   │   ├── AuthController.java
│   │   ├── UserController.java
│   │   └── MultiTurnController.java
│   └── dto/                 ← Type-safe request/response DTOs
├── audit/
│   └── AuditLogAspect.java  ← AOP-based immutable audit logging
├── budget/
│   ├── BudgetAwareAgentInvoker.java
│   ├── BudgetExceededException.java
│   └── PricingTable.java
├── cluster/
│   ├── TraceClusteringEngine.java
│   └── EmbeddingClusterer.java
├── config/
│   ├── ChorusObserveProperties.java
│   └── ChorusObserveAutoConfiguration.java
├── eval/
│   ├── ParallelEvalRunner.java
│   ├── ExactMatchScorer.java
│   ├── ContainsScorer.java
│   └── LlmJudgeScorer.java
├── export/
│   └── ExportService.java
├── grpc/
│   └── OtlpGrpcService.java
├── lock/
│   ├── JdbcDistributedLock.java
│   ├── DistributedLockRegistry.java
│   └── DistributedLockReaper.java
├── model/
│   └── *.java               ← Domain records (Run, Span, LlmCall, ...)
├── notification/
│   ├── NotificationService.java
│   ├── SlackDispatcher.java
│   ├── PagerDutyDispatcher.java
│   ├── EmailDispatcher.java
│   └── WebhookDispatcher.java
├── persistence/
│   ├── repository/          ← JDBC repositories (no JPA)
│   │   ├── RunRepository.java
│   │   ├── SpanRepository.java
│   │   └── ...
│   └── memory/              ← In-memory fakes for tests
├── prompt/
│   └── PromptAbTestExecutor.java
├── retention/
│   └── DataRetentionScheduler.java
├── security/
│   ├── ApiKeyAuthFilter.java
│   ├── JwtAuthFilter.java
│   ├── RbacAuthorizationFilter.java
│   ├── RateLimitFilter.java
│   ├── JwtTokenService.java
│   └── AuthenticationService.java
├── service/
│   ├── OtlpIngestionService.java
│   ├── RunService.java
│   ├── EvalService.java
│   ├── RedTeamService.java
│   ├── AlertService.java
│   ├── AlertScheduler.java
│   ├── AlertConditionEvaluator.java
│   ├── SqlQueryService.java
│   ├── BudgetService.java
│   ├── TraceClusterService.java
│   ├── MultiTurnTestService.java
│   ├── ExportService.java
│   ├── CustomDashboardService.java
│   └── NotificationService.java
├── stats/
│   └── WelchTTest.java      ← Statistical significance (no external math lib)
└── resources/
    └── db/migration/        ← Flyway SQL scripts V1–V5
```

---

## 4. Database Schema

### V1 — Core Observability

| Table | Purpose |
|---|---|
| `runs` | Agent execution records (agent, model, status, cost, latency) |
| `spans` | OpenTelemetry spans within a run |
| `llm_calls` | LLM invocations with tokens, cost, latency |
| `tool_calls` | Tool invocations with args, results, errors |
| `feedback` | Human / automated scores and comments |
| `metric_snapshots` | Time-series metrics |

### V2 — Enterprise Foundation

| Table | Purpose |
|---|---|
| `tenants` | Multi-tenant isolation |
| `users` | Tenant-scoped users with bcrypt password hashes |
| `roles` | Named roles with permission arrays |
| `user_roles` | Many-to-many user-role assignments |
| `api_keys` | Database-backed API keys with scopes and expiry |
| `audit_logs` | Immutable append-only audit trail |
| `retention_policies` | Per-resource-type retention rules |
| `budget_events` | Budget spend tracking |
| `budget_enforcements` | Active budget limits per agent |
| `distributed_locks` | Table-based distributed locks with TTL |

### V3 — Evaluation & Testing

| Table | Purpose |
|---|---|
| `datasets` | Evaluation datasets with items |
| `eval_runs` | Eval run lifecycle (PENDING → RUNNING → COMPLETED/FAILED) |
| `eval_results` | Per-item scores and metadata |
| `prompt_versions` | Versioned prompt templates |
| `prompt_ab_tests` | A/B test definitions linking two prompts to a dataset |
| `red_team_scenarios` | Adversarial scenario definitions |
| `red_team_runs` | Red team execution results |
| `red_team_results` | Per-scenario guardrail bypass results |
| `multi_turn_scenarios` | Multi-turn conversation test definitions |
| `multi_turn_runs` | Multi-turn run lifecycle |
| `multi_turn_turns` | Individual turns with expected keywords |

### V4 — Alerting

| Table | Purpose |
|---|---|
| `alert_rules` | SQL-based alert rule definitions |
| `alert_events` | Triggered alert instances with cooldown tracking |
| `notification_channels` | Reusable notification channel configs |
| `alert_rule_channels` | Many-to-many rule-channel links |

### V5 — Dashboards & Clustering

| Table | Purpose |
|---|---|
| `dashboards` | Custom dashboard definitions |
| `dashboard_widgets` | SQL-driven widgets per dashboard |
| `trace_clusters` | DBSCAN cluster labels and stats |
| `trace_embeddings` | Embedding vectors for trace clustering |
| `export_jobs` | Async export job tracking |

---

## 5. Authentication & Authorization

### 5.1 Filter Chain

Requests pass through a layered filter chain:

```
Request → ApiKeyAuthFilter → JwtAuthFilter → RateLimitFilter → RbacAuthorizationFilter → Controller
```

| Filter | Purpose |
|---|---|
| `ApiKeyAuthFilter` | Validates `X-API-Key` against `api_keys` table; exempts `/actuator/health`, `/v3/api-docs`, `/swagger-ui` |
| `JwtAuthFilter` | Validates `Authorization: Bearer <token>` via `JwtTokenService` |
| `RateLimitFilter` | Token-bucket rate limiting per API key / user |
| `RbacAuthorizationFilter` | Checks `@RequirePermission("evals:write")` annotations on controller methods |

### 5.2 JWT Token Service

- `JwtTokenService.generateToken()` — Creates JWT with tenant ID, user ID, roles, permissions
- `JwtTokenService.validateToken()` — Verifies signature, expiry, and tenant scope
- Secret auto-generated on first boot if not configured (logged once, **must** be persisted)
- Default expiry: 60 minutes (configurable via `jwt.expiryMinutes`)

### 5.3 API Keys

- Stored in `api_keys` with bcrypt-hashed key value
- Scopes: `read`, `write`, `admin`
- Optional expiration date
- Raw key returned **once** on creation; only the hash is stored

### 5.4 Permission Model

Permissions are strings like `runs:read`, `evals:write`, `admin`. The `RbacAuthorizationFilter` checks the `@RequirePermission` annotation against the authenticated principal's permission set.

---

## 6. Trace Ingestion Pipeline

### 6.1 OTLP Intake

```
OTLP Request → OtlpHttpController / OtlpGrpcService
           → OtlpIngestionService.ingest()
           → Sampler.decide() (if enabled)
           → Accumulator (per-run buffer, 5 min TTL)
           → Bulk INSERT into spans / llm_calls / tool_calls
```

### 6.2 Sampler

The `Sampler` interface supports three strategies:

| Strategy | Class | Behavior |
|---|---|---|
| Random | `RandomSampler` | Independent `Math.random() < rate` decision per trace |
| Head-based | `HeadBasedSampler` | Deterministic hash of trace ID at root, cached for all child spans |
| Tail-based | `TailBasedSampler` | Keeps errors and p99-latency traces regardless of rate |

### 6.3 Accumulator

- Buffers spans per `runId` in a `ConcurrentHashMap`
- Flushes in bulk every N spans or on TTL expiry (5 minutes)
- TTL eviction prevents unbounded memory growth for incomplete traces

### 6.4 Real-Time Streaming

- `RunController.stream()` returns `SseEmitter`
- Per-run subscriber map with max 100 concurrent subscribers
- 6-minute idle eviction prevents emitter leaks

---

## 7. Span Storage Backends

### 7.1 SpanStore Interface

```java
public interface SpanStore {
    void save(Run run, List<Span> spans, List<LlmCall> llmCalls, List<ToolCall> toolCalls);
    List<Span> findByRunId(String runId);
}
```

Implementations:
- `JdbcSpanStore` — PostgreSQL via raw JDBC
- `ClickHouseSpanStore` — ClickHouse with JSON attribute/event parsing via `ObjectMapper`
- `DualWriteSpanStore` — Writes to both, reads from PostgreSQL

### 7.2 ClickHouse JSON Parsing

`ClickHouseSpanStore` parses span attributes and events from OTLP JSON using `ObjectMapper`:

```java
Map<String, Object> attributes = objectMapper.readValue(json, new TypeReference<>() {});
```

This requires the `clickhouse-jdbc` driver and a properly configured `ObjectMapper` with `JavaTimeModule`.

---

## 8. Evaluation Framework

### 8.1 Scorer Resolution

`EvalService.resolveScorer(String name)` maps scorer names to implementations:

| Name | Class | Scoring |
|---|---|---|
| `exact_match` | `ExactMatchScorer` | `expected.equals(actual)` |
| `contains` | `ContainsScorer` | `actual.toLowerCase().contains(expected.toLowerCase())` |
| `llm_judge` | `LlmJudgeScorer` | LLM-as-judge with threshold (default 0.7) |

### 8.2 Async Execution

`executeEvalRun()` uses `CompletableFuture.runAsync()` with a virtual-thread executor:

```java
CompletableFuture.runAsync(() -> {
    for (DatasetItem item : dataset.getItems()) {
        String output = agentInvoker.invoke(item.input(), config);
        double score = scorer.score(item.expectedOutput(), output);
        evalResultRepository.save(...);
        // Progress save every 10%
    }
}, virtualThreadExecutor);
```

### 8.3 Crash Recovery

`@PostConstruct recoverStaleRuns()` marks any eval run in `RUNNING` for >30 minutes as `FAILED` with a recovery note. Same mechanism for red-team runs.

### 8.4 Regression Detection

`compareRuns()` computes per-item score deltas and categorizes:
- **Regression**: score dropped by ≥ threshold
- **Improvement**: score increased by ≥ threshold
- **Unchanged**: within threshold

---

## 9. Red Teaming

### 9.1 Guardrail Integration

`RedTeamService.executeRedTeamRun()` invokes the agent and then calls `TieredGuardrailEngine.evaluateOutput()`:

```java
var guardrailResult = guardrailEngine.evaluateOutput(scenario.getAttackPrompt(), output);
```

Results stored:
- `BYPASSED` — Guardrail did not catch the attack
- `BLOCKED` — Guardrail blocked the output
- `NO_ENGINE` — No guardrail engine configured (honest, no fake bypass)

### 9.2 Crash Recovery

Same 30-minute stale `RUNNING` recovery as eval runs.

---

## 10. Time-Travel Debugging

### 10.1 Checkpoints

The engine's `GraphCheckpointer` persists state snapshots. The observe server provides:

- `GET /api/v1/checkpoints/{runId}` — List checkpoints by sequence
- `POST /api/v1/replay` — Resume from checkpoint with optional state overrides
- `POST /api/v1/breakpoints` — Pause execution before specific nodes/tools

### 10.2 Replay

`CompiledGraph.invokeFromCheckpoint()` loads the checkpoint state, applies overrides, and resumes graph execution. This requires the engine module dependency.

---

## 11. Alerting System

### 11.1 Architecture

```
AlertScheduler (@Scheduled 60s)
  → Scan enabled alert_rules
  → Check cooldown via alertEventRepository.findMostRecentByRuleId()
  → AlertConditionEvaluator.evaluate()
  → If value > threshold: triggerEvent() + dispatch to linked channels
```

### 11.2 Alert Condition Evaluator

Defense-in-depth SQL evaluation:
1. Normalizes query (strips literals/comments)
2. Validates SELECT-only
3. Blocks semicolons (multi-statement prevention)
4. Scans for forbidden keywords (DROP, DELETE, etc.)
5. Validates table whitelist
6. `SET ROLE` with whitelist regex `^[a-zA-Z_][a-zA-Z0-9_]*$`
7. `setMaxRows(1)` (alert conditions expect scalar)
8. `setQueryTimeout(10s)`

### 11.3 Webhook Retry

`sendWebhookWithRetry()` implements exponential backoff:
- Attempt 1: immediate
- Attempt 2: 1s delay
- Attempt 3: 2s delay
- Attempt 4: 4s delay
- Max 3 retries, 10s HTTP timeout per attempt

---

## 12. Budget Enforcement

### 12.1 Transparent Decorator

`BudgetAwareAgentInvoker` wraps every `AgentInvoker`:

```java
public Result<String, AgentInvocationError> invoke(String input, AgentConfig config) {
    // Pre-invoke: check budget
    BudgetStatus status = budgetService.getStatus(config.agentId());
    if (status == BudgetStatus.EXCEEDED) {
        throw new BudgetExceededException("Budget exceeded for " + config.agentId());
    }

    // Execute
    Result<String, AgentInvocationError> result = delegate.invoke(input, config);

    // Post-invoke: record spend
    if (result instanceof Result.Ok<String, AgentInvocationError> ok) {
        BigDecimal cost = pricingTable.estimateCost(config.model(),
            input.length() / 4, ok.value().length() / 4);
        budgetService.updateSpending(config.agentId(), cost);
    }
    return result;
}
```

### 12.2 Atomic Updates

`BudgetService.updateSpending()` uses a single atomic UPDATE:

```sql
UPDATE budget_enforcements
SET current_value = current_value + ?,
    status = CASE
        WHEN current_value + ? >= limit_value THEN 'EXCEEDED'
        WHEN current_value + ? >= limit_value * 0.8 THEN 'WARNING'
        ELSE 'ACTIVE'
    END,
    updated_at = NOW()
WHERE agent_id = ?
```

This eliminates read-modify-write races without pessimistic locking.

### 12.3 TTL Cache

Budget status is cached with a 5-second TTL to prevent N+1 queries during high-volume ingestion:

```java
LoadingCache<String, BudgetStatus> cache = Caffeine.newBuilder()
    .expireAfterWrite(Duration.ofSeconds(5))
    .build(budgetService::getStatus);
```

---

## 13. Prompt A/B Testing

### 13.1 Statistical Testing

`PromptAbTestExecutor.execute()`:
1. Acquires distributed lock (prevents concurrent execution)
2. Runs `ParallelEvalRunner` for both prompt versions against the same dataset
3. Computes per-case score arrays
4. Applies Welch's t-test (unequal variances assumed)
5. Winner declared if `pValue < 0.05` AND higher average score

### 13.2 Welch's T-Test Implementation

`WelchTTest.test()` is a complete statistical implementation:
- Lanczos gamma approximation for the gamma function
- Continued-fraction incomplete beta for the CDF
- No external math library dependency

```java
WelchTTest.Result result = WelchTTest.test(scoresA, scoresB);
// result.pValue() — two-tailed p-value
// result.significant() — pValue < alpha
```

---

## 14. Trace Clustering

### 14.1 Three-Phase Pipeline

```
TraceClusteringEngine.run()
  → Phase 1: generateEmbeddings()
       Calls EmbeddingInvoker for each trace (prompt + completion text)
       Caps at 50K embeddings per run
  → Phase 2: clusterTraces()
       EmbeddingClusterer.cluster() — DBSCAN with cosine similarity
  → Phase 3: labelAndPersist()
       Generates cluster labels from sample runs
       Persists TraceCluster records
```

### 14.2 DBSCAN with Cosine Similarity

`EmbeddingClusterer` adapts DBSCAN for high-dimensional embedding spaces:

```java
List<Cluster> clusters = new EmbeddingClusterer()
    .cluster(vectors, minPoints, minSimilarity);
```

- `regionQuery()` uses cosine similarity instead of Euclidean distance
- `expandCluster()` performs breadth-first seed expansion
- Deterministic re-labeling to stable cluster names

### 14.3 Duplicate Input Removal

Before embedding generation, duplicate input texts are deduplicated to avoid wasting embedding budget on identical traces.

---

## 15. Multi-Turn Testing

### 15.1 Conversation History Injection

`MultiTurnTestService.executeRun()` builds a `messages` array from conversation history and invokes the agent per turn:

```java
List<Message> messages = new ArrayList<>();
for (Turn turn : scenario.getTurns()) {
    messages.add(new Message(turn.getRole(), turn.getMessage()));
    String output = agentInvoker.invoke(messages, config);
    messages.add(new Message("assistant", output));
    // Score: expectedKeywords in output
}
```

### 15.2 Scoring

- Per-turn: `score = matchedKeywords / expectedKeywords` (case-insensitive)
- Final: `finalScore = passedTurns / totalTurns`
- Async execution via virtual threads

---

## 16. Data Retention & Export

### 16.1 Retention Scheduler

`DataRetentionScheduler` runs daily at 2 AM (`@Scheduled(cron = "0 0 2 * * *")`):

```java
for (RetentionPolicy policy : policies) {
    if (policy.isEnabled()) {
        retentionRepository.deleteOlderThan(
            policy.getResourceType(),
            Instant.now().minus(policy.getRetentionDays(), ChronoUnit.DAYS)
        );
    }
}
```

### 16.2 Export Service

`ExportService.submitExportJob()`:
1. Creates `ExportJob` record with `PENDING` status
2. Runs async via virtual thread
3. Executes SQL query for the resource type
4. Writes to `exports/` directory in requested format
5. Updates status to `COMPLETED` or `FAILED`

Supported formats: JSON, CSV, Parquet (fallback to JSON until Parquet lib added)

---

## 17. Notifications

### 17.1 Multi-Channel Dispatch

`NotificationService.dispatch()` routes to implementations by `ChannelType`:

```java
public interface NotificationDispatcher {
    void dispatch(NotificationChannel channel, AlertEvent event);
}
```

| Dispatcher | Protocol |
|---|---|
| `SlackDispatcher` | Incoming webhook POST |
| `PagerDutyDispatcher` | Events API v2 |
| `EmailDispatcher` | JavaMail SMTP with STARTTLS |
| `WebhookDispatcher` | Generic HTTP POST |

### 17.2 Email Configuration

Requires `spring-boot-starter-mail` dependency. Configure SMTP in `application.yml`:

```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: alerts@company.com
    password: ${SMTP_PASSWORD}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
```

---

## 18. Custom Dashboards

### 18.1 SQL-Driven Widgets

Each widget defines a SQL query. `CustomDashboardService.executeWidget()` runs the query via `SqlQueryService` and returns results:

```java
public Map<String, Object> executeWidget(String widgetId) {
    DashboardWidget widget = widgetRepository.findById(widgetId);
    return sqlQueryService.executeQuery(widget.getQueryConfig().getSql());
}
```

### 18.2 Widget Types

| Type | Expected SQL Result |
|---|---|
| `LINE_CHART` | Two columns: timestamp (x), value (y) |
| `BAR_CHART` | Two columns: category (x), value (y) |
| `STAT_CARD` | Single row, single column: scalar value |
| `TABLE` | Any shape, rendered as table |
| `PIE_CHART` | Two columns: label, numeric value |

---

## 19. SQL Query Defense

The `SqlQueryService` implements seven layers of defense:

```
┌─────────────────────────────────────────┐
│ 1. SELECT-only enforcement              │
│ 2. Literal/comment stripping            │
│ 3. Table whitelist validation           │
│ 4. Semicolon blocking                   │
│ 5. Database-level SET ROLE (fail-closed)│
│ 6. setMaxRows(10_000)                   │
│ 7. setQueryTimeout(30s)                 │
└─────────────────────────────────────────┘
```

### 19.1 Normalization

```java
String normalized = sql
    .replaceAll("'[^']*'", "''")      // Strip string literals
    .replaceAll("/\\*.*?\\*/", "");   // Strip block comments
```

### 19.2 Role Name Whitelist

```java
if (!readOnlyRole.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
    throw new SecurityException("Invalid role name: " + readOnlyRole);
}
```

If `readOnlyRole` is empty, `SET ROLE` is skipped entirely.

---

## 20. Distributed Locking

### 20.1 JdbcDistributedLock

Table-based distributed locking with TTL:

```java
LockToken token = lockRegistry.tryLock("eval-run-123", Duration.ofSeconds(300));
if (token != null) {
    try {
        // Critical section
    } finally {
        lockRegistry.unlock(token);
    }
}
```

### 20.2 Lock Acquisition

1. `INSERT INTO distributed_locks (lock_name, owner_id, token_id, expires_at)`
2. On `DuplicateKeyException`, check if existing lock is expired (`expires_at < NOW()`)
3. If expired, steal with `UPDATE ... SET owner_id = ?, token_id = ?, expires_at = ?`
4. Poll interval: 500ms (configurable)

### 20.3 Lock Reaper

`DistributedLockReaper` runs every 60 seconds and deletes expired lock rows to prevent table bloat.

### 20.4 Token-Scoped Release

`unlock(LockToken token)` uses a conditional DELETE:

```sql
DELETE FROM distributed_locks
WHERE lock_name = ? AND owner_id = ? AND token_id = ?
```

This prevents accidental unlock of a lock acquired by another process.

---

## 21. Testing Strategy

### 21.1 Zero Mockito

All tests use hand-written `InMemory*Repository` fakes extending real repository interfaces:

```java
class InMemoryRunRepository implements RunRepository {
    private final Map<String, Run> runs = new ConcurrentHashMap<>();
    // ... implement all methods with in-memory storage
}
```

### 21.2 H2 In-Memory Database

Integration tests use H2 with `schema-h2.sql` covering all V3–V5 tables:

```java
@SpringBootTest
@TestPropertySource(properties = {
    "chorus.observe.database.url=jdbc:h2:mem:testdb",
    "chorus.observe.database.migrateOnStartup=false"
})
class EvalServiceIntegrationTest {
    // Tests run against real repositories backed by H2
}
```

### 21.3 Test JVM Args

```groovy
test {
    jvmArgs = [
        '--enable-preview',
        '--add-modules', 'jdk.incubator.vector'
    ]
    maxHeapSize = '2g'
    useJUnitPlatform()
}
```

### 21.4 Key Test Categories

| Category | Example |
|---|---|
| Unit | `WelchTTestTest`, `EmbeddingClustererTest` |
| Service | `EvalServiceTest` with fakes, `BudgetServiceTest` |
| Integration | `SqlQueryServiceIntegrationTest` with H2 |
| Security | `ApiKeyAuthFilterTest`, `RateLimitFilterTest` |
| End-to-end | `OtlpIngestionE2ETest` |

---

## 22. Configuration

### 22.1 Core Properties

```yaml
chorus:
  observe:
    enabled: true

    database:
      url: jdbc:postgresql://localhost:5432/chorus_observe
      username: chorus
      password: chorus
      maxPoolSize: 20
      migrateOnStartup: true
      readOnlyRole: chorus_readonly

    clickhouse:
      url: jdbc:clickhouse://localhost:8123/chorus_observe
      username: chorus
      password: chorus
      maxPoolSize: 20

    storage:
      span-store: postgresql          # postgresql | clickhouse | dual

    grpc:
      enabled: true
      port: 4317

    alert:
      evalIntervalMs: 60000

    lock:
      defaultTtlSeconds: 300
      pollIntervalMillis: 500

    jwt:
      secret: "your-32-char-min-secret-here"
      expiryMinutes: 60

    sampling:
      enabled: false
      rate: 1.0
      strategy: random

    eval:
      agentEndpoint: http://localhost:8080/invoke
      defaultParallelism: 8
      maxParallelism: 32

    security:
      apiKeyEnabled: false

    rateLimit:
      enabled: false
      maxRequestsPerMinute: 100

    server:
      maxFileSizeMb: 10
      maxRequestSizeMb: 10
```

### 22.2 Environment Variables

```bash
CHORUS_OBSERVE_DATABASE_URL=jdbc:postgresql://...
CHORUS_OBSERVE_DATABASE_USERNAME=chorus
CHORUS_OBSERVE_JWT_SECRET=super-secret-32-chars-min
CHORUS_OBSERVE_SAMPLING_ENABLED=true
CHORUS_OBSERVE_SAMPLING_RATE=0.1
```

---

## 23. Docker Deployment

### 23.1 Docker Compose (Full Stack)

```bash
cd chorus-observe-server
docker compose up -d
```

Services:
- PostgreSQL on `5432`
- ClickHouse on `8123`
- Chorus Observe on `8080`

### 23.2 Standalone Container

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

## 24. Troubleshooting

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
      port: 14317
```

### I don't see any traces

1. Check that the exporter endpoint points to the right host
2. Check `sample-rate`: `1.0` = 100%, `0.1` = 10%
3. Verify the OTLP payload has `gen_ai.system` or `chorus.run_id` attributes

### Budget not enforcing

1. Verify budget was created for the correct `agentId`
2. Check `BudgetAwareAgentInvoker` is in the decorator chain
3. Ensure `BudgetService.updateSpending()` is being called (check logs)

### Alerts not firing

1. Check `AlertScheduler` is running (logs every 60s)
2. Verify SQL condition returns a single scalar value
3. Check cooldown — rule may be in cooldown period
4. Verify notification channel configuration

---

*Need help? Open an issue at https://github.com/MuhibNayem/chorus-engine4j/issues*
