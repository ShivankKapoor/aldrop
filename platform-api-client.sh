#!/usr/bin/env bash
# Talk to the /auth/** endpoints as a platform, using a pasted platform API key.
set -euo pipefail
cd "$(dirname "$0")"

BASE_URL="${BASE_URL:-http://localhost:4000}"

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

prompt_secret() {
    local label="$1" value
    read -r -s -p "$label: " value
    echo >&2
    echo "$value"
}

if [ -n "${PLATFORM_API_KEY:-}" ]; then
    API_KEY="$PLATFORM_API_KEY"
else
    API_KEY="$(prompt_secret "Platform API key")"
fi

if [ -z "$API_KEY" ]; then
    echo "No API key provided" >&2
    exit 1
fi

curl_auth() {
    curl -sS -H "Authorization: Bearer ${API_KEY}" \
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

post_json() {
    local path="$1" payload="$2"
    curl_auth -X POST "${BASE_URL}${path}" \
        -H 'Content-Type: application/json' \
        -d "$payload"
}

do_register() {
    username="$(prompt "Username")"
    password="$(prompt_secret "Password")"
    response="$(post_json "/auth/register" "{\"username\": \"${username}\", \"password\": \"${password}\"}")"
    print_response "$response"
}

do_login() {
    username="$(prompt "Username")"
    password="$(prompt_secret "Password")"
    ip_address="$(prompt "IP address (only needed if platform requires device binding)")"
    user_agent="$(prompt "User agent (only needed if platform requires device binding)")"
    response="$(post_json "/auth/login" "{\"username\": \"${username}\", \"password\": \"${password}\", \"ipAddress\": \"${ip_address}\", \"userAgent\": \"${user_agent}\"}")"
    print_response "$response"
}

do_verify_totp() {
    totp_token="$(prompt "TOTP challenge token")"
    code="$(prompt "Code (authenticator or backup code)")"
    ip_address="$(prompt "IP address (only needed if platform requires device binding)")"
    user_agent="$(prompt "User agent (only needed if platform requires device binding)")"
    response="$(post_json "/auth/login/verify-totp" "{\"totpToken\": \"${totp_token}\", \"code\": \"${code}\", \"ipAddress\": \"${ip_address}\", \"userAgent\": \"${user_agent}\"}")"
    print_response "$response"
}

do_validate() {
    token="$(prompt "Session token")"
    ip_address="$(prompt "IP address (only needed if platform requires device binding)")"
    user_agent="$(prompt "User agent (only needed if platform requires device binding)")"
    response="$(post_json "/auth/validate" "{\"token\": \"${token}\", \"ipAddress\": \"${ip_address}\", \"userAgent\": \"${user_agent}\"}")"
    print_response "$response"
}

do_logout() {
    token="$(prompt "Session token")"
    ip_address="$(prompt "IP address")"
    response="$(post_json "/auth/logout" "{\"token\": \"${token}\", \"ipAddress\": \"${ip_address}\"}")"
    print_response "$response"
}

do_logout_all() {
    token="$(prompt "Session token")"
    ip_address="$(prompt "IP address")"
    response="$(post_json "/auth/logout-all" "{\"token\": \"${token}\", \"ipAddress\": \"${ip_address}\"}")"
    print_response "$response"
}

do_totp_enable() {
    token="$(prompt "Session token")"
    response="$(post_json "/auth/totp/enable" "{\"token\": \"${token}\"}")"
    print_response "$response"
}

do_totp_confirm() {
    token="$(prompt "Session token")"
    code="$(prompt "Authenticator code")"
    response="$(post_json "/auth/totp/confirm" "{\"token\": \"${token}\", \"code\": \"${code}\"}")"
    print_response "$response"
}

while true; do
    echo
    echo "Aldrop platform API client (${BASE_URL})"
    echo "  1) Register user"
    echo "  2) Login"
    echo "  3) Complete two-factor login (verify TOTP)"
    echo "  4) Validate session token"
    echo "  5) Logout (one session)"
    echo "  6) Logout all sessions"
    echo "  7) Start two-factor enrolment"
    echo "  8) Confirm two-factor enrolment"
    echo "  9) Exit"
    choice="$(prompt "Select an option")"

    case "$choice" in
        1) do_register ;;
        2) do_login ;;
        3) do_verify_totp ;;
        4) do_validate ;;
        5) do_logout ;;
        6) do_logout_all ;;
        7) do_totp_enable ;;
        8) do_totp_confirm ;;
        9) exit 0 ;;
        *) echo "Invalid option: $choice" ;;
    esac
done
