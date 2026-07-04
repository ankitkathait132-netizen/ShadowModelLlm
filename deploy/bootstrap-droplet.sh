#!/usr/bin/env bash
set -euo pipefail

APP_USER="${APP_USER:-shadowmode}"
APP_HOME="${APP_HOME:-/opt/shadowmode}"

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Run this script as root on the droplet." >&2
  exit 1
fi

apt-get update
apt-get install -y git openjdk-17-jdk

if ! id "${APP_USER}" >/dev/null 2>&1; then
  useradd -r -m -d "${APP_HOME}" "${APP_USER}"
fi

mkdir -p "${APP_HOME}" "${APP_HOME}/logs"
chown -R "${APP_USER}:${APP_USER}" "${APP_HOME}"

install -m 0644 deploy/shadow-proxy-api.service /etc/systemd/system/shadow-proxy-api.service
install -m 0644 deploy/shadow-worker.service /etc/systemd/system/shadow-worker.service

systemctl daemon-reload

echo "Bootstrap complete."
echo "Next: create /etc/shadowmode.env, then run deploy/deploy.sh from the repo root."
