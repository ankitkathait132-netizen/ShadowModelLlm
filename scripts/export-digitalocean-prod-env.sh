#!/usr/bin/env bash
set -euo pipefail

API_BASE="${API_BASE:-https://api.digitalocean.com/v2}"
TOKEN="${DIGITALOCEAN_TOKEN:-${DIGITALOCEAN_ACCESS_TOKEN:-${DO_API_TOKEN:-}}}"

PROJECT_PREFIX="${PROJECT_PREFIX:-llm-shadow}"
MYSQL_NAME="${MYSQL_NAME:-${PROJECT_PREFIX}-mysql}"
MYSQL_DB_NAME="${MYSQL_DB_NAME:-llm_shadow}"
MYSQL_APP_USER="${MYSQL_APP_USER:-llm_shadow}"
KAFKA_NAME="${KAFKA_NAME:-${PROJECT_PREFIX}-kafka}"
OUTPUT_FILE="${OUTPUT_FILE:-.env.prod.local}"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

read_token_from_doctl() {
  if command -v doctl >/dev/null 2>&1; then
    doctl auth token 2>/dev/null || true
  fi
}

read_token_from_doctl_config() {
  local config_file="${DOCTL_CONFIG:-${XDG_CONFIG_HOME:-${HOME}/.config}/doctl/config.yaml}"

  if [[ ! -f "${config_file}" ]]; then
    return 0
  fi

  python3 - "${config_file}" <<'PY'
import re
import sys
from pathlib import Path

path = Path(sys.argv[1])
text = path.read_text()

current_match = re.search(r"(?m)^current-context:\s*(\S+)\s*$", text)
current_context = current_match.group(1) if current_match else "default"

context_pattern = re.compile(
    r"(?ms)^\s*-\s*name:\s*" + re.escape(current_context) + r"\s*$"
    r"(?P<body>.*?)(?=^\s*-\s*name:|\Z)"
)
context_match = context_pattern.search(text)
if context_match:
    token_match = re.search(r"(?m)^\s*access-token:\s*(\S+)\s*$", context_match.group("body"))
    if token_match:
        print(token_match.group(1))
        sys.exit(0)

token_match = re.search(r"(?m)^access-token:\s*(\S+)\s*$", text)
if token_match:
    print(token_match.group(1))
PY
}

if [[ -z "${TOKEN}" ]]; then
  TOKEN="$(read_token_from_doctl)"
fi

if [[ -z "${TOKEN}" ]]; then
  TOKEN="$(read_token_from_doctl_config)"
fi

if [[ -z "${TOKEN}" ]]; then
  echo "Missing DigitalOcean token." >&2
  echo "Run one of these first:" >&2
  echo "  export DIGITALOCEAN_TOKEN=\"dop_v1_...\"" >&2
  echo "  doctl auth init" >&2
  exit 1
fi

api() {
  local method="$1"
  local path="$2"
  local response_file
  local status

  response_file="$(mktemp)"
  status="$(curl -sS \
    -X "${method}" \
    -H "Authorization: Bearer ${TOKEN}" \
    -o "${response_file}" \
    -w "%{http_code}" \
    "${API_BASE}${path}")"

  if [[ "${status}" =~ ^2[0-9][0-9]$ ]]; then
    cat "${response_file}"
    rm -f "${response_file}"
    return 0
  fi

  echo "DigitalOcean API error: ${method} ${path} returned HTTP ${status}" >&2
  if ! jq -r '.message // .id // .error // .' "${response_file}" >&2 2>/dev/null; then
    cat "${response_file}" >&2
  fi
  rm -f "${response_file}"
  return 1
}

cluster_id_by_name() {
  local name="$1"
  api GET "/databases" | jq -r --arg name "${name}" '.databases[]? | select(.name == $name) | .id' | head -n 1
}

shell_quote() {
  local value="$1"
  printf "%q" "${value}"
}

require_command curl
require_command jq
require_command python3

