#!/usr/bin/env bash
set -euo pipefail

API_BASE="${API_BASE:-https://api.digitalocean.com/v2}"
TOKEN="${DIGITALOCEAN_TOKEN:-${DIGITALOCEAN_ACCESS_TOKEN:-${DO_API_TOKEN:-}}}"

PROJECT_PREFIX="${PROJECT_PREFIX:-llm-shadow}"
REGION="${REGION:-blr1}"
FALLBACK_REGION="${FALLBACK_REGION:-sgp1}"

MYSQL_NAME="${MYSQL_NAME:-${PROJECT_PREFIX}-mysql}"
MYSQL_VERSION="${MYSQL_VERSION:-}"
MYSQL_SIZE="${MYSQL_SIZE:-db-s-1vcpu-1gb}"
MYSQL_NODES="${MYSQL_NODES:-1}"
MYSQL_DB_NAME="${MYSQL_DB_NAME:-llm_shadow}"
MYSQL_APP_USER="${MYSQL_APP_USER:-llm_shadow}"

KAFKA_NAME="${KAFKA_NAME:-${PROJECT_PREFIX}-kafka}"
KAFKA_VERSION="${KAFKA_VERSION:-}"
KAFKA_SIZE="${KAFKA_SIZE:-gd-2vcpu-8gb}"
KAFKA_NODES="${KAFKA_NODES:-6}"
KAFKA_TOPIC_PARTITIONS="${KAFKA_TOPIC_PARTITIONS:-6}"
KAFKA_TOPIC_REPLICATION_FACTOR="${KAFKA_TOPIC_REPLICATION_FACTOR:-3}"

TOPICS=(
  "llm-shadow-requests"
  "llm-shadow-requests-retry"
  "llm-shadow-requests-dlq"
)

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
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
  local body="${3:-}"
  local response_file
  local status

  response_file="$(mktemp)"

  if [[ -n "${body}" ]]; then
    status="$(curl -sS \
      -X "${method}" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer ${TOKEN}" \
      -d "${body}" \
      -o "${response_file}" \
      -w "%{http_code}" \
      "${API_BASE}${path}")"
  else
    status="$(curl -sS \
      -X "${method}" \
      -H "Authorization: Bearer ${TOKEN}" \
      -o "${response_file}" \
      -w "%{http_code}" \
      "${API_BASE}${path}")"
  fi

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

cluster_status() {
  local id="$1"
  api GET "/databases/${id}" | jq -r '.database.status'
}

wait_for_cluster() {
  local id="$1"
  local name="$2"
  local timeout_seconds="${3:-3600}"
  local started
  started="$(date +%s)"

  while true; do
    local status
    status="$(cluster_status "${id}")"
    echo "${name} status: ${status}"

    if [[ "${status}" == "online" ]]; then
      return 0
    fi

    if (( "$(date +%s)" - started > timeout_seconds )); then
      echo "Timed out waiting for ${name} (${id}) to become online." >&2
      return 1
    fi

    sleep 30
  done
}

region_available_for_engine() {
  local engine="$1"
  local region="$2"
  api GET "/databases/options" | jq -e --arg engine "${engine}" --arg region "${region}" '
    any(.options[$engine].regions[]?; . == $region)
  ' >/dev/null
}

available_regions_for_engine() {
  local engine="$1"
  api GET "/databases/options" | jq -r --arg engine "${engine}" '
    [.options[$engine].regions[]?] | join(", ")
  '
}

layout_available_for_engine() {
  local engine="$1"
  local nodes="$2"
  local size="$3"

  api GET "/databases/options" | jq -e \
    --arg engine "${engine}" \
    --argjson nodes "${nodes}" \
    --arg size "${size}" '
      any(.options[$engine].layouts[]?; .num_nodes == $nodes and any(.sizes[]?; . == $size))
    ' >/dev/null
}

available_sizes_for_engine_nodes() {
  local engine="$1"
  local nodes="$2"

  api GET "/databases/options" | jq -r \
    --arg engine "${engine}" \
    --argjson nodes "${nodes}" '
      [
        .options[$engine].layouts[]?
        | select(.num_nodes == $nodes)
        | .sizes[]?
      ]
      | unique
      | join(", ")
    '
}

validate_layout() {
  local engine="$1"
  local nodes="$2"
  local size="$3"

  if layout_available_for_engine "${engine}" "${nodes}" "${size}"; then
    return 0
  fi

  echo "Requested ${engine} layout is not available: ${nodes} node(s), size ${size}." >&2
  echo "Available ${engine} sizes for ${nodes} node(s): $(available_sizes_for_engine_nodes "${engine}" "${nodes}")" >&2
  exit 1
}

region_available_for_engines() {
  local region="$1"
  shift

  local engine
  for engine in "$@"; do
    if ! region_available_for_engine "${engine}" "${region}"; then
      return 1
    fi
  done

  return 0
}

select_shared_region() {
  local engines=("mysql" "kafka")

  if region_available_for_engines "${REGION}" "${engines[@]}"; then
    echo "${REGION}"
    return 0
  fi

  if region_available_for_engines "${FALLBACK_REGION}" "${engines[@]}"; then
    echo "${FALLBACK_REGION}"
    return 0
  fi

  echo "Neither ${REGION} nor ${FALLBACK_REGION} is available for both MySQL and Kafka." >&2
  echo "Available mysql regions: $(available_regions_for_engine mysql)" >&2
  echo "Available kafka regions: $(available_regions_for_engine kafka)" >&2
  exit 1
}

