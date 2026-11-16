#!/usr/bin/env bash
# End-to-end probe: ubuntu:24.04 with systemd as PID 1, real sshd, real git origin, the real deploy.sh and
# deploy-forced.sh (only mvn, tomcat, pg_dump and the health URL are stubbed). Needs Docker with privileged
# containers and about 3 GB free in the container.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
name=l24-e2e-run
docker build -q -t l24-e2e "$here" >/dev/null
docker rm -f "$name" >/dev/null 2>&1 || true
trap 'docker rm -f "$name" >/dev/null 2>&1 || true' EXIT
docker run -d --name "$name" --privileged --cgroupns=host -v /sys/fs/cgroup:/sys/fs/cgroup:rw --tmpfs /run \
    -v "$here/../..":/work:ro l24-e2e >/dev/null
for _ in $(seq 1 60); do
    docker exec "$name" systemctl is-active --quiet ssh 2>/dev/null && break
    sleep 1
done
docker exec "$name" bash /work/test/e2e/setup.sh
docker exec "$name" bash /work/test/e2e/tests.sh
