#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

REPOSITORY="localhost/aldrop"

# Scoped by image rather than by container name: anything running a localhost/aldrop image is an
# aldrop container, including one left behind under a different name. Other podman workloads on this
# host never match, and the shared ibm-semeru-runtimes base images that other projects build on are
# left in place.

# containers must go first: podman refuses to remove an image that any container still references,
# even a stopped one
mapfile -t CONTAINERS < <(
    podman ps -a --format '{{.Names}} {{.Image}}' \
        | awk -v repo="^${REPOSITORY}:" '$2 ~ repo { print $1 }'
)

if [ ${#CONTAINERS[@]} -eq 0 ]; then
    echo "No $REPOSITORY containers found"
else
    for container in "${CONTAINERS[@]}"; do
        echo "Stopping and removing container $container"
        podman rm -f "$container" > /dev/null
    done
fi

mapfile -t IMAGE_TAGS < <(podman images --format '{{.Repository}}:{{.Tag}}' | grep -E "^${REPOSITORY}:" || true)

if [ ${#IMAGE_TAGS[@]} -eq 0 ]; then
    echo "No $REPOSITORY images found"
    exit 0
fi

for tag in "${IMAGE_TAGS[@]}"; do
    echo "Removing image $tag"
    podman rmi "$tag" > /dev/null
done

echo "Done. Untagged layers left in place; run.sh reuses them to rebuild quickly."
