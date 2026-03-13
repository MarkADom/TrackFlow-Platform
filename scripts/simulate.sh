#!/usr/bin/env bash
set -euo pipefail

# ── Config ────────────────────────────────────────────────────────────────────
N="${1:-50}"
ORDER_SERVICE="http://localhost:8081"
DESTINATIONS=("Lisboa" "Porto" "Braga" "Coimbra" "Faro" "Aveiro" "Setúbal" "Évora")

# ── Preflight checks ──────────────────────────────────────────────────────────
for cmd in curl jq; do
  if ! command -v "$cmd" &>/dev/null; then
    echo "ERROR: '$cmd' is required but not installed." >&2
    exit 1
  fi
done

if ! curl -sf --max-time 3 "${ORDER_SERVICE}/actuator/health" &>/dev/null \
   && ! curl -sf --max-time 3 "${ORDER_SERVICE}/api/v1/orders/00000000-0000-0000-0000-000000000000" &>/dev/null; then
  if ! curl -o /dev/null -sf --max-time 3 "${ORDER_SERVICE}/api/v1/orders" &>/dev/null; then
    echo "ERROR: order-service is not reachable at ${ORDER_SERVICE}" >&2
    exit 1
  fi
fi

# ── Helpers ───────────────────────────────────────────────────────────────────
pick_destination() {
  local origin="$1"
  local candidates=()
  for d in "${DESTINATIONS[@]}"; do
    [[ "$d" != "$origin" ]] && candidates+=("$d")
  done
  echo "${candidates[RANDOM % ${#candidates[@]}]}"
}

update_status() {
  local order_id="$1"
  local status="$2"
  local http_code
  http_code=$(curl -s -o /dev/null -w "%{http_code}" -X PUT \
    "${ORDER_SERVICE}/api/v1/orders/${order_id}/status" \
    -H "Content-Type: application/json" \
    -d "{\"status\":\"${status}\",\"notes\":\"Simulated event\"}")
  echo "$http_code"
}

# ── Run one full order lifecycle (called in background per order) ──────────────
run_order() {
  local first="$1"
  local last="$2"
  local email="$3"
  local city="$4"
  local idx="$5"

  local destination
  destination=$(pick_destination "$city")

  local payload
  payload=$(jq -n \
    --arg rn "${first} ${last}" \
    --arg re "$email" \
    --arg or "$city" \
    --arg ds "$destination" \
    '{recipientName:$rn, recipientEmail:$re, origin:$or, destination:$ds}')

  local response http_code
  response=$(curl -s -o /tmp/tf_order_${idx}.json -w "%{http_code}" -X POST \
    "${ORDER_SERVICE}/api/v1/orders" \
    -H "Content-Type: application/json" \
    -d "$payload")
  http_code="$response"

  if [[ "$http_code" != "201" ]]; then
    echo "[${idx}] ERROR creating order for ${first} ${last}: HTTP ${http_code}" >&2
    echo "FAIL" > /tmp/tf_result_${idx}.txt
    return
  fi

  local order_id
  order_id=$(jq -r '.id' /tmp/tf_order_${idx}.json)

  echo "[${idx}/${N}] Created ${order_id} | ${first} ${last} | ${city} → ${destination}"

  local statuses=("PICKED_UP" "IN_TRANSIT" "OUT_FOR_DELIVERY" "DELIVERED")
  local events=1  # CREATED

  for status in "${statuses[@]}"; do
    sleep 0.1
    local sc
    sc=$(update_status "$order_id" "$status")
    if [[ "$sc" == "200" ]]; then
      events=$((events + 1))
      echo "    [${idx}] -> ${status}"
    else
      echo "    [${idx}] ERROR ${status}: HTTP ${sc}" >&2
    fi
  done

  echo "OK:${events}" > /tmp/tf_result_${idx}.txt
  rm -f /tmp/tf_order_${idx}.json
}

# ── Main ──────────────────────────────────────────────────────────────────────
echo ""
echo "Fetching ${N} Portuguese users from randomuser.me..."

users_json=$(curl -sf --max-time 15 \
  "https://randomuser.me/api/?nat=pt&results=${N}")

user_count=$(echo "$users_json" | jq '.results | length')
echo "Got ${user_count} users."
echo ""

start_time=$(date +%s%3N)

# ── Launch all orders in parallel ─────────────────────────────────────────────
pids=()
for i in $(seq 0 $((user_count - 1))); do
  first=$(echo "$users_json" | jq -r ".results[${i}].name.first")
  last=$(echo "$users_json"  | jq -r ".results[${i}].name.last")
  email=$(echo "$users_json" | jq -r ".results[${i}].email")
  city=$(echo "$users_json"  | jq -r ".results[${i}].location.city")

  run_order "$first" "$last" "$email" "$city" "$((i + 1))" &
  pids+=($!)
done

# Wait for all background jobs
for pid in "${pids[@]}"; do
  wait "$pid" || true
done

# ── Collect results ───────────────────────────────────────────────────────────
orders_created=0
total_events=0

for i in $(seq 1 "$user_count"); do
  result_file="/tmp/tf_result_${i}.txt"
  if [[ -f "$result_file" ]]; then
    result=$(cat "$result_file")
    if [[ "$result" == OK:* ]]; then
      orders_created=$((orders_created + 1))
      events="${result#OK:}"
      total_events=$((total_events + events))
    fi
    rm -f "$result_file"
  fi
done

end_time=$(date +%s%3N)
elapsed=$((end_time - start_time))
elapsed_s=$(awk "BEGIN {printf \"%.1f\", ${elapsed}/1000}")

echo ""
echo "========== SIMULATION COMPLETE =========="
echo "Orders created:         ${orders_created}"
echo "Kafka events published: ${total_events}"
echo "Time elapsed:           ${elapsed}ms (${elapsed_s}s)"
echo "========================================="
