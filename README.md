# ShadowModeLLM Demo

Spring Boot demo application with a health check endpoint.

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

Kafka topics (configured in `application.yml`):

- `llm-shadow-requests`
- `llm-shadow-requests-retry`
- `llm-shadow-requests-dlq`

### 2. Run the app with the `local` profile

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Or:

```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

Using the bundled JDK and Maven wrapper:

```bash
./run.sh spring-boot:run -Dspring-boot.run.profiles=local
```

The server starts on `http://localhost:8080`.

### Stop containers

```bash
docker compose down
```

## Endpoints

### GET /health

Returns the current health status of the application, including connectivity checks to MySQL and Kafka.

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
|-------------------|----------|-------------------------------------------------------------------|
| `status`          | `string` | `OK` when both MySQL and Kafka are reachable, otherwise `DEGRADED` |
| `timestamp`       | `string` | Current server time (ISO-8601 UTC)                                |
| `mysqlTimestamp`  | `string` | Current time from MySQL (`UTC_TIMESTAMP`); `null` if unreachable   |
| `kafkaTimestamp`  | `string` | Time when Kafka cluster connectivity was verified; `null` if unreachable |
