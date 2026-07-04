# LLM Shadow Proxy Plan

## Objective

Build a Spring Boot proxy that serves customer traffic synchronously from a primary private LLM endpoint, while safely firing asynchronous shadow requests to a candidate LLM and logging mismatched outputs.

The proxy should return the primary model response immediately. Candidate model execution, comparison, retries, and mismatch persistence should happen in the background.

## Recommended Architecture

Kafka and MySQL are enough for this project.

```text
Client
  -> Spring Boot Proxy API
      -> Primary LLM synchronously
      -> Kafka shadow topic
  -> returns primary response immediately

Kafka shadow topic
  -> Spring Boot Shadow Worker
      -> Candidate LLM
      -> compare outputs
      -> MySQL mismatch table

Kafka retry topic
  -> Shadow Worker retry path

Kafka DLQ
  -> failed tasks for inspection
```

## Application Modules

- `proxy-api`: REST controller, primary LLM client, Kafka producer.
- `shadow-worker`: Kafka consumer, candidate LLM client, comparator, retry handling, DLQ publisher, DB writer.
- `common`: shared DTOs, config objects, JSON extraction, comparison utilities, structured logger.

These can live in one Spring Boot deployable for the MVP. Later, `proxy-api` and `shadow-worker` can be split into separate deployables if traffic or scaling requires it.

## Request Flow

1. Client sends `POST` request to the proxy API.
2. Proxy generates or reads a `correlationId`.
3. Proxy calls the primary LLM synchronously.
4. Proxy captures the primary response, status code, latency, model name, and request hash.
5. Proxy publishes a shadow task to Kafka.
6. Proxy returns the primary response to the client immediately.
7. Shadow worker consumes the Kafka task.
8. Worker calls the candidate LLM.
9. Worker extracts JSON from primary and candidate outputs.
10. Worker compares normalized outputs.
11. Worker writes a mismatch row to MySQL only when outputs differ.

## Kafka Topics

Use three topics initially:

- `llm-shadow-requests`: main background task topic.
- `llm-shadow-requests-retry`: retry topic for transient background failures.
- `llm-shadow-requests-dlq`: dead-letter topic for exhausted or non-retryable failures.

The shadow task should include:

- `shadowTaskId`
- `correlationId`
- original request payload or redacted payload
- request hash
- primary response payload
- primary status code
- primary latency
- primary model name
- candidate model name
- attempt count
- max attempts
- first attempt timestamp
- last error type and message

## Retry And DLQ Behavior

Candidate calls, JSON extraction, comparison, and DB persistence happen outside the customer request path.

If the worker fails because of a retryable issue, such as a timeout, temporary candidate error, Kafka issue, or transient DB error, it should publish the task to the retry topic with incremented attempt metadata.

Retry behavior should be config-driven:

- max attempts
- initial backoff
- backoff multiplier
- max backoff
- optional jitter

If attempts are exhausted, or the task is malformed, publish it to the DLQ with a structured failure reason.

## MySQL Storage

Use MySQL as the canonical mismatch store.

Suggested table: `llm_shadow_mismatch`

- `id`
- `shadow_task_id`
- `correlation_id`
- `request_hash`
- `request_payload_redacted`
- `primary_model`
- `candidate_model`
- `primary_status`
- `candidate_status`
- `primary_latency_ms`
- `candidate_latency_ms`
- `primary_raw_output`
- `candidate_raw_output`
- `primary_json_output`
- `candidate_json_output`
- `diff_summary`
- `error_type`
- `created_at`

Add a unique constraint on `shadow_task_id` or another idempotency key to avoid duplicate mismatch rows from retries.

## JSON Extraction And Comparison

Avoid regex-only parsing.

Preferred extraction order:

1. Try parsing the full response as JSON.
2. If that fails, scan for the first balanced JSON object or array in the text.
3. Parse the extracted block with Jackson.
4. Store raw output plus parsed JSON when parsing succeeds.
5. If parsing fails, record a structured extraction error and compare normalized raw text.

Comparison should start with normalized JSON equality. Later, it can support selected-field comparison if LLM responses include nondeterministic fields.

## Central Structured Logging

Use a shared logger used by both `proxy-api` and `shadow-worker`.

The logger should emit the same JSON format in both modules, with fields that make sense across multi-host DigitalOcean deployments.

Required log fields:

- `event`
- `serviceName`
- `component`
- `operation`
- `machineId`
- `correlationId`
- `shadowTaskId`
- `attempt`
- `status`
- `durationMs`
- `primaryModel`
- `candidateModel`
- `errorType`
- `message`

Resolve `machineId` in this order:

1. `MACHINE_ID` environment variable from deployment config.
2. DigitalOcean droplet hostname.
3. OS hostname.
4. Generated boot ID as a last resort.

Example `proxy-api` log:

```json
{
  "event": "shadow.enqueued",
  "serviceName": "llm-shadow-proxy",
  "component": "proxy-api",
  "operation": "kafka_publish",
  "machineId": "do-nyc3-shadow-01",
  "correlationId": "req-123",
  "shadowTaskId": "7dc9c0e0-5f5d-49a7-8f46-d4823aebd55e",
  "status": "success",
  "durationMs": 14
}
```

Example `shadow-worker` log:

```json
{
  "event": "shadow.retry.scheduled",
  "serviceName": "llm-shadow-proxy",
  "component": "shadow-worker",
  "operation": "candidate_call",
  "machineId": "do-nyc3-shadow-02",
  "correlationId": "req-123",
  "shadowTaskId": "7dc9c0e0-5f5d-49a7-8f46-d4823aebd55e",
  "attempt": 2,
  "status": "retry",
  "errorType": "candidate_timeout",
  "message": "Candidate endpoint timed out; retry scheduled"
}
```

Log at these points:

- request received
- primary call completed
- Kafka enqueue success or failure
- worker task received
- candidate call success or failure
- JSON extraction failure
- mismatch detected and persisted
- retry scheduled
- DLQ published

Do not log secrets, auth headers, or full raw payloads by default. Use hashes, redacted snippets, and database records for controlled debugging.

## Configuration

All runtime values should come from Spring config or environment variables:

- primary endpoint URL and auth token
- candidate endpoint URL and auth token
- Kafka bootstrap servers
- shadow topic name
- retry topic name
- DLQ topic name
- max retry attempts
- retry backoff settings
- MySQL connection details
- machine ID
- shadow enabled flag
- comparison mode
- max persisted payload size
- redaction fields
- HTTP connect and read timeouts

## Deployment Shape

For local development:

- Spring Boot app
- Docker Compose Kafka
- Docker Compose MySQL
- mock primary LLM endpoint
- mock candidate LLM endpoint

For DigitalOcean:

- one or more proxy API instances
- one or more shadow worker instances
- managed MySQL
- Kafka cluster or managed Kafka-compatible service
- `MACHINE_ID` configured per host or instance

## Key Risks

- Kafka publish failure means a primary response may not be shadowed unless an outbox pattern is added.
- Retry loops can amplify candidate outages, so attempts must be bounded with backoff and jitter.
- Full payload logging can create privacy risk; logs should be redacted and database payloads should be controlled.
- Exact LLM output comparison may create noisy mismatches; normalized JSON comparison is the better default.
- Worker persistence must be idempotent because Kafka messages can be retried or redelivered.
