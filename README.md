# ShadowModeLLM

Spring Boot proxy that serves customer traffic synchronously from a primary LLM endpoint,
while shadowing requests to a candidate LLM in the background via Kafka. See
[`LLM_SHADOW_PROXY_PLAN.md`](./LLM_SHADOW_PROXY_PLAN.md) for the full design.

## Modules

This is a Maven multi-module reactor project:

| Module          | Type       | Port | Description                                                                 |
|-----------------|------------|------|------------------------------------------------------------------------------|
| `common`        | library    | -    | Shared DTOs, config (`ShadowProxyProperties`), JSON extraction, response comparison, structured logging, Kafka producer config, backoff calculation, and the shared `/health` endpoint. No main class. |
| `proxy-api`     | executable | `8080` | Customer-facing REST API. Depends on `common`. Owns the `DemoApplication` entry point, the primary LLM client, and the Kafka shadow-task producer. |
| `shadow-worker` | executable | `8081` | Background Kafka consumer. Depends on `common`. Owns the `ShadowWorkerApplication` entry point, the candidate LLM client, response comparison/persistence, and retry/DLQ publishing. |

The root `pom.xml` is a `packaging=pom` aggregator: it has no source code or main
class of its own, so it can't be "run" directly. Each executable module (`proxy-api`,
`shadow-worker`) has its own `@SpringBootApplication` entry point and is started
independently — see [Run everything locally](#3-run-everything-locally) below for a
script that starts both together with one command.

## Local development

### 1. Start MySQL and Kafka

```bash
docker compose up -d
```

This starts:

| Service | Container          | Host port |
|---------|--------------------|-----------|
| MySQL   | `llm-shadow-mysql` | `3306`    |
| Kafka   | `llm-shadow-kafka` | `9092`    |

MySQL credentials (local):

- Database: `llm_shadow`
- User: `llm_shadow`
- Password: `llm_shadow`

Kafka topics (configured in `proxy-api/src/main/resources/application.yml`):

- `llm-shadow-requests`
- `llm-shadow-requests-retry`
- `llm-shadow-requests-dlq`

### 2. Build the reactor

```bash
./mvnw clean install -DskipTests
```

Or with the bundled JDK:

```bash
./run.sh clean install -DskipTests
```

### 3. Run everything locally

The easiest way to start both `proxy-api` and `shadow-worker` together is:

```bash
./start-local.sh
```

This launches both modules via the Maven reactor in the background, streams each
to its own log file (`logs/proxy-api.log`, `logs/shadow-worker.log`), and stops
both together on `Ctrl+C`.

### 3b. Run modules individually

You can also run each module in its own terminal, which is useful when you only
need one of them or want interleaved console output:

```bash
./mvnw -pl proxy-api spring-boot:run -Dspring-boot.run.profiles=local
./mvnw -pl shadow-worker spring-boot:run -Dspring-boot.run.profiles=local
```

Or with the bundled JDK:

```bash
./run.sh -pl proxy-api spring-boot:run -Dspring-boot.run.profiles=local
./run.sh -pl shadow-worker spring-boot:run -Dspring-boot.run.profiles=local
```

`proxy-api` starts on `http://localhost:8080`, `shadow-worker` on `http://localhost:8081`.

### Stop containers

```bash
docker compose down
```

## Endpoints

| Method | Path         | Module                      | Description                                                     |
|--------|--------------|------------------------------|-------------------------------------------------------------------|
| GET    | `/health`    | `common` (proxy-api, shadow-worker) | Health status, including MySQL and Kafka connectivity checks       |
| POST   | `/v1/proxy`  | `proxy-api`                  | Calls the primary LLM synchronously and shadows the request via Kafka |

### GET /health

Shared endpoint (defined in `common`, exposed by every module that includes it).
Available on both `proxy-api` (`http://localhost:8080/health`) and `shadow-worker`
(`http://localhost:8081/health`). Returns the current health status of the running
module, including connectivity checks to MySQL and Kafka.

**Request body:** None

**Response:** `200 OK`

```json
{
  "status": "OK",
  "timestamp": "2026-07-04T08:49:00.123456Z",
  "mysqlTimestamp": "2026-07-04T08:49:00.098765Z",
  "kafkaTimestamp": "2026-07-04T08:49:00.120000Z"
}
```

| Field             | Type     | Description                                                       |
|-------------------|----------|--------------------------------------------------------------------|
| `status`          | `string` | `OK` when both MySQL and Kafka are reachable, otherwise `DEGRADED` |
| `timestamp`       | `string` | Current server time (ISO-8601 UTC)                                |
| `mysqlTimestamp`  | `string` | Current time from MySQL (`UTC_TIMESTAMP`); `null` if unreachable   |
| `kafkaTimestamp`  | `string` | Time when Kafka cluster connectivity was verified; `null` if unreachable |

### POST /v1/proxy

Owned by `proxy-api`. Calls the configured primary LLM synchronously, publishes a
shadow task to Kafka in the background (unless `app.shadow.enabled=false`), and
returns the primary response immediately.

**Request body:**

```json
{
  "correlationId": "req-123",
  "payload": { "prompt": "hello" },
  "primaryModel": "gpt-primary",
  "candidateModel": "gpt-candidate"
}
```

| Field            | Type     | Required | Description                                              |
|------------------|----------|----------|------------------------------------------------------------|
| `correlationId`  | `string` | No       | Client-supplied correlation ID; generated if omitted        |
| `payload`        | `object` | Yes      | Request body forwarded as-is to the primary LLM              |
| `primaryModel`   | `string` | No       | Overrides `app.primary-llm.default-model`                    |
| `candidateModel` | `string` | No       | Overrides `app.candidate-llm.default-model`                  |

**Response:** `200 OK`

```json
{
  "correlationId": "req-123",
  "primaryResponse": { "text": "hi there" },
  "primaryStatusCode": 200,
  "primaryLatencyMs": 42,
  "shadowEnqueued": true
}
```

Note: the primary/candidate LLM base URLs in `application.yml` currently point to
local placeholder addresses (`http://localhost:9090/primary`,
`http://localhost:9091/candidate`). Point them at real endpoints, or stand up mock
servers, before exercising this endpoint end-to-end.

### shadow-worker (background, no HTTP API)

`shadow-worker` has no customer-facing endpoints beyond `/health` — it's a Kafka
consumer that runs continuously in the background:

1. Listens on `llm-shadow-requests` (new tasks) and `llm-shadow-requests-retry` (retried tasks).
2. For each `ShadowTaskDto`, calls the candidate LLM (`CandidateLlmClient`).
3. Extracts JSON from both the primary and candidate raw outputs (`JsonExtractor`) and
   compares them (`ResponseComparator`), falling back to normalized text comparison if
   JSON extraction fails on either side.
4. On mismatch, persists a row to the `llm_shadow_mismatch` table (idempotent on
   `shadowTaskId`, so redelivered messages are skipped rather than duplicated).
5. On failure (candidate call error or DB write error), retries with exponential
   backoff + jitter up to `app.retry.max-attempts`, then publishes to
   `llm-shadow-requests-dlq` once attempts are exhausted.

All steps emit structured JSON log events (`shadow.task.received`, `shadow.candidate.failed`,
`shadow.match`, `shadow.mismatch.persisted`, `shadow.retry.scheduled`, `shadow.dlq.published`, etc.)
via the shared `StructuredLogger`.
