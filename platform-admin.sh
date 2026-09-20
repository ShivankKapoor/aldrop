#!/usr/bin/env bash
# Talk to the /platform/** admin endpoints using the operator Basic Auth creds from .env.
set -euo pipefail
cd "$(dirname "$0")"

BASE_URL="${BASE_URL:-http://localhost:4000}"

if [ ! -f .env ]; then
    echo ".env not found in project root" >&2
    exit 1
fi

# shellcheck disable=SC1091
set -a
source .env
set +a

if [ -z "${PLATFORM_ADMIN_USERNAME:-}" ] || [ -z "${PLATFORM_ADMIN_PASSWORD:-}" ]; then
    echo "PLATFORM_ADMIN_USERNAME / PLATFORM_ADMIN_PASSWORD not set in .env" >&2
    exit 1
fi

curl_admin() {
    curl -sS -u "${PLATFORM_ADMIN_USERNAME}:${PLATFORM_ADMIN_PASSWORD}" \
        -w '\n%{http_code}\n' "$@"
}

print_response() {
    local output="$1"
    local body status
    body="$(printf '%s' "$output" | sed '$d')"
    status="$(printf '%s' "$output" | tail -n1)"
    echo
    echo "$body"
    echo "HTTP $status"
}

prompt() {
    local label="$1" default="${2:-}" value
    if [ -n "$default" ]; then
        read -r -p "$label [$default]: " value
        echo "${value:-$default}"
    else
        read -r -p "$label: " value
        echo "$value"
    fi
}

do_create() {
    name="$(prompt "Platform name")"
    session_ttl="$(prompt "Session TTL (ISO-8601 duration)" "PT1H")"
    max_sessions="$(prompt "Max sessions per user" "5")"
    totp_available="$(prompt "TOTP available (true/false)" "false")"
    require_device_binding="$(prompt "Require device binding (true/false)" "false")"

    payload="$(cat <<EOF
{
  "name": "${name}",
  "sessionTtl": "${session_ttl}",
  "maxSessionsPerUser": ${max_sessions},
  "totpAvailable": ${totp_available},
  "requireDeviceBinding": ${require_device_binding}
}
EOF
)"
    response="$(curl_admin -X POST "${BASE_URL}/platform/create" \
        -H 'Content-Type: application/json' \
        -d "$payload")"
    print_response "$response"
}

do_status() {
    id="$(prompt "Platform id")"
    is_active="$(prompt "Set active (true/false)")"
    response="$(curl_admin -X PATCH "${BASE_URL}/platform/${id}/status" \
        -H 'Content-Type: application/json' \
        -d "{\"isActive\": ${is_active}}")"
    print_response "$response"
}

do_rotate_key() {
    id="$(prompt "Platform id")"
    response="$(curl_admin -X PATCH "${BASE_URL}/platform/${id}/rotate-key")"
    print_response "$response"
}

do_delete() {
    id="$(prompt "Platform id")"
    confirm="$(prompt "This permanently deletes the platform and all its data. Type 'yes' to confirm")"
    if [ "$confirm" != "yes" ]; then
        echo "Cancelled."
        return
    fi
    response="$(curl_admin -X DELETE "${BASE_URL}/platform/${id}")"
    print_response "$response"
}

while true; do
    echo
    echo "Aldrop platform admin (${BASE_URL})"
    echo "  1) Create platform"
    echo "  2) Update platform status (activate/deactivate)"
    echo "  3) Rotate platform API key"
    echo "  4) Delete platform"
    echo "  5) Exit"
    choice="$(prompt "Select an option")"

    case "$choice" in
        1) do_create ;;
        2) do_status ;;
        3) do_rotate_key ;;
        4) do_delete ;;
        5) exit 0 ;;
        *) echo "Invalid option: $choice" ;;
    esac
done
