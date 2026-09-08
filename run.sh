#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

IMAGE="aldrop:latest"
CONTAINER="aldrop"
PORT=4000

if [ ! -f .env ]; then
    echo ".env not found in project root" >&2
    exit 1
fi

# the .env is copied into the image at build time, so a stale image would carry stale settings
podman build -t "$IMAGE" .

# replace any previous container, so this script can be re-run after a code change
if podman container exists "$CONTAINER"; then
    echo "Removing previous $CONTAINER container"
    podman rm -f "$CONTAINER" > /dev/null
fi

mkdir -p logs
TIMESTAMP="$(date +%Y-%m-%d_%H-%M-%S)"
LOG_FILE="logs/${CONTAINER}-${TIMESTAMP}.log"

podman run -d --name "$CONTAINER" -p "$PORT:4000" "$IMAGE" > /dev/null

# `podman logs -f` runs on the host (not inside the podman machine VM), so redirecting it here
# writes straight to the host disk regardless of the VM's mount configuration. It exits on its own
# once the container's log stream closes (stop.sh removes the container).
podman logs -f "$CONTAINER" > "$LOG_FILE" 2>&1 &
disown

# wait for the app rather than reporting success the moment podman returns
for _ in $(seq 1 60); do
    if curl -s -o /dev/null --max-time 2 "http://localhost:$PORT/monitor"; then
        echo
        echo "Aldrop is up on http://localhost:$PORT"
        echo "  docs     http://localhost:$PORT/swagger-ui/index.html   (ENV=QA only, platform admin credentials)"
        echo "  logs     tail -f $LOG_FILE"
        echo "  stop     podman stop $CONTAINER"
        exit 0
    fi
    sleep 1
done

echo "Container did not come up within 60s, last logs:" >&2
podman logs --tail 30 "$CONTAINER" >&2
exit 1
