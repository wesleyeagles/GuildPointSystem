#!/usr/bin/env bash
# Usage: ./deploy/validate-prod.sh api.seudominio.com app.seudominio.com
set -euo pipefail

API_HOST="${1:?api host required}"
APP_HOST="${2:?app host required}"

API_BASE="https://${API_HOST}"
APP_BASE="https://${APP_HOST}"

echo "==> Health: ${API_BASE}/actuator/health"
HEALTH=$(curl -fsS "${API_BASE}/actuator/health")
echo "$HEALTH"
echo "$HEALTH" | grep -q '"status":"UP"' || { echo "FAIL: health not UP"; exit 1; }

echo "==> SockJS info: ${API_BASE}/ws/info"
curl -fsS "${API_BASE}/ws/info" | head -c 200
echo ""

echo "==> Frontend: ${APP_BASE}"
STATUS=$(curl -fsS -o /dev/null -w '%{http_code}' "${APP_BASE}")
echo "HTTP ${STATUS}"
[[ "$STATUS" == "200" ]] || { echo "FAIL: app not 200"; exit 1; }

echo "==> CORS header check (Origin: ${APP_BASE})"
CORS=$(curl -fsS -o /dev/null -D - -H "Origin: ${APP_BASE}" "${API_BASE}/api/seeds/races" | grep -i access-control-allow-origin || true)
echo "${CORS:-WARN: no Access-Control-Allow-Origin (check CORS_ORIGINS)}"

echo "OK — basic production checks passed."
