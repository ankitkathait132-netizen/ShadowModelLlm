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

The caller supplies **plain text only** — no raw JSON payload, no model name. The
proxy builds the actual OpenAI-compatible chat completion body server-side:

```json
{"model": "<configured-model>", "messages": [{"role": "user", "content": "<text>"}]}
```

This keeps two things entirely out of the caller's control:

- **Which model handles the request** — always `app.primary-llm.default-model` /
  `app.candidate-llm.default-model` from server config, never client input. Otherwise
  a caller could route around approved models or cost controls.
- **The shape of the upstream request body** — a caller can't inject extra fields,
  a system prompt, or arbitrary JSON into what's sent to the LLM provider.

wThis is enforced, not just undocumented: `ProxyRequestDto` only declares `correlationId`
and `text`, and `proxy-api` sets `spring.jackson.deserialization.fail-on-unknown-properties=true`,
so a request body containing e.g. `"primaryModel"`, `"candidateModel"`, or `"payload"` is
rejected with `400 Bad Request` instead of the field being silently ignored.

**Request body:**

```json
{
  "correlationId": "req-123",
  "text": "What is the capital of France?"
}
```

| Field            | Type     | Required | Description                                              |
|------------------|----------|----------|------------------------------------------------------------|
| `correlationId`  | `string` | No       | Client-supplied correlation ID; generated if omitted        |
| `text`           | `string` | Yes      | Plain prompt text; rejected with `400` if blank/missing       |

The same `text` is sent to both the primary LLM (synchronously) and the candidate LLM
(asynchronously, via the shadow task) — each wrapped with its own server-configured
model — so the two responses are directly comparable.

**Response:** `200 OK`

`primaryResponse` is the raw, unmodified response body from the primary LLM (an
OpenAI-compatible chat completion response):

```json
{
  "correlationId": "req-123",
  "primaryResponse": {
    "choices": [
      { "message": { "role": "assistant", "content": "Paris." } }
    ]
  },
  "primaryStatusCode": 200,
  "primaryLatencyMs": 42,
  "shadowEnqueued": true
}
```

Note: the primary/candidate LLM base URLs in `application.yml` currently point to
local placeholder addresses (`http://localhost:9090/primary`,
`http://localhost:9091/candidate`), which won't understand the OpenAI-compatible
request shape. Point `app.primary-llm.base-url` / `app.candidate-llm.base-url` at
real OpenAI-compatible endpoints (see
[`DIGITALOCEAN_TEST_LLM_MODELS.md`](./DIGITALOCEAN_TEST_LLM_MODELS.md) for a working
DigitalOcean Serverless Inference setup), or stand up mock servers that accept
`{"model", "messages"}` bodies, before exercising this endpoint end-to-end.

### shadow-worker (background, no HTTP API)

`shadow-worker` has no customer-facing endpoints beyond `/health` — it's a Kafka
consumer that runs continuously in the background:

1. Listens on `llm-shadow-requests` (new tasks) and `llm-shadow-requests-retry` (retried tasks).
2. For each `ShadowTaskDto`, calls the candidate LLM (`CandidateLlmClient`).
3. Extracts JSON from both the primary and candidate raw outputs (`JsonExtractor`) and
   compares them (`ResponseComparator`), falling back to normalized text comparison if
   JSON extraction fails on either side.
4. Persists a row to the `llm_shadow_mismatch` table for **every** comparison result —
   both matches and mismatches — with `match_status` set to `MATCH` or `MISMATCH`
   (idempotent on `shadowTaskId`, so redelivered messages are skipped rather than
   duplicated). A `MATCH` row records `diff_summary = "100% match"`; a `MISMATCH` row
   records the actual diff. Every row includes `correlation_id` and the request text
   (`request_payload_redacted`, truncated to `app.redaction.max-persisted-payload-size`).
5. On failure (candidate call error or DB write error), retries with exponential
   backoff + jitter up to `app.retry.max-attempts`, then publishes to
   `llm-shadow-requests-dlq` once attempts are exhausted.

All steps emit structured JSON log events (`shadow.task.received`, `shadow.candidate.failed`,
`shadow.match`, `shadow.mismatch`, `shadow.comparison.persisted`, `shadow.retry.scheduled`,
`shadow.dlq.published`, etc.) via the shared `StructuredLogger`.

## Testing

Every module has a JUnit 5 test suite (using the `spring-boot-starter-test` BOM:
JUnit 5, Mockito, AssertJ). Run everything from the repo root:

```bash
./mvnw test
```

Or with the bundled JDK:

```bash
./run.sh test
```

Run a single module's tests:

```bash
./run.sh -pl common test
./run.sh -pl proxy-api -am test
./run.sh -pl shadow-worker -am test
```

(`-am` also builds/tests the `common` module the other two depend on.)