create_cluster_if_missing() {
  local name="$1"
  local engine="$2"
  local version="$3"
  local region="$4"
  local size="$5"
  local nodes="$6"
  local existing_id="${7:-}"

  if [[ -n "${existing_id}" && "${existing_id}" != "null" ]]; then
    echo "${name} already exists: ${existing_id}" >&2
    echo "${existing_id}"
    return 0
  fi

  echo "Creating ${engine} cluster ${name} in ${region}..." >&2
  api POST "/databases" "$(jq -n \
    --arg name "${name}" \
    --arg engine "${engine}" \
    --arg version "${version}" \
    --arg region "${region}" \
    --arg size "${size}" \
    --argjson nodes "${nodes}" \
    '{
      name: $name,
      engine: $engine,
      region: $region,
      size: $size,
      num_nodes: $nodes
    }
    | if $version == "" then . else . + {version: $version} end')" | jq -r '.database.id'
}

create_mysql_database_if_missing() {
  local cluster_id="$1"
  local db_name="$2"

  if api GET "/databases/${cluster_id}/dbs" | jq -e --arg name "${db_name}" '.dbs[]? | select(.name == $name)' >/dev/null; then
    echo "MySQL database ${db_name} already exists."
    return 0
  fi

  echo "Creating MySQL database ${db_name}..."
  api POST "/databases/${cluster_id}/dbs" "$(jq -n --arg name "${db_name}" '{name: $name}')" >/dev/null
}

create_mysql_user_if_missing() {
  local cluster_id="$1"
  local user_name="$2"

  if api GET "/databases/${cluster_id}/users" | jq -e --arg name "${user_name}" '.users[]? | select(.name == $name)' >/dev/null; then
    echo "MySQL user ${user_name} already exists."
    return 0
  fi

  echo "Creating MySQL user ${user_name}..."
  api POST "/databases/${cluster_id}/users" "$(jq -n --arg name "${user_name}" '{name: $name}')" >/dev/null
}

create_kafka_topic_if_missing() {
  local cluster_id="$1"
  local topic="$2"

  if api GET "/databases/${cluster_id}/topics" | jq -e --arg name "${topic}" '.topics[]? | select(.name == $name)' >/dev/null; then
    echo "Kafka topic ${topic} already exists."
    return 0
  fi

  echo "Creating Kafka topic ${topic} with ${KAFKA_TOPIC_PARTITIONS} partitions..."
  api POST "/databases/${cluster_id}/topics" "$(jq -n \
    --arg name "${topic}" \
    --argjson partitions "${KAFKA_TOPIC_PARTITIONS}" \
    --argjson replication "${KAFKA_TOPIC_REPLICATION_FACTOR}" \
    '{
      name: $name,
      partition_count: $partitions,
      replication_factor: $replication
    }')" >/dev/null
}

print_summary() {
  local mysql_id="$1"
  local kafka_id="$2"

  echo
  echo "Provisioning complete."
  echo "Region: ${SELECTED_REGION}"
  echo "MySQL cluster: ${MYSQL_NAME} (${mysql_id})"
  echo "MySQL database: ${MYSQL_DB_NAME}"
  echo "MySQL app user: ${MYSQL_APP_USER}"
  echo "Kafka cluster: ${KAFKA_NAME} (${kafka_id})"
  echo "Kafka partitions per topic: ${KAFKA_TOPIC_PARTITIONS}"
  echo "Kafka topics:"
  printf '  - %s\n' "${TOPICS[@]}"
}

require_command curl
require_command jq

echo "Checking for existing DigitalOcean clusters..."
EXISTING_MYSQL_ID="$(cluster_id_by_name "${MYSQL_NAME}")"
EXISTING_KAFKA_ID="$(cluster_id_by_name "${KAFKA_NAME}")"

if [[ -n "${EXISTING_MYSQL_ID}" && "${EXISTING_MYSQL_ID}" != "null" ]]; then
  echo "Found existing MySQL cluster ${MYSQL_NAME}: ${EXISTING_MYSQL_ID}"
else
  echo "No existing MySQL cluster named ${MYSQL_NAME} found."
fi

if [[ -n "${EXISTING_KAFKA_ID}" && "${EXISTING_KAFKA_ID}" != "null" ]]; then
  echo "Found existing Kafka cluster ${KAFKA_NAME}: ${EXISTING_KAFKA_ID}"
else
  echo "No existing Kafka cluster named ${KAFKA_NAME} found."
fi

SELECTED_REGION="$(select_shared_region)"
echo "Selected DigitalOcean region: ${SELECTED_REGION}"

validate_layout mysql "${MYSQL_NODES}" "${MYSQL_SIZE}"
validate_layout kafka "${KAFKA_NODES}" "${KAFKA_SIZE}"

MYSQL_ID="$(create_cluster_if_missing "${MYSQL_NAME}" mysql "${MYSQL_VERSION}" "${SELECTED_REGION}" "${MYSQL_SIZE}" "${MYSQL_NODES}" "${EXISTING_MYSQL_ID}")"
KAFKA_ID="$(create_cluster_if_missing "${KAFKA_NAME}" kafka "${KAFKA_VERSION}" "${SELECTED_REGION}" "${KAFKA_SIZE}" "${KAFKA_NODES}" "${EXISTING_KAFKA_ID}")"

wait_for_cluster "${MYSQL_ID}" "${MYSQL_NAME}"
wait_for_cluster "${KAFKA_ID}" "${KAFKA_NAME}"

create_mysql_database_if_missing "${MYSQL_ID}" "${MYSQL_DB_NAME}"
create_mysql_user_if_missing "${MYSQL_ID}" "${MYSQL_APP_USER}"

for topic in "${TOPICS[@]}"; do
  create_kafka_topic_if_missing "${KAFKA_ID}" "${topic}"
done

print_summary "${MYSQL_ID}" "${KAFKA_ID}"
