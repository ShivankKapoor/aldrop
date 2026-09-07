#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f .env ]; then
    echo ".env not found in project root" >&2
    exit 1
fi

set -a
source .env
set +a

RUNNER="${1:-*KarateRunner}"
./mvnw test -Dtest="$RUNNER" -DfailIfNoTests=true
