#!/usr/bin/env bash
# Enrol a user in TOTP end to end: login, start enrolment, compute the current
# code from the returned secret, confirm, and print the secret and backup codes.
# Needs curl and python3. Env: BASE_URL, PLATFORM_API_KEY, TOTP_USERNAME, TOTP_PASSWORD.
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:4000}"
IP_ADDRESS="${IP_ADDRESS:-127.0.0.1}"
USER_AGENT="${USER_AGENT:-enroll-totp.sh}"

prompt() {
    local label="$1" default="${2:-}" value
    read -r -p "$label${default:+ [$default]}: " value
    echo "${value:-$default}"
}

prompt_secret() {
    local label="$1" value
    read -r -s -p "$label: " value
    echo >&2
    echo "$value"
}

API_KEY="${PLATFORM_API_KEY:-$(prompt_secret "Platform API key")}"
USERNAME="${TOTP_USERNAME:-$(prompt "Username")}"
PASSWORD="${TOTP_PASSWORD:-$(prompt_secret "Password")}"

if [ -z "$API_KEY" ] || [ -z "$USERNAME" ] || [ -z "$PASSWORD" ]; then
    echo "API key, username and password are all required" >&2
    exit 1
fi

# Build JSON from key=value args so special characters in values are escaped.
json() {
    python3 -c 'import json,sys; print(json.dumps(dict(a.split("=",1) for a in sys.argv[1:])))' "$@"
}

# POST a JSON body from stdin; print the body, fail on a non-2xx status.
post() {
    local path="$1" out status
    out="$(curl -sS -m 15 -w '\n%{http_code}' -X POST "${BASE_URL}${path}" \
        -H "Authorization: Bearer ${API_KEY}" \
        -H 'Content-Type: application/json' -d @-)"
    status="${out##*$'\n'}"
    if [[ "$status" != 2* ]]; then
        echo "POST ${path} failed: HTTP ${status}: ${out%$'\n'*}" >&2
        exit 1
    fi
    echo "${out%$'\n'*}"
}

field() { python3 -c 'import json,sys; v=json.load(sys.stdin)[sys.argv[1]]; print(json.dumps(v) if isinstance(v,(list,dict)) else v)' "$1"; }

totp_code() {
    python3 - "$1" <<'EOF'
import base64, hashlib, hmac, struct, sys, time
key = base64.b32decode(sys.argv[1])
h = hmac.new(key, struct.pack(">Q", int(time.time()) // 30), hashlib.sha1).digest()
o = h[-1] & 15
print("%06d" % ((struct.unpack(">I", h[o:o + 4])[0] & 0x7fffffff) % 10**6))
EOF
}

login="$(json username="$USERNAME" password="$PASSWORD" ipAddress="$IP_ADDRESS" userAgent="$USER_AGENT" | post /auth/login)"
token="$(field token <<<"$login")"
if [ "$token" = "None" ]; then
    echo "User already has two-factor enabled (login returned a challenge)" >&2
    exit 1
fi

enable="$(json token="$token" | post /auth/totp/enable)"
secret="$(field secret <<<"$enable")"
uri="$(field otpAuthUri <<<"$enable")"

confirm="$(json token="$token" code="$(totp_code "$secret")" | post /auth/totp/confirm)"
backup="$(field backupCodes <<<"$confirm")"

echo
echo "TOTP enabled for ${USERNAME}"
echo "Secret:       ${secret}"
echo "URI:          ${uri}"
echo "Backup codes: ${backup}"
echo "Save the backup codes now; they cannot be regenerated."
