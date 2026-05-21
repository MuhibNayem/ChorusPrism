# ClickHouse Schema for Chorus Observe

## Setup

1. Start ClickHouse:
```bash
docker run -d --name clickhouse \
  -p 8123:8123 -p 9000:9000 \
  clickhouse/clickhouse-server:latest
```

2. Create the schema:
```bash
cat chorus-observe-server/src/main/resources/db/clickhouse/V1__init_schema.sql | docker exec -i clickhouse clickhouse-client
```

3. Configure Chorus Observe to use ClickHouse for spans:
```yaml
chorus:
  observe:
    storage:
      span-store: clickhouse
    database:
      url: jdbc:clickhouse://localhost:8123/chorus_observe
      username: default
      password: ""
```

## Design Decisions

- **MergeTree engine** — Default ClickHouse engine for time-series data
- **ORDER BY (run_id, start_time)** — Optimizes trace waterfall queries
- **TTL 90 days** — Automatic data expiry to control storage costs
- **index_granularity = 8192** — Default, good for span-size rows
- **Nullable columns** — Matches the Java `@Nullable` model
- **No ON CONFLICT** — ClickHouse is append-only; duplicates are handled by ReplacingMergeTree if needed in future
