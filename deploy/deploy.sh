#!/usr/bin/env bash
set -euo pipefail

APP_USER="${APP_USER:-shadowmode}"
APP_HOME="${APP_HOME:-/opt/shadowmode}"
APP_DIR="${APP_DIR:-${APP_HOME}/app}"
REPO_URL="${REPO_URL:-https://github.com/ankitkathait132-netizen/ShadowModelLlm.git}"
BRANCH="${BRANCH:-main}"

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Run this script as root on the droplet." >&2
  exit 1
fi

if [[ ! -f /etc/shadowmode.env ]]; then
  echo "Missing /etc/shadowmode.env. Create it before deploying." >&2
  exit 1
fi

if ! grep -Eq '^SPRING_PROFILES_ACTIVE=prod$' /etc/shadowmode.env; then
  echo "/etc/shadowmode.env must contain SPRING_PROFILES_ACTIVE=prod." >&2
  exit 1
fi

if [[ ! -d "${APP_DIR}/.git" ]]; then
  rm -rf "${APP_DIR}"
  sudo -u "${APP_USER}" git clone --branch "${BRANCH}" "${REPO_URL}" "${APP_DIR}"
else
  sudo -u "${APP_USER}" git -C "${APP_DIR}" fetch origin "${BRANCH}"
  sudo -u "${APP_USER}" git -C "${APP_DIR}" reset --hard "origin/${BRANCH}"
fi

chmod +x "${APP_DIR}/mvnw"
sudo -u "${APP_USER}" "${APP_DIR}/mvnw" -f "${APP_DIR}/pom.xml" -DskipTests package

install -m 0644 "${APP_DIR}/deploy/shadow-proxy-api.service" /etc/systemd/system/shadow-proxy-api.service
install -m 0644 "${APP_DIR}/deploy/shadow-worker.service" /etc/systemd/system/shadow-worker.service

systemctl daemon-reload
systemctl enable shadow-proxy-api shadow-worker
systemctl restart shadow-proxy-api shadow-worker

systemctl --no-pager --full status shadow-proxy-api
systemctl --no-pager --full status shadow-worker