| Module          | What's covered                                                                                                                                                                                        |
|-----------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `common`        | `RequestHasher` (SHA-256), `BackoffCalculator` (exponential backoff + cap + jitter bounds), `JsonExtractor` (full/embedded/malformed/missing JSON), `ResponseComparator` (JSON vs. text fallback matching), `ChatCompletionRequest`, `LogEvent`, `StructuredLogger` (JSON log shape + level via a Logback `ListAppender`), `MachineIdResolver`, `ShadowProxyProperties` defaults, and `HealthService` (MySQL/Kafka reachable and unreachable paths, with `AdminClient` statically mocked via Mockito's inline mock maker so no real broker is needed). |
| `proxy-api`     | `ProxyRequestDto` strict deserialization (rejects `primaryModel`/`candidateModel`/`payload`), `ProxyService` (validation, correlation ID generation, model selection from properties only, shadow task construction, truncation, shadow-disabled path), `PrimaryLlmClient` (real HTTP round-trips against a JDK `HttpServer` — success, auth header, 4xx, connection refused), `ShadowTaskProducer` (Kafka publish + success/failure logging), `ProxyController`. |
| `shadow-worker` | `CandidateLlmClient` (same HTTP-server-backed approach as `PrimaryLlmClient`), `RetryDlqPublisher` (retry vs. DLQ routing, attempt increment), `ShadowWorkerService` (duplicate skip, candidate failure → retry/DLQ, match/mismatch persistence incl. the `"100% match"` diff summary, JSON-extraction-degraded error type, duplicate-key vs. generic save failures), `ShadowTaskConsumer`, and `LlmShadowMismatchRepository` against a real embedded H2 database (`src/test/resources/application.yml`, `MODE=MySQL` so the `LONGTEXT` column definitions apply) verifying persistence, `existsByShadowTaskId`, and the unique constraint on `shadow_task_id`. |

All three modules pass with `mvn clean test` at the repo root (95 tests total as of
this writing: 50 in `common`, 25 in `proxy-api`, 20 in `shadow-worker`).

## DigitalOcean Deployment Test Commands

Use the deployed proxy API on the droplet:

```bash
curl -sS http://167.71.226.194:8080/health | jq
```

Send an end-to-end shadow request:

```bash
curl -sS -X POST http://167.71.226.194:8080/v1/proxy \
  -H 'Content-Type: application/json' \
  -d '{
    "correlationId": "do-prod-test-001",
    "payload": {
      "messages": [
        {
          "role": "user",
          "content": "Return only JSON: {\"answer\":\"hello\",\"score\":1}"
        }
      ],
      "temperature": 0
    }
  }' | jq
```

Check service logs on the droplet:

```bash
ssh -i ~/.ssh/id_ed25519_shadowmodellm root@167.71.226.194
journalctl -u shadow-proxy-api -n 120 --no-pager --full
journalctl -u shadow-worker -n 120 --no-pager --full
```

Check service status:

```bash
systemctl status shadow-proxy-api --no-pager
systemctl status shadow-worker --no-pager
```

Verify the deployed env file has all required keys without printing secrets:

```bash
python3 - <<'PY'
from pathlib import Path

for key in [
    "SPRING_PROFILES_ACTIVE",
    "DO_MYSQL_JDBC_URL",
    "DO_MYSQL_USERNAME",
    "DO_MYSQL_PASSWORD",
    "DO_KAFKA_BOOTSTRAP_SERVERS",
    "DO_KAFKA_USERNAME",
    "DO_KAFKA_PASSWORD",
    "DO_KAFKA_TRUSTSTORE_LOCATION",
    "DO_KAFKA_TRUSTSTORE_PASSWORD",
    "DO_MODEL_ACCESS_KEY",
    "MACHINE_ID",
]:
    value = ""
    for line in Path("/etc/shadowmode.env").read_text().splitlines():
        if line.startswith(key + "="):
            value = line.split("=", 1)[1].strip()
    print(f"{key}: {'SET' if value else 'EMPTY'}")
PY
```

Inspect persisted comparison rows in managed MySQL from your Mac after sourcing prod env values:

```bash
source .env.prod.local

MYSQL_HOST="$(python3 - <<'PY'
import os, re
m = re.search(r'jdbc:mysql://([^:/?]+)', os.environ["DO_MYSQL_JDBC_URL"])
print(m.group(1))
PY
)"

MYSQL_PORT="$(python3 - <<'PY'
import os, re
m = re.search(r'jdbc:mysql://[^:/?]+:(\d+)', os.environ["DO_MYSQL_JDBC_URL"])
print(m.group(1) if m else "25060")
PY
)"

mysql --ssl-mode=REQUIRED \
  -h "$MYSQL_HOST" \
  -P "$MYSQL_PORT" \
  -u "$DO_MYSQL_USERNAME" \
  -p"$DO_MYSQL_PASSWORD" \
  llm_shadow \
  -e "SELECT id, correlation_id, match_status, primary_model, candidate_model, diff_summary, created_at FROM llm_shadow_mismatch ORDER BY created_at DESC LIMIT 20;"
```