MYSQL_ID="$(cluster_id_by_name "${MYSQL_NAME}")"
KAFKA_ID="$(cluster_id_by_name "${KAFKA_NAME}")"

if [[ -z "${MYSQL_ID}" || "${MYSQL_ID}" == "null" ]]; then
  echo "MySQL cluster not found: ${MYSQL_NAME}" >&2
  exit 1
fi

if [[ -z "${KAFKA_ID}" || "${KAFKA_ID}" == "null" ]]; then
  echo "Kafka cluster not found: ${KAFKA_NAME}" >&2
  exit 1
fi

MYSQL_JSON="$(api GET "/databases/${MYSQL_ID}")"
KAFKA_JSON="$(api GET "/databases/${KAFKA_ID}")"
MYSQL_USERS_JSON="$(api GET "/databases/${MYSQL_ID}/users")"

MYSQL_HOST="$(jq -r '.database.connection.host // empty' <<<"${MYSQL_JSON}")"
MYSQL_PORT="$(jq -r '.database.connection.port // empty' <<<"${MYSQL_JSON}")"
MYSQL_PASSWORD="$(jq -r --arg user "${MYSQL_APP_USER}" '.users[]? | select(.name == $user) | .password // empty' <<<"${MYSQL_USERS_JSON}")"

KAFKA_HOST="$(jq -r '.database.connection.host // empty' <<<"${KAFKA_JSON}")"
KAFKA_PORT="$(jq -r '.database.connection.port // empty' <<<"${KAFKA_JSON}")"
KAFKA_USERNAME="$(jq -r '.database.connection.user // .database.users[0].name // empty' <<<"${KAFKA_JSON}")"
KAFKA_PASSWORD="$(jq -r '.database.connection.password // .database.users[0].password // empty' <<<"${KAFKA_JSON}")"

if [[ -z "${MYSQL_HOST}" || -z "${MYSQL_PORT}" || -z "${MYSQL_PASSWORD}" ]]; then
  echo "Could not resolve complete MySQL connection details." >&2
  echo "Make sure the token has database:view_credentials scope." >&2
  exit 1
fi

if [[ -z "${KAFKA_HOST}" || -z "${KAFKA_PORT}" || -z "${KAFKA_USERNAME}" || -z "${KAFKA_PASSWORD}" ]]; then
  echo "Could not resolve complete Kafka connection details." >&2
  echo "Make sure the token has database:view_credentials scope." >&2
  exit 1
fi

MYSQL_JDBC_URL="jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DB_NAME}?sslMode=REQUIRED&serverTimezone=UTC"
KAFKA_BOOTSTRAP_SERVERS="${KAFKA_HOST}:${KAFKA_PORT}"

{
  echo "# Generated from DigitalOcean managed database connection details."
  echo "# Do not commit this file."
  echo "export DO_MYSQL_JDBC_URL=$(shell_quote "${MYSQL_JDBC_URL}")"
  echo "export DO_MYSQL_USERNAME=$(shell_quote "${MYSQL_APP_USER}")"
  echo "export DO_MYSQL_PASSWORD=$(shell_quote "${MYSQL_PASSWORD}")"
  echo "export DO_KAFKA_BOOTSTRAP_SERVERS=$(shell_quote "${KAFKA_BOOTSTRAP_SERVERS}")"
  echo "export DO_KAFKA_USERNAME=$(shell_quote "${KAFKA_USERNAME}")"
  echo "export DO_KAFKA_PASSWORD=$(shell_quote "${KAFKA_PASSWORD}")"
} > "${OUTPUT_FILE}"

chmod 600 "${OUTPUT_FILE}"

echo "Wrote production environment exports to ${OUTPUT_FILE}"
echo "MySQL cluster: ${MYSQL_NAME} (${MYSQL_ID})"
echo "Kafka cluster: ${KAFKA_NAME} (${KAFKA_ID})"
echo "Load it with: source ${OUTPUT_FILE}"
