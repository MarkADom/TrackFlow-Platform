#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

ORDER_API="http://localhost:8081/api/v1/orders"
TRACKING_API="http://localhost:8082/api/v1/tracking"
NOTIFICATION_API="http://localhost:8083/api/v1/notifications"

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "ERROR: required command '$1' not found"
    exit 1
  }
}

wait_for_health() {
  local url="$1"
  local name="$2"
  local retries="${3:-45}"

  for ((i=1; i<=retries; i++)); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done

  echo "ERROR: ${name} health endpoint is not reachable: ${url}"
  exit 1
}

require_cmd curl
require_cmd python3

wait_for_health "http://localhost:8081/actuator/health" "order-service"
wait_for_health "http://localhost:8082/actuator/health" "tracking-service"
wait_for_health "http://localhost:8083/actuator/health" "notification-service"

echo "[1/4] Creating order..."
response_body_file="$(mktemp)"
http_code="$(curl -sS -o "$response_body_file" -w '%{http_code}' -X POST "$ORDER_API" \
  -H 'Content-Type: application/json' \
  -d '{
    "origin": "Berlin",
    "destination": "Munich",
    "recipientName": "Alice Example",
    "recipientEmail": "alice@example.com"
  }')"
create_response="$(cat "$response_body_file")"
rm -f "$response_body_file"

if [[ "$http_code" != "201" ]]; then
  echo "ERROR: order creation failed (HTTP ${http_code})"
  echo "$create_response"
  exit 1
fi

eval "$(printf '%s' "$create_response" | python3 -c 'import json,sys,shlex; p=json.load(sys.stdin); print("ORDER_ID=" + shlex.quote(p["id"])); print("TRACKING_CODE=" + shlex.quote(p["trackingCode"]))')"

echo "Created order: ${ORDER_ID} (tracking: ${TRACKING_CODE})"

echo "[2/4] Verifying event propagation to tracking + notification services..."
for i in {1..45}; do
  tracking_status="$(curl -s "$TRACKING_API/$ORDER_ID/latest" | python3 -c 'import json,sys; data=json.load(sys.stdin); print(data.get("status",""))' 2>/dev/null || true)"
  notif_count="$(curl -s "$NOTIFICATION_API/$ORDER_ID" | python3 -c 'import json,sys; data=json.load(sys.stdin); print(len(data) if isinstance(data,list) else 0)' 2>/dev/null || echo 0)"

  if [[ "$tracking_status" == "CREATED" && "$notif_count" -ge 1 ]]; then
    echo "Event propagation verified"
    break
  fi

  if [[ "$i" -eq 45 ]]; then
    echo "ERROR: event propagation timeout"
    echo "tracking_status=$tracking_status, notification_logs=$notif_count"
    exit 1
  fi

  sleep 2
done

echo "[3/4] Verifying tracking data exists..."
tracking_history="$(curl -fsS "$TRACKING_API/$ORDER_ID/history")"
tracking_len="$(printf '%s' "$tracking_history" | python3 -c 'import json,sys; print(len(json.load(sys.stdin)))')"
if [[ "$tracking_len" -lt 1 ]]; then
  echo "ERROR: tracking history is empty"
  exit 1
fi

echo "Tracking entries found: $tracking_len"

echo "[4/4] Verifying notification log exists..."
notification_history="$(curl -fsS "$NOTIFICATION_API/$ORDER_ID")"
notification_len="$(printf '%s' "$notification_history" | python3 -c 'import json,sys; print(len(json.load(sys.stdin)))')"
if [[ "$notification_len" -lt 1 ]]; then
  echo "ERROR: notification log is empty"
  exit 1
fi

echo "Notification logs found: $notification_len"
echo "Smoke test passed ✅"
