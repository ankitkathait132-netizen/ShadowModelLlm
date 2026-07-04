#!/bin/sh
# Starts proxy-api and shadow-worker together from the reactor root.
#
# Maven's spring-boot:run goal blocks for a single application, so there's no
# native "run all modules" reactor goal for long-lived apps. This script is the
# practical stand-in: it launches both modules (via run.sh, using the bundled
# JDK + Maven wrapper), streams each to its own log file, and stops both
# together on Ctrl+C.
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

PROFILE="${1:-local}"
mkdir -p logs

echo "Starting proxy-api and shadow-worker with profile '$PROFILE'..."

./run.sh -pl proxy-api spring-boot:run -Dspring-boot.run.profiles="$PROFILE" > logs/proxy-api.log 2>&1 &
PROXY_PID=$!

./run.sh -pl shadow-worker spring-boot:run -Dspring-boot.run.profiles="$PROFILE" > logs/shadow-worker.log 2>&1 &
WORKER_PID=$!

cleanup() {
  echo ""
  echo "Stopping proxy-api (pid $PROXY_PID) and shadow-worker (pid $WORKER_PID)..."
  kill "$PROXY_PID" "$WORKER_PID" 2>/dev/null || true
  wait "$PROXY_PID" "$WORKER_PID" 2>/dev/null || true
}
trap cleanup INT TERM EXIT

echo "proxy-api:     http://localhost:8080  (pid $PROXY_PID, log: logs/proxy-api.log)"
echo "shadow-worker: http://localhost:8081  (pid $WORKER_PID, log: logs/shadow-worker.log)"
echo "Tail logs with: tail -f logs/proxy-api.log logs/shadow-worker.log"
echo "Press Ctrl+C to stop both."

wait
